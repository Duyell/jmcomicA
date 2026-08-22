package com.jmcomic.pdfapp.data

import android.util.Log
import com.jmcomic.pdfapp.model.ComicGroup
import com.jmcomic.pdfapp.model.ComicInfo
import com.jmcomic.pdfapp.model.DownloadRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Manages download history.  v1.3 stores PDFs under
 *   pdf_output/{album_id}/[JM{album_id}] ...
 * and writes a comic_info.json in each comic directory.
 */
class DownloadHistoryManager(private val filesDir: File) {

    companion object {
        private const val TAG = "JMComicPDF"
        private const val HISTORY_FILE = "download_history.json"
    }

    private val pdfDir: File
        get() = File(filesDir, "pdf_output").also { it.mkdirs() }

    // ── History JSON (legacy + sync) ──────────────────────────

    private val historyFile: File get() = File(pdfDir, HISTORY_FILE)

    fun loadAll(): List<DownloadRecord> {
        if (!historyFile.exists()) return emptyList()
        return try {
            val json = historyFile.readText()
            if (json.isBlank()) return emptyList()
            val arr = JSONArray(json)
            (0 until arr.length()).map { i -> parseRecord(arr.getJSONObject(i)) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load history", e)
            emptyList()
        }
    }

    fun saveAll(records: List<DownloadRecord>) {
        try {
            val arr = JSONArray()
            for (r in records) arr.put(serializeRecord(r))
            historyFile.writeText(arr.toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save history", e)
        }
    }

    fun add(record: DownloadRecord) {
        val records = loadAll().toMutableList()
        records.removeAll { it.pdfPath == record.pdfPath }
        records.add(0, record)
        saveAll(records)
    }

    fun remove(pdfPath: String): Boolean {
        val records = loadAll().toMutableList()
        val removed = records.removeAll { it.pdfPath == pdfPath }
        if (removed) saveAll(records)
        try { File(pdfPath).delete() } catch (_: Exception) {}
        return removed
    }

    /** Delete an entire comic directory and all its records. */
    fun removeComic(albumId: String) {
        val dir = File(pdfDir, albumId)
        if (dir.isDirectory) dir.deleteRecursively()
        deleteCover(albumId)
        val records = loadAll().toMutableList()
        records.removeAll { it.albumId == albumId }
        saveAll(records)
    }

    /** Remove a cached cover image if present. */
    fun deleteCover(albumId: String) {
        try { File(File(filesDir, "covers"), "$albumId.jpg").delete() } catch (_: Exception) {}
    }

    // ── Comic grouping ────────────────────────────────────────

    /** Scan filesystem to find all downloaded comics and their chapters. */
    fun loadGroups(): List<ComicGroup> {
        val records = loadAll()
        if (records.isEmpty()) {
            // Rebuild from filesystem scan
            return scanFilesystem()
        }
        // Group records by albumId
        val byAlbum = records.groupBy { it.albumId }
        return byAlbum.map { (albumId, chapters) ->
            // Try to get title from comic_info.json
            val info = readComicInfo(albumId)
            ComicGroup(
                albumId = albumId,
                albumTitle = info?.title
                    ?: chapters.firstOrNull()?.albumTitle ?: "",
                chapters = chapters.sortedBy { it.chapterIndex }
            )
        }.sortedByDescending { it.chapters.maxOfOrNull { r -> r.downloadTime } ?: 0L }
    }

    /** Get a set of downloaded chapter indices for a given album. */
    fun getDownloadedChapterIndices(albumId: String): Set<Int> {
        val dir = File(pdfDir, albumId)
        if (!dir.isDirectory) return emptySet()
        return dir.listFiles()
            ?.filter { it.extension.equals("pdf", ignoreCase = true) }
            ?.mapNotNull { extractChapterIndex(it.name) }
            ?.toSet() ?: emptySet()
    }

    /** Search across all comics, matching albumId/title/chapter title. */
    fun search(query: String): List<ComicGroup> {
        val q = query.trim().lowercase()
        if (q.isBlank()) return loadGroups()
        return loadGroups().filter { group ->
            group.albumId.contains(q) ||
            group.albumTitle.lowercase().contains(q) ||
            group.chapters.any { it.chapterTitle.lowercase().contains(q) }
        }
    }

    fun toggleGroupExpanded(group: ComicGroup): ComicGroup {
        return group.copy(expanded = !group.expanded)
    }

    // ── Internal ──────────────────────────────────────────────

    private fun parseRecord(obj: JSONObject) = DownloadRecord(
        albumId = obj.optString("albumId", ""),
        albumTitle = obj.optString("albumTitle", ""),
        chapterIndex = obj.optInt("chapterIndex", 0),
        chapterTitle = obj.optString("chapterTitle", ""),
        pdfPath = obj.optString("pdfPath", ""),
        downloadTime = obj.optLong("downloadTime", 0L),
        fileSize = obj.optLong("fileSize", 0L)
    )

    private fun serializeRecord(r: DownloadRecord) = JSONObject().apply {
        put("albumId", r.albumId)
        put("albumTitle", r.albumTitle)
        put("chapterIndex", r.chapterIndex)
        put("chapterTitle", r.chapterTitle)
        put("pdfPath", r.pdfPath)
        put("downloadTime", r.downloadTime)
        put("fileSize", r.fileSize)
    }

    private fun readComicInfo(albumId: String): ComicInfo? {
        val file = File(pdfDir, "$albumId/comic_info.json")
        if (!file.exists()) return null
        return try {
            val obj = JSONObject(file.readText())
            ComicInfo(
                albumId = obj.optString("album_id", albumId),
                title = obj.optString("title", "")
            )
        } catch (_: Exception) { null }
    }

    /**
     * Extract 0-based chapter index from filename like
     * "[JM1081229] 002_xxx.pdf" → 1  (002 is 1-based, API uses 0-based)
     */
    private fun extractChapterIndex(filename: String): Int? {
        val match = Regex("""\[JM\d+\]\s*(\d+)_.*\.pdf""").find(filename)
        val oneBased = match?.groupValues?.get(1)?.toIntOrNull() ?: return null
        return (oneBased - 1).coerceAtLeast(0)
    }

    /** Scan filesystem (used when history JSON is empty/missing). */
    private fun scanFilesystem(): List<ComicGroup> {
        val dirs = pdfDir.listFiles()?.filter { it.isDirectory } ?: return emptyList()
        return dirs.mapNotNull { dir ->
            val info = readComicInfo(dir.name)
            val pdfs = dir.listFiles()
                ?.filter { it.extension.equals("pdf", ignoreCase = true) }
                ?.sortedBy { it.name } ?: emptyList()
            if (pdfs.isEmpty()) return@mapNotNull null
            val chapters = pdfs.mapIndexed { idx, pdf ->
                val chapterIdx = extractChapterIndex(pdf.name) ?: idx
                DownloadRecord(
                    albumId = dir.name,
                    albumTitle = info?.title ?: dir.name,
                    chapterIndex = chapterIdx,
                    chapterTitle = pdf.nameWithoutExtension,
                    pdfPath = pdf.absolutePath,
                    downloadTime = pdf.lastModified(),
                    fileSize = pdf.length()
                )
            }
            ComicGroup(
                albumId = dir.name,
                albumTitle = info?.title ?: dir.name,
                chapters = chapters
            )
        }.sortedByDescending { it.chapters.maxOfOrNull { r -> r.downloadTime } ?: 0L }
    }
}
