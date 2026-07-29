package com.jmcomic.pdfapp.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chaquo.python.Python
import com.jmcomic.pdfapp.data.DownloadHistoryManager
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
    }

    private val historyManager = DownloadHistoryManager(application.filesDir)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null

    val outputDir: String
        get() = File(getApplication<Application>().filesDir, "pdf_output").also {
            if (!it.exists()) it.mkdirs()
        }.absolutePath

    // ── Input ────────────────────────────────────────────────

    fun onAlbumIdChanged(newId: String) {
        _uiState.value = _uiState.value.copy(albumId = newId)
    }

    // ── Main action: fetch info → decide single / multi chapter ──

    fun onDownloadTapped() {
        val id = _uiState.value.albumId.trim()
        if (id.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                status = DownloadStatus.Error("请输入漫画ID"),
                errorMessage = "请输入漫画ID"
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            status = DownloadStatus.FetchingInfo,
            progressMessage = "获取漫画信息...",
            progressFraction = null,
            errorMessage = null,
            chapters = emptyList(),
            chapterResults = emptyList(),
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonStr = callPython("get_album_info", id, "")
                val json = JSONObject(jsonStr)

                if (json.optBoolean("success", false)) {
                    val title = json.optString("title", "")
                    val chaptersArr = json.optJSONArray("chapters") ?: JSONArray()
                    val chapters = (0 until chaptersArr.length()).map { i ->
                        val ch = chaptersArr.getJSONObject(i)
                        ChapterInfo(
                            index = ch.getInt("index"),
                            title = ch.optString("title", "第${ch.getInt("index") + 1}章")
                        )
                    }

                    if (chapters.size <= 1) {
                        // Single chapter — download directly (legacy flow)
                        _uiState.value = _uiState.value.copy(
                            albumTitle = title,
                            chapters = chapters,
                        )
                        downloadSingleChapter(id)
                    } else {
                        // Multi-chapter — show selection dialog
                        _uiState.value = _uiState.value.copy(
                            status = DownloadStatus.Idle,
                            albumTitle = title,
                            chapters = chapters,
                            showChapterDialog = true,
                            selectedChapters = chapters.map { it.index }.toSet(), // all selected by default
                            progressMessage = "",
                        )
                    }
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
        val selected = _uiState.value.selectedChapters
        if (selected.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                status = DownloadStatus.Error("请至少选择一章"),
                errorMessage = "请至少选择一章",
            )
            return
        }

        _uiState.value = _uiState.value.copy(
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
            downloadSelectedChapters(selected.toList())
        }
    }

    // ── Download logic ───────────────────────────────────────

    private suspend fun downloadSingleChapter(albumId: String) {
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
                    ?: _uiState.value.albumTitle.ifBlank { fileName }
                historyManager.add(DownloadRecord(
                    albumId = albumId,
                    albumTitle = _uiState.value.albumTitle.ifBlank { fileName },
                    chapterIndex = 0,
                    chapterTitle = chapterTitle,
                    pdfPath = pdfPath,
                    downloadTime = System.currentTimeMillis(),
                    fileSize = File(pdfPath).length(),
                ))
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

    private suspend fun downloadSelectedChapters(indices: List<Int>) {
        pollJob = viewModelScope.launch(Dispatchers.IO) { pollProgress() }

        try {
            val indicesJson = JSONArray(indices).toString()
            val jsonStr = callPython(
                "download_selected_chapters",
                _uiState.value.albumId.trim(),
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
                val albumId = _uiState.value.albumId.trim()
                val albumTitle = _uiState.value.albumTitle
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
                _uiState.value = _uiState.value.copy(
                    status = if (allSuccess) DownloadStatus.Success else DownloadStatus.Idle,
                    pdfPath = firstPdf,
                    chapterResults = results,
                    progressFraction = null,
                    progressMessage = "",
                    errorMessage = if (allSuccess) null
                        else "${results.count { it.error != null }} 章失败",
                )
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

    private fun cleanupPdfDir() {
        try {
            val dir = File(outputDir)
            if (dir.isDirectory) {
                dir.listFiles()?.filter { it.extension.equals("pdf", ignoreCase = true) }
                    ?.forEach { it.delete() }
                val downloads = File(dir, "downloads")
                if (downloads.isDirectory) downloads.deleteRecursively()
            }
        } catch (_: Exception) {}
    }
}
