package com.jmcomic.pdfapp.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chaquo.python.Python
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

data class DownloadUiState(
    val albumId: String = "",
    val status: DownloadStatus = DownloadStatus.Idle,
    val pdfPath: String? = null,
    val errorMessage: String? = null,
    val debugStep: String = ""
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
        // Clean up old downloads and PDFs from previous sessions
        viewModelScope.launch(Dispatchers.IO) {
            cleanupCache()
        }
    }

    fun onAlbumIdChanged(newId: String) {
        _uiState.value = _uiState.value.copy(albumId = newId)
    }

    fun startDownload() {
        val id = _uiState.value.albumId.trim()
        if (id.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                status = DownloadStatus.Error("please enter album ID"),
                errorMessage = "please enter album ID"
            )
            return
        }

        _uiState.value = _uiState.value.copy(
            status = DownloadStatus.Downloading,
            debugStep = "",
            errorMessage = null
        )

        // Clean previous PDF before starting new download
        viewModelScope.launch(Dispatchers.IO) {
            cleanupOldPdfs()
            downloadInBackground(id)
        }
    }

    private suspend fun downloadInBackground(albumId: String) {
        val steps = StringBuilder()
        try {
            val outDir = outputDir
            step(steps, "1.outputDir", outDir)

            step(steps, "2.getInstance", "OK")
            val py = withContext(Dispatchers.IO) { Python.getInstance() }

            step(steps, "3.getModule", "OK")
            val module = withContext(Dispatchers.IO) { py.getModule("jm_bridge") }

            step(steps, "4.download.start", "running...")
            val jsonStr = withContext(Dispatchers.IO) {
                module.callAttr("get_pdf_path", albumId, outDir).toString()
            }
            Log.w(TAG, "json result: " + jsonStr.substring(0, kotlin.math.min(jsonStr.length, 500)))

            val json = JSONObject(jsonStr)
            step(steps, "5.parse", "OK")

            val success = json.optBoolean("success", false)
            val pdfPath = json.optString("pdf_path", "")
            val error = json.optString("error", "")
            val tb = json.optString("traceback", "")

            if (success) {
                step(steps, "DONE", pdfPath)
                _uiState.value = _uiState.value.copy(
                    status = DownloadStatus.Success, pdfPath = pdfPath,
                    debugStep = steps.toString(), errorMessage = null
                )
            } else {
                val errMsg = when {
                    tb.isNotBlank() -> tb
                    error.isNotBlank() -> error
                    else -> "download failed, no details"
                }.let { it.substring(0, kotlin.math.min(it.length, 500)) }
                step(steps, "FAIL", errMsg)
                _uiState.value = _uiState.value.copy(
                    status = DownloadStatus.Error(errMsg),
                    errorMessage = errMsg, debugStep = steps.toString()
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Python download failed", e)
            val errMsg = e.javaClass.simpleName + ": " + (e.message ?: "(null)")
            step(steps, "CRASH", errMsg)
            _uiState.value = _uiState.value.copy(
                status = DownloadStatus.Error(errMsg),
                errorMessage = errMsg, debugStep = steps.toString()
            )
        }
    }

    private fun step(sb: StringBuilder, label: String, result: String) {
        sb.append(label).append(" -> ").append(result).append("\n")
        _uiState.value = _uiState.value.copy(debugStep = sb.toString())
    }

    private val outputDir: String
        get() = File(getApplication<Application>().filesDir, "pdf_output").also {
            if (!it.exists()) it.mkdirs()
        }.absolutePath

    fun reset() {
        _uiState.value = DownloadUiState()
        viewModelScope.launch(Dispatchers.IO) {
            cleanupOldPdfs()
        }
    }

    private fun cleanupOldPdfs() {
        try {
            val dir = File(outputDir)
            if (dir.isDirectory) {
                dir.listFiles()?.filter { it.extension.equals("pdf", ignoreCase = true) }
                    ?.forEach { it.delete() }
                // Also clean downloads dir
                val downloads = File(dir, "downloads")
                if (downloads.isDirectory) {
                    downloads.deleteRecursively()
                }
            }
        } catch (_: Exception) { }
    }

    private fun cleanupCache() {
        try {
            val dir = File(outputDir)
            if (dir.isDirectory) {
                dir.listFiles()?.forEach { it.deleteRecursively() }
            }
        } catch (_: Exception) { }
    }
}
