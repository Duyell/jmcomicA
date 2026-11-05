package com.jmcomic.pdfapp.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.jmcomic.pdfapp.MainActivity
import com.jmcomic.pdfapp.model.DownloadStatus
import com.jmcomic.pdfapp.model.DownloadUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch

/**
 * 下载前台服务：通知栏常驻显示进度，保证切后台/锁屏时进程不被杀。
 *
 * START_STICKY：进程被系统杀死后由系统重启本服务（intent 为 null），
 * 此时自动调用 [DownloadManager.resumePending] 断点续传。
 */
class DownloadService : Service() {

    companion object {
        private const val CHANNEL_ID = "download_progress"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_START = "com.jmcomic.pdfapp.action.START_DOWNLOAD"

        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var observing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        DownloadManager.init(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildProgressNotification("准备下载...", null))

        if (!observing) {
            observing = true
            scope.launch {
                DownloadManager.state
                    .distinctUntilChangedBy { st ->
                        "${st.status} | ${st.progressMessage} | " +
                            (st.progressFraction?.let { (it * 100).toInt() } ?: "?")
                    }
                    .collect { st -> onStateChanged(st) }
            }
        }

        // 进程被杀后由系统重启（intent == null）→ 自动续传
        if (intent == null) {
            DownloadManager.resumePending(startService = false)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun onStateChanged(st: DownloadUiState) {
        val nm = getSystemService(NotificationManager::class.java)
        when (val status = st.status) {
            is DownloadStatus.Downloading ->
                nm.notify(
                    NOTIFICATION_ID,
                    buildProgressNotification(st.progressMessage, st.progressFraction)
                )

            is DownloadStatus.Success -> {
                nm.notify(
                    NOTIFICATION_ID,
                    buildResultNotification("下载完成", st.errorMessage ?: "全部章节已保存")
                )
                finishForeground()
            }

            is DownloadStatus.Error -> {
                nm.notify(
                    NOTIFICATION_ID,
                    buildResultNotification("下载失败", status.message)
                )
                finishForeground()
            }

            else -> {}
        }
    }

    /** 停止前台身份（通知保留供用户查看）并停止服务。 */
    private fun finishForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(false)
        }
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "下载进度",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "漫画下载进度通知"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun openAppIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildProgressNotification(
        text: String,
        fraction: Float?,
    ) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle("正在下载")
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(openAppIntent())
        .apply {
            if (fraction != null) {
                setProgress(100, (fraction * 100).toInt().coerceIn(0, 100), false)
            } else {
                setProgress(100, 0, true)
            }
        }
        .build()

    private fun buildResultNotification(
        title: String,
        text: String,
    ) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download_done)
        .setContentTitle(title)
        .setContentText(text)
        .setAutoCancel(true)
        .setContentIntent(openAppIntent())
        .build()
}
