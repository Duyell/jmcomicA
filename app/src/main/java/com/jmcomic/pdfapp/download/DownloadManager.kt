package com.jmcomic.pdfapp.download

import android.content.Context
import android.util.Log
import com.chaquo.python.Python
import com.jmcomic.pdfapp.data.DownloadHistoryManager
import com.jmcomic.pdfapp.model.ChapterDownloadResult
import com.jmcomic.pdfapp.model.ChapterInfo
import com.jmcomic.pdfapp.model.DownloadRecord
import com.jmcomic.pdfapp.model.DownloadStatus
import com.jmcomic.pdfapp.model.DownloadTask
import com.jmcomic.pdfapp.model.DownloadUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 全局下载协调器（单例）。
 *
 * 设计目标（v1.4.1）：
 *  1. 下载执行与界面解耦——UI（ViewModel）只订阅状态，即使界面重建、
 *     进程重启，下载也由前台服务 + 本单例继续推进。
 *  2. 任务落盘（download_task.json）——进程被杀后，服务自动续传或
 *     UI 提示"继续下载"，配合 Python 侧断点续传避免从 0 重下。
 */
object DownloadManager {

    private const val TAG = "JMComicPDF"
    private const val TASK_FILE = "download_task.json"

    private lateinit var appContext: Context
    private var initialized = false

    private val historyManager by lazy { DownloadHistoryManager(appContext.filesDir) }

    /** 独立于 UI 的执行域：进程存活期间持续存在。 */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    private val _state = MutableStateFlow(DownloadUiState())
    val state: StateFlow<DownloadUiState> = _state.asStateFlow()

    private val _pendingTask = MutableStateFlow<DownloadTask?>(null)
    val pendingTask: StateFlow<DownloadTask?> = _pendingTask.asStateFlow()

    val outputDir: String
        get() = File(appContext.filesDir, "pdf_output").also {
            if (!it.exists()) it.mkdirs()
        }.absolutePath

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext
        // 恢复上次未完成的任务（若有）
        _pendingTask.value = loadTaskFromDisk()
    }

    fun isDownloading(): Boolean = job?.isActive == true

    /** 开始一个新下载任务（前台服务保活 + 任务落盘）。 */
    fun start(context: Context, task: DownloadTask) {
        init(context)
        if (job?.isActive == true) return
        persistTask(task)
        _pendingTask.value = task
        DownloadService.start(appContext)
        launchExecute(task)
    }

    /**
     * 继续上次未完成的任务。进程被杀后由系统重启的服务（null intent）或
     * UI 的"继续下载"按钮触发。
     *
     * @param startService 是否补启动服务；服务自身的 onStartCommand 里
     *                     调用时传 false，避免自我重启。
     */
    fun resumePending(startService: Boolean = true): Boolean {
        val task = _pendingTask.value ?: return false
        if (job?.isActive == true) return true
        if (startService) DownloadService.start(appContext)
        launchExecute(task)
        return true
    }

    /** 放弃上次未完成的任务（删除落盘快照）。 */
    fun discardPending() {
        _pendingTask.value = null
        clearTaskFile()
    }

    /** 结果已由 UI 确认（成功/失败卡片关闭），重置状态。 */
    fun acknowledge() {
        if (job?.isActive == true) return
        _state.value = DownloadUiState()
    }

    private fun launchExecute(task: DownloadTask) {
        job = scope.launch { execute(task) }
    }

    private suspend fun execute(task: DownloadTask) {
        _state.value = _state.value.copy(
            activeTask = task,
            status = DownloadStatus.Downloading,
            progressMessage = "准备下载 ${task.selected.size} 章...",
            progressFraction = null,
            errorMessage = null,
            chapterResults = emptyList(),
            pdfPath = null,
        )

        val pollJob = scope.launch(Dispatchers.IO) { pollProgress() }

        try {
            // 清掉上次运行残留的进度文件，避免恢复时展示旧进度
            try { File(outputDir, "progress.json").delete() } catch (_: Exception) {}

            val indicesJson = JSONArray(task.selected).toString()
            val jsonStr = callPython(
                "download_selected_chapters",
                task.albumId,
                indicesJson,
                outputDir,
                ""
            )
            Log.d(TAG, "download_selected result: " + jsonStr.take(500))

            val json = JSONObject(jsonStr)
            if (json.optBoolean("success", false)) {
                val pdfsArr = json.optJSONArray("pdfs") ?: JSONArray()
                val results = (0 until pdfsArr.length()).map { i ->
                    val obj = pdfsArr.getJSONObject(i)
                    ChapterDownloadResult(
                        chapterIndex = obj.getInt("chapter_index"),
                        chapterTitle = obj.optString("chapter_title", ""),
                        pdfPath = obj.optString("pdf_path", "").ifBlank { null },
                        error = obj.optString("error", "").ifBlank { null },
                    )
                }

                // Save records for successful downloads
                val now = System.currentTimeMillis()
                for (r in results) {
                    if (r.pdfPath != null) {
                        historyManager.add(DownloadRecord(
                            albumId = task.albumId,
                            albumTitle = task.title,
                            chapterIndex = r.chapterIndex,
                            chapterTitle = r.chapterTitle,
                            pdfPath = r.pdfPath,
                            downloadTime = now,
                            fileSize = File(r.pdfPath).length(),
                        ))
                    }
                }

                val allSuccess = results.all { it.pdfPath != null }
                val firstPdf = results.firstOrNull()?.pdfPath
                // 部分失败也置 Success：MultiChapterSuccessSection 会逐章展示失败明细
                _state.value = _state.value.copy(
                    status = DownloadStatus.Success,
                    pdfPath = firstPdf,
                    chapterResults = results,
                    progressFraction = null,
                    progressMessage = "",
                    errorMessage = if (allSuccess) null
                        else "${results.count { it.error != null }} 章失败",
                )
                // 任务完成：清除落盘快照
                clearTaskFile()
                _pendingTask.value = null
            } else {
                val msg = json.optString("user_message", "下载失败")
                _state.value = _state.value.copy(
                    status = DownloadStatus.Error(msg),
                    errorMessage = msg,
                )
                // 失败保留任务快照，供"继续下载"断点续传
            }
        } catch (e: Exception) {
            Log.e(TAG, "download failed", e)
            _state.value = _state.value.copy(
                status = DownloadStatus.Error(translateError(e)),
                errorMessage = translateError(e),
            )
            // 异常同样保留任务快照
        } finally {
            pollJob.cancel()
            try { File(outputDir, "progress.json").delete() } catch (_: Exception) {}
        }
    }

    private suspend fun pollProgress() {
        while (true) {
            delay(400)
            try {
                val file = File(outputDir, "progress.json")
                if (!file.exists()) continue
                val json = JSONObject(file.readText())
                val message = json.optString("message", "")
                val current = json.optInt("current", 0)
                val total = json.optInt("total", 0)
                val fraction = if (total > 0) current.toFloat() / total else null
                _state.value = _state.value.copy(
                    progressMessage = message,
                    progressFraction = fraction,
                )
            } catch (_: Exception) {}
        }
    }

    private suspend fun callPython(funcName: String, vararg args: String): String {
        return withContext(Dispatchers.IO) {
            val py = Python.getInstance()
            val module = py.getModule("jm_bridge")
            val callArgs = arrayOf<Any>(*args)
            when (funcName) {
                "download_selected_chapters" ->
                    module.callAttr("download_selected_chapters", *callArgs).toString()
                else -> throw IllegalArgumentException("Unknown python function: $funcName")
            }
        }
    }

    private fun translateError(e: Exception): String {
        return when {
            e is ConnectException || e is UnknownHostException -> "网络连接失败"
            e is SocketTimeoutException -> "连接超时"
            else -> "下载失败"
        }
    }

    // ── task.json 落盘 ────────────────────────────────────────

    private val taskFile: File get() = File(appContext.filesDir, TASK_FILE)

    private fun persistTask(task: DownloadTask) {
        try {
            val json = JSONObject().apply {
                put("albumId", task.albumId)
                put("title", task.title)
                put("author", task.author)
                put("tags", JSONArray(task.tags))
                put("pageCount", task.pageCount)
                put("chapters", JSONArray().apply {
                    for (c in task.chapters) {
                        put(JSONObject().apply {
                            put("index", c.index)
                            put("title", c.title)
                            put("downloaded", c.downloaded)
                        })
                    }
                })
                put("selected", JSONArray(task.selected))
            }
            taskFile.writeText(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "persistTask failed", e)
        }
    }

    private fun loadTaskFromDisk(): DownloadTask? {
        if (!taskFile.exists()) return null
        return try {
            val json = JSONObject(taskFile.readText())
            val chaptersArr = json.optJSONArray("chapters") ?: JSONArray()
            val chapters = (0 until chaptersArr.length()).map { i ->
                val c = chaptersArr.getJSONObject(i)
                ChapterInfo(
                    index = c.getInt("index"),
                    title = c.optString("title", ""),
                    downloaded = c.optBoolean("downloaded", false),
                )
            }
            val selectedArr = json.optJSONArray("selected") ?: JSONArray()
            val selected = (0 until selectedArr.length()).map { selectedArr.getInt(it) }
            val tagsArr = json.optJSONArray("tags") ?: JSONArray()
            val tags = (0 until tagsArr.length()).map { tagsArr.optString(it) }
                .filter { it.isNotBlank() }
            DownloadTask(
                albumId = json.optString("albumId", ""),
                title = json.optString("title", ""),
                author = json.optString("author", ""),
                tags = tags,
                pageCount = json.optInt("pageCount", 0),
                chapters = chapters,
                selected = selected,
            )
        } catch (e: Exception) {
            Log.e(TAG, "loadTaskFromDisk failed", e)
            null
        }
    }

    private fun clearTaskFile() {
        try { taskFile.delete() } catch (_: Exception) {}
    }
}
