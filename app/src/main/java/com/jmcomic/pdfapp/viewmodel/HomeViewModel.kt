package com.jmcomic.pdfapp.viewmodel

import android.app.Application
import android.content.ClipboardManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chaquo.python.Python
import com.jmcomic.pdfapp.data.DownloadHistoryManager
import com.jmcomic.pdfapp.data.RecentIdsPrefs
import com.jmcomic.pdfapp.download.DownloadManager
import com.jmcomic.pdfapp.model.AlbumInfo
import com.jmcomic.pdfapp.model.ChapterInfo
import com.jmcomic.pdfapp.model.DownloadStatus
import com.jmcomic.pdfapp.model.DownloadTask
import com.jmcomic.pdfapp.model.HomeUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "JMComicPDF"

        /** URL → ID：任意域名的 /album/{id}、/photo/{id} 或 ?id={id}，大小写不敏感。 */
        private val URL_ID_REGEX = Regex("""(?i)(?:/(?:album|photo)/(\d+)|[?&]id=(\d+))""")

        /** 解析兜底：任意连续数字（【JM350234】等分享格式）。 */
        private val ANY_DIGITS_REGEX = Regex("""\d+""")
    }

    /**
     * 实时规整：URL → 纯数字；其余原样返回。
     * 注意：实时规整只认 /album/、/photo/、?id= 片段，不做 \d+ 兜底，
     * 否则手动输入 URL 时域名里的 "18" 会被截断、URL 永远打不完。
     */
    private fun normalizeAlbumId(raw: String): String {
        val t = raw.trim()
        URL_ID_REGEX.find(t)?.let { m ->
            val id = m.groupValues[1].ifEmpty { m.groupValues[2] }
            if (id.isNotEmpty()) return id
        }
        return t
    }

    private val historyManager = DownloadHistoryManager(application.filesDir)

    private val _uiState = MutableStateFlow(
        HomeUiState(recentIds = RecentIdsPrefs.load(application))
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        // 接入全局下载协调器：镜像下载状态 + 恢复未完成任务
        DownloadManager.init(application)
        viewModelScope.launch {
            DownloadManager.state.collect { ds ->
                val prev = _uiState.value
                _uiState.value = prev.copy(
                    status = ds.status,
                    progressMessage = ds.progressMessage,
                    progressFraction = ds.progressFraction,
                    chapterResults = ds.chapterResults,
                    pdfPath = ds.pdfPath,
                    errorMessage = ds.errorMessage,
                )
                // 下载成功后刷新章节"已下载"标记
                if (ds.status is DownloadStatus.Success && prev.status !is DownloadStatus.Success) {
                    ds.activeTask?.let { refreshChapterDownloadedFlags(it.albumId) }
                }
            }
        }
        viewModelScope.launch {
            DownloadManager.pendingTask.collect { task ->
                val prev = _uiState.value
                // 进程被杀重启后：用任务快照恢复信息卡片
                if (task != null && prev.pendingTask == null && prev.resolvedAlbum == null) {
                    _uiState.value = prev.copy(
                        albumId = task.albumId,
                        resolvedAlbum = AlbumInfo(
                            albumId = task.albumId,
                            title = task.title,
                            author = task.author,
                            tags = task.tags,
                            pageCount = task.pageCount,
                        ),
                        chapters = task.chapters,
                    )
                }
                _uiState.value = _uiState.value.copy(pendingTask = task)
            }
        }
    }

    // ── Input ────────────────────────────────────────────────

    fun onAlbumIdChanged(raw: String) {
        val normalized = normalizeAlbumId(raw)
        val prev = _uiState.value
        // 输入与已解析 ID 不一致 → 失效信息卡片，回到解析前
        val invalidated = prev.resolvedAlbum != null && normalized != prev.resolvedAlbum.albumId
        _uiState.value = prev.copy(
            albumId = normalized,
            resolvedAlbum = if (invalidated) null else prev.resolvedAlbum,
            chapters = if (invalidated) emptyList() else prev.chapters,
            showChapterDialog = if (invalidated) false else prev.showChapterDialog,
            selectedChapters = if (invalidated) emptySet() else prev.selectedChapters,
            chapterResults = if (invalidated) emptyList() else prev.chapterResults,
            pdfPath = if (invalidated) null else prev.pdfPath,
            errorMessage = if (invalidated) null else prev.errorMessage,
            status = if (invalidated && prev.status is DownloadStatus.Error) DownloadStatus.Idle else prev.status,
        )
    }

    // ── Step 1: resolve album info ───────────────────────────

    fun onResolveTapped() {
        val state = _uiState.value
        // 解析时兜底 \d+：支持【JM350234】等分享格式
        val id = ANY_DIGITS_REGEX.find(state.albumId)?.value ?: state.albumId.trim()
        if (id.isEmpty()) {
            _uiState.value = state.copy(
                status = DownloadStatus.Error("请输入漫画ID"),
                errorMessage = "请输入漫画ID"
            )
            return
        }

        _uiState.value = state.copy(
            albumId = id,
            status = DownloadStatus.FetchingInfo,
            progressMessage = "获取漫画信息...",
            progressFraction = null,
            errorMessage = null,
            resolvedAlbum = null,
            chapters = emptyList(),
            chapterResults = emptyList(),
            pdfPath = null,
            showChapterDialog = false,
            selectedChapters = emptySet(),
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonStr = callPython("get_album_info", id, "", coversDir)
                val json = JSONObject(jsonStr)

                if (json.optBoolean("success", false)) {
                    val title = json.optString("title", "")
                    val chaptersArr = json.optJSONArray("chapters") ?: JSONArray()
                    // Check which chapters already exist on disk
                    val existingIndices = historyManager.getDownloadedChapterIndices(id)
                    val chapters = (0 until chaptersArr.length()).map { i ->
                        val ch = chaptersArr.getJSONObject(i)
                        ChapterInfo(
                            index = ch.getInt("index"),
                            title = ch.optString("title", "第${ch.getInt("index") + 1}章"),
                            downloaded = ch.getInt("index") in existingIndices
                        )
                    }

                    // 解析成功才写入历史
                    RecentIdsPrefs.add(getApplication(), id)

                    _uiState.value = _uiState.value.copy(
                        status = DownloadStatus.Idle,
                        resolvedAlbum = AlbumInfo(
                            albumId = id,
                            title = title,
                            author = json.optString("author", ""),
                            tags = parseStringArray(json.optJSONArray("tags")),
                            pageCount = json.optInt("page_count", 0),
                        ),
                        chapters = chapters,
                        recentIds = RecentIdsPrefs.load(getApplication()),
                        progressMessage = "",
                    )
                } else {
                    val msg = json.optString("user_message", "获取信息失败")
                    _uiState.value = _uiState.value.copy(
                        status = DownloadStatus.Error(msg),
                        errorMessage = msg,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "get_album_info failed", e)
                _uiState.value = _uiState.value.copy(
                    status = DownloadStatus.Error(translateError(e)),
                    errorMessage = translateError(e),
                )
            }
        }
    }

    // ── Step 2: download from the info card (no re-fetch) ───

    /**
     * v1.4.1：单章节与多章节统一——一律弹出章节选择对话框，
     * 预选未下载章节；下载统一走 download_selected_chapters。
     */
    fun onCardDownloadTapped() {
        val state = _uiState.value
        if (state.resolvedAlbum == null) return
        if (state.chapters.isEmpty()) {
            _uiState.value = state.copy(
                status = DownloadStatus.Error("无章节信息，请重新解析"),
                errorMessage = "无章节信息，请重新解析",
            )
            return
        }
        // 全部章节已下载：按钮已禁用，这里兜底不弹窗（避免出现"全是已下载"的选择框）
        if (state.chapters.all { it.downloaded }) return
        // 单章节同样弹窗，保持交互一致
        _uiState.value = state.copy(
            status = DownloadStatus.Idle,
            showChapterDialog = true,
            selectedChapters = state.chapters.filter { !it.downloaded }.map { it.index }.toSet(),
            progressMessage = "",
        )
    }

    /** 关闭信息卡片（右上角 ×）：回到解析前的初始状态。 */
    fun onAlbumCardDismiss() {
        _uiState.value = _uiState.value.copy(
            albumId = "",
            resolvedAlbum = null,
            chapters = emptyList(),
            showChapterDialog = false,
            selectedChapters = emptySet(),
            chapterResults = emptyList(),
            pdfPath = null,
            errorMessage = null,
            status = DownloadStatus.Idle,
            progressMessage = "",
            progressFraction = null,
        )
    }

    // ── Chapter dialog ───────────────────────────────────────

    fun onChapterDialogDismiss() {
        _uiState.value = _uiState.value.copy(showChapterDialog = false)
    }

    fun onToggleChapter(index: Int) {
        val current = _uiState.value.selectedChapters.toMutableSet()
        if (index in current) current.remove(index) else current.add(index)
        _uiState.value = _uiState.value.copy(selectedChapters = current)
    }

    fun onSelectAll() {
        val all = _uiState.value.chapters.map { it.index }.toSet()
        _uiState.value = _uiState.value.copy(selectedChapters = all)
    }

    fun onDeselectAll() {
        _uiState.value = _uiState.value.copy(selectedChapters = emptySet())
    }

    fun onConfirmChapterSelection() {
        val state = _uiState.value
        val album = state.resolvedAlbum ?: return
        val selected = state.selectedChapters
        if (selected.isEmpty()) {
            _uiState.value = state.copy(
                status = DownloadStatus.Error("请至少选择一章"),
                errorMessage = "请至少选择一章",
            )
            return
        }

        _uiState.value = state.copy(showChapterDialog = false)

        // 交给 DownloadManager（前台服务保活 + 任务落盘）
        val task = DownloadTask(
            albumId = album.albumId,
            title = album.title,
            author = album.author,
            tags = album.tags,
            pageCount = album.pageCount,
            chapters = state.chapters,
            selected = selected.toList(),
        )
        DownloadManager.start(getApplication(), task)
    }

    // ── Pending task (process-kill recovery) ─────────────────

    fun onResumePending() {
        DownloadManager.resumePending()
    }

    fun onDiscardPending() {
        DownloadManager.discardPending()
    }

    // ── Smart input: clipboard & history ─────────────────────

    /** 读取剪贴板并填入输入框（走统一的 URL 规整链路，不自动解析）。 */
    fun onPasteTapped() {
        val app = getApplication<Application>()
        val cm = app.getSystemService(ClipboardManager::class.java) ?: return
        val clip = cm.primaryClip ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0)?.coerceToText(app)?.toString()?.trim()
        if (!text.isNullOrEmpty()) onAlbumIdChanged(text)
    }

    /** 点击历史 chip：填充并自动解析。 */
    fun onRecentIdTapped(id: String) {
        _uiState.value = _uiState.value.copy(albumId = id)
        onResolveTapped()
    }

    fun onClearRecentIds() {
        RecentIdsPrefs.clear(getApplication())
        _uiState.value = _uiState.value.copy(recentIds = emptyList())
    }

    // ── Helpers ──────────────────────────────────────────────

    private val coversDir: String
        get() = java.io.File(getApplication<Application>().filesDir, "covers").also {
            if (!it.exists()) it.mkdirs()
        }.absolutePath

    /**
     * Call a named function in jm_bridge.py via Chaquopy.
     */
    private suspend fun callPython(funcName: String, vararg args: String): String {
        return withContext(Dispatchers.IO) {
            val py = Python.getInstance()
            val module = py.getModule("jm_bridge")
            val callArgs = arrayOf<Any>(*args)
            when (funcName) {
                "get_album_info" -> module.callAttr("get_album_info", *callArgs).toString()
                else -> throw IllegalArgumentException("Unknown python function: $funcName")
            }
        }
    }

    /** 下载成功后刷新章节"已下载"标记（磁盘读，很快），保证回到卡片后标记最新。 */
    private fun refreshChapterDownloadedFlags(albumId: String) {
        val existing = historyManager.getDownloadedChapterIndices(albumId)
        _uiState.value = _uiState.value.copy(
            chapters = _uiState.value.chapters.map {
                it.copy(downloaded = it.index in existing)
            }
        )
    }

    private fun parseStringArray(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
    }

    private fun translateError(e: Exception): String {
        return when {
            e is ConnectException || e is UnknownHostException -> "网络连接失败"
            e is SocketTimeoutException -> "连接超时"
            else -> "下载失败"
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(
            status = DownloadStatus.Idle,
            errorMessage = null,
        )
        DownloadManager.acknowledge()
    }

    fun dismissSuccess() {
        _uiState.value = _uiState.value.copy(
            status = DownloadStatus.Idle,
            pdfPath = null,
            chapterResults = emptyList(),
        )
        DownloadManager.acknowledge()
    }
}
