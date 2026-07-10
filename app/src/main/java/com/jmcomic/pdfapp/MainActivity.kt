package com.jmcomic.pdfapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jmcomic.pdfapp.ui.screen.MainScreen
import com.jmcomic.pdfapp.ui.theme.JMComicPDFTheme
import com.jmcomic.pdfapp.viewmodel.MainViewModel
import java.io.File

class MainActivity : ComponentActivity() {

    companion object { private const val TAG = "JMComicPDF" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate start")

        // Safe edge-to-edge — must be called before setContent
        try {
            enableEdgeToEdge()
        } catch (e: Exception) {
            Log.e(TAG, "enableEdgeToEdge failed", e)
        }

        setContent {
            JMComicPDFTheme {
                val viewModel: MainViewModel = viewModel()
                MainScreen(
                    viewModel = viewModel,
                    onOpenPdf = { filePath -> openPdf(filePath) }
                )
            }
        }
        Log.d(TAG, "onCreate done")
    }

    private fun openPdf(filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            Log.w(TAG, "PDF not found: $filePath")
            return
        }

        try {
            val uri = FileProvider.getUriForFile(
                this,
                "com.jmcomic.pdfapp.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "openPdf failed", e)
        }
    }
}
