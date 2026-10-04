package com.jmcomic.pdfapp.viewmodel

import android.app.Application
import android.content.ClipboardManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chaquo.python.Python
import com.jmcomic.pdfapp.data.DownloadHistoryManager
import com.jmcomic.pdfapp.data.RecentIdsPrefs
import com.jmcomic.pdfapp.model.AlbumInfo
import com.jmcomic.pdfapp.model.ChapterInfo
import com.jmcomic.pdfapp.model.ChapterDownloadResult
import com.jmcomic.pdfapp.model.DownloadRecord
import com.jmcomic.pdfapp.model.DownloadStatus
import com.jmcomic.pdfapp.model.HomeUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    private var pollJob: Job? = null

    val outputDir: String
        get() = File(getApplication<Application>().filesDir, "pdf_output").also {
            if (!it.exists()) it.mkdirs()
        }.absolutePath

    /** 封面缓存目录（Python 下载封面后存放于此，UI 直接读磁盘）。 */
    val coversDir: String
        get() = File(getApplication<Application>().filesDir, "covers").also {
            if (!it.exists()) it.mkdirs()
        }.absolutePath

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

    fun onCardDownloadTapped() {
        val state = _uiState.value
        val album = state.resolvedAlbum ?: return
        val chapters = state.chapters
        when {
            chapters.isEmpty() -> _uiState.value = state.copy(
                status = DownloadStatus.Error("无章节信息，请重新解析"),
                errorMessage = "无章节信息，请重新解析",
            )

            chapters.size == 1 -> {
                // Single chapter — skip if already downloaded
                if (chapters[0].downloaded) {
                    _uiState.value = state.copy(
                        status = DownloadStatus.Error("该章节已下载"),
                        errorMessage = "该章节已下载",
                    )
                } else {
                    viewModelScope.launch(Dispatchers.IO) {
                        downloadSingleChapter(album.albumId, album.title)
                    }
                }
            }

            else -> {
                // Multi-chapter — show dialog, pre-select non-downloaded
                _uiState.value = state.copy(
                    status = DownloadStatus.Idle,
                    showChapterDialog = true,
                    selectedChapters = chapters.filter { !it.downloaded }.map { it.index }.toSet(),
                    progressMessage = "",
                )
            }
        }
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

        _uiState.value = state.copy(
            showChapterDialog = false,
            status = DownloadStatus.Downloading,
            progressMessage = "准备下载 ${selected.size} 章...",
            progressFraction = null,
            errorMessage = null,
            chapterResults = emptyList(),
            currentChapterIndex = -1,
            totalSelectedCount = selected.size,
            pdfPath = null,
        )

        viewModelScope.launch(Dispatchers.IO) {
            downloadSelectedChapters(selected.toList(), album.albumId, album.title)
        }
    }

    // ── Download logic ───────────────────────────────────────

    private suspend fun downloadSingleChapter(albumId: String, albumTitle: String) {
        _uiState.value = _uiState.value.copy(
            status = DownloadStatus.Downloading,
            progressMessage = "准备下载...",
            progressFraction = null,
        )

        // Clean old PDFs
        cleanupPdfDir()

        pollJob = viewModelScope.launch(Dispatchers.IO) { pollProgress() }

        try {
            val jsonStr = callPython("get_pdf_path", albumId, outputDir, "")
            Log.d(TAG, "json result: " + jsonStr.take(500))

            val json = JSONObject(jsonStr)
            if (json.optBoolean("success", false)) {
                val pdfPath = json.optString("pdf_path", "")
                val fileName = File(pdfPath).name
                _uiState.value = _uiState.value.copy(
                    status = DownloadStatus.Success,
                    pdfPath = pdfPath,
                    progressFraction = null,
                    progressMessage = "",
                    errorMessage = null,
                )
                // Save record
                val chapterTitle = _uiState.value.chapters.firstOrNull()?.title
                    ?: albumTitle.ifBlank { fileName }
                historyManager.add(DownloadRecord(
                    albumId = albumId,
                    albumTitle = albumTitle.ifBlank { fileName },
                    chapterIndex = 0,
                    chapterTitle = chapterTitle,
                    pdfPath = pdfPath,
                    downloadTime = System.currentTimeMillis(),
                    fileSize = File(pdfPath).length(),
                ))
                refreshChapterDownloadedFlags(albumId)
            } else {
                val msg = json.optString("user_message", "下载失败")
                _uiState.value = _uiState.value.copy(
                    status = DownloadStatus.Error(msg),
                    errorMessage = msg,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "download failed", e)
            _uiState.value = _uiState.value.copy(
                status = DownloadStatus.Error(translateError(e)),
                errorMessage = translateError(e),
            )
        } finally {
            pollJob?.cancel()
            try { File(outputDir, "progress.json").delete() } catch (_: Exception) {}
        }
    }

    private suspend fun downloadSelectedChapters(
        indices: List<Int>,
        albumId: String,
        albumTitle: String,
    ) {
        pollJob = viewModelScope.launch(Dispatchers.IO) { pollProgress() }

        try {
            val indicesJson = JSONArray(indices).toString()
            val jsonStr = callPython(
                "download_selected_chapters",
                albumId,
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
                            albumId = albumId,
                            albumTitle = albumTitle,
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
                _uiState.value = _uiState.value.copy(
                    status = DownloadStatus.Success,
                    pdfPath = firstPdf,
                    chapterResults = results,
                    progressFraction = null,
                    progressMessage = "",
                    errorMessage = if (allSuccess) null
                        else "${results.count { it.error != null }} 章失败",
                )
                refreshChapterDownloadedFlags(albumId)
            } else {
                val msg = json.optString("user_message", "下载失败")
                _uiState.value = _uiState.value.copy(
                    status = DownloadStatus.Error(msg),
                    errorMessage = msg,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "download_selected failed", e)
            _uiState.value = _uiState.value.copy(
                status = DownloadStatus.Error(translateError(e)),
                errorMessage = translateError(e),
            )
        } finally {
            pollJob?.cancel()
            try { File(outputDir, "progress.json").delete() } catch (_: Exception) {}
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
                _uiState.value = _uiState.value.copy(
                    progressMessage = message,
                    progressFraction = fraction,
                )
            } catch (_: Exception) {}
        }
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
                "get_pdf_path" -> module.callAttr("get_pdf_path", *callArgs).toString()
                "download_selected_chapters" -> module.callAttr("download_selected_chapters", *callArgs).toString()
                else -> throw IllegalArgumentException("Unknown python function: $funcName")
            }
        }
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
    }

    fun dismissSuccess() {
        _uiState.value = _uiState.value.copy(
            status = DownloadStatus.Idle,
            pdfPath = null,
            chapterResults = emptyList(),
        )
    }

    /** Only clean the temp downloads dir, not existing PDFs (v1.3 multi-comic). */
    private fun cleanupPdfDir() {
        try {
            val downloads = File(outputDir, "downloads")
            if (downloads.isDirectory) downloads.deleteRecursively()
        } catch (_: Exception) {}
    }
}
