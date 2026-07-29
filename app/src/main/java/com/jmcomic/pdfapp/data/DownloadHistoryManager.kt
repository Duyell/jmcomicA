package com.jmcomic.pdfapp.data

import android.util.Log
import com.jmcomic.pdfapp.model.DownloadRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Manages download history persistence via a JSON file in the app's files directory.
 * Thread-safe for single-writer use (all writes happen on Dispatchers.IO).
 */
class DownloadHistoryManager(private val filesDir: File) {

    companion object {
        private const val TAG = "JMComicPDF"
        private const val FILE_NAME = "download_history.json"
    }

    private val historyFile: File
        get() = File(filesDir, FILE_NAME)

    /** Load all records from disk. Returns empty list if file doesn't exist or is corrupt. */
    fun loadAll(): List<DownloadRecord> {
        if (!historyFile.exists()) return emptyList()
        return try {
            val json = historyFile.readText()
            if (json.isBlank()) return emptyList()
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                DownloadRecord(
                    albumId = obj.optString("albumId", ""),
                    albumTitle = obj.optString("albumTitle", ""),
                    chapterIndex = obj.optInt("chapterIndex", 0),
                    chapterTitle = obj.optString("chapterTitle", ""),
                    pdfPath = obj.optString("pdfPath", ""),
                    downloadTime = obj.optLong("downloadTime", 0L),
                    fileSize = obj.optLong("fileSize", 0L)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load download history", e)
            emptyList()
        }
    }

    /** Persist the full record list to disk. */
    fun saveAll(records: List<DownloadRecord>) {
        try {
            val arr = JSONArray()
            for (r in records) {
                arr.put(JSONObject().apply {
                    put("albumId", r.albumId)
                    put("albumTitle", r.albumTitle)
                    put("chapterIndex", r.chapterIndex)
                    put("chapterTitle", r.chapterTitle)
                    put("pdfPath", r.pdfPath)
                    put("downloadTime", r.downloadTime)
                    put("fileSize", r.fileSize)
                })
            }
            historyFile.writeText(arr.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save download history", e)
        }
    }

    /** Append a single record. Deduplicates by pdfPath (replaces existing entry). */
    fun add(record: DownloadRecord) {
        val records = loadAll().toMutableList()
        records.removeAll { it.pdfPath == record.pdfPath }
        records.add(0, record)  // newest first
        saveAll(records)
    }

    /** Remove a record by PDF path. Also deletes the PDF file from disk. */
    fun remove(pdfPath: String): Boolean {
        val records = loadAll().toMutableList()
        val removed = records.removeAll { it.pdfPath == pdfPath }
        if (removed) saveAll(records)
        // Also delete the actual file
        try { File(pdfPath).delete() } catch (_: Exception) {}
        return removed
    }

    /** Fuzzy search across albumId, albumTitle, chapterTitle. Case-insensitive. */
    fun search(query: String): List<DownloadRecord> {
        if (query.isBlank()) return loadAll()
        val q = query.trim().lowercase()
        return loadAll().filter { record ->
            record.albumId.contains(q) ||
            record.albumTitle.lowercase().contains(q) ||
            record.chapterTitle.lowercase().contains(q)
        }
    }
}
