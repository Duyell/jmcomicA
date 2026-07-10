package com.jmcomic.pdfapp

import android.util.Log
import com.chaquo.python.android.PyApplication
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class JMComicApp : PyApplication() {

    companion object { private const val TAG = "JMComicApp" }

    override fun onCreate() {
        // Install crash handler BEFORE any initialization
        val appContext = this
        val origHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val logFile = File(appContext.filesDir, "crash_log.txt")
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                logFile.appendText("=== $ts ===\nThread: ${thread.name}\n${sw}\n\n")
                Log.e(TAG, "CRASH: ${throwable.javaClass.simpleName}: ${throwable.message}")
            } catch (_: Exception) { }
            origHandler?.uncaughtException(thread, throwable)
        }

        Log.d(TAG, "PyApplication init starting...")
        try {
            super.onCreate()
            Log.d(TAG, "PyApplication init OK")
        } catch (e: Exception) {
            Log.e(TAG, "PyApplication init FAILED", e)
        }
    }
}
