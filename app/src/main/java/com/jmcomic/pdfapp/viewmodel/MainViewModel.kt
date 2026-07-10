package com.jmcomic.pdfapp.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

data class PdfInfo(
    val name: String,
    val path: String
)

data class DownloadUiState(
    val albumId: String = "",
    val status: DownloadStatus = DownloadStatus.Idle,
    val pdfPath: String? = null,
    val errorMessage: String? = null,
    val lastPdf: PdfInfo? = null,
    val progressMessage: String = "",
    val progressFraction: Float? = null  // null = indeterminate
)

sealed class DownloadStatus {
    data object Idle : DownloadStatus()
    data object Downloading : DownloadStatus()
    data object Success : DownloadStatus()
    data class Error(val message: String) : DownloadStatus()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object { private const val TAG = "JMComicPDF" }

    private val _uiState = MutableStateFlow(DownloadUiState())
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            scanExistingPdf()
        }
    }

    private fun scanExistingPdf() {
        val dir = File(outputDir)
        if (!dir.isDirectory) return
        val pdfs = dir.listFiles()?.filter {
            it.extension.equals("pdf", ignoreCase = true) && it.isFile
        }?.sortedByDescending { it.lastModified() }
        val latest = pdfs?.firstOrNull()
        if (latest != null) {
            _uiState.value = _uiState.value.copy(
                lastPdf = PdfInfo(name = latest.name, path = latest.absolutePath)
            )
        }
    }

    fun onAlbumIdChanged(newId: String) {
        _uiState.value = _uiState.value.copy(albumId = newId)
    }

    fun startDownload() {
        val id = _uiState.value.albumId.trim()
        if (id.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                status = DownloadStatus.Error("请输入车号"),
                errorMessage = "请输入车号"
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            status = DownloadStatus.Downloading,
            progressMessage = "准备中...",
            progressFraction = null,
            errorMessage = null
        )

        viewModelScope.launch(Dispatchers.IO) {
            cleanupOldPdfs()
            downloadInBackground(id)
        }
    }

    private suspend fun downloadInBackground(albumId: String) {
        // Launch progress polling coroutine
        val pollJob = viewModelScope.launch(Dispatchers.IO) {
            pollProgress()
        }

        try {
            val outDir = outputDir
            val py = withContext(Dispatchers.IO) { Python.getInstance() }
            val module = withContext(Dispatchers.IO) { py.getModule("jm_bridge") }
            val jsonStr = withContext(Dispatchers.IO) {
                module.callAttr("get_pdf_path", albumId, outDir).toString()
            }
            Log.d(TAG, "json result: " + jsonStr.take(500))

            val json = JSONObject(jsonStr)
            val success = json.optBoolean("success", false)
            val pdfPath = json.optString("pdf_path", "")
            val error = json.optString("error", "")
            val tb = json.optString("traceback", "")

            if (success) {
                _uiState.value = _uiState.value.copy(
                    status = DownloadStatus.Success,
                    pdfPath = pdfPath,
                    progressFraction = null,
                    progressMessage = "",
                    errorMessage = null,
                    lastPdf = PdfInfo(name = File(pdfPath).name, path = pdfPath)
                )
            } else {
                val errMsg = (if (tb.isNotBlank()) tb else error)
                    .take(500).ifBlank { "下载失败，无详细信息" }
                _uiState.value = _uiState.value.copy(
                    status = DownloadStatus.Error(errMsg),
                    errorMessage = errMsg,
                    progressFraction = null,
                    progressMessage = ""
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Python download failed", e)
            val errMsg = "${e.javaClass.simpleName}: ${e.message ?: "(null)"}"
            _uiState.value = _uiState.value.copy(
                status = DownloadStatus.Error(errMsg),
                errorMessage = errMsg,
                progressFraction = null,
                progressMessage = ""
            )
        } finally {
            pollJob.cancel()
            // Delete stale progress file
            try { File(outputDir, "progress.json").delete() } catch (_: Exception) {}
        }
    }

    private suspend fun pollProgress() {
        while (true) {
            delay(400)
            try {
                val file = File(outputDir, "progress.json")
                if (!file.exists()) continue
                val content = file.readText()
                val json = JSONObject(content)
                val message = json.optString("message", "")
                val current = json.optInt("current", 0)
                val total = json.optInt("total", 0)
                val fraction = if (total > 0) current.toFloat() / total else null
                _uiState.value = _uiState.value.copy(
                    progressMessage = message,
                    progressFraction = fraction
                )
            } catch (_: Exception) {}
        }
    }

    private val outputDir: String
        get() = File(getApplication<Application>().filesDir, "pdf_output").also {
            if (!it.exists()) it.mkdirs()
        }.absolutePath

    fun deleteLastPdf() {
        val info = _uiState.value.lastPdf ?: return
        try { File(info.path).delete() } catch (_: Exception) {}
        _uiState.value = _uiState.value.copy(lastPdf = null)
        // Also clear success state
        if (_uiState.value.status is DownloadStatus.Success) {
            _uiState.value = _uiState.value.copy(
                status = DownloadStatus.Idle,
                pdfPath = null
            )
        }
    }

    private fun cleanupOldPdfs() {
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
