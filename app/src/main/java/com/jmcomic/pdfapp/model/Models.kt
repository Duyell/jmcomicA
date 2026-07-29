package com.jmcomic.pdfapp.model

/**
 * Download status sealed class — shared across screens.
 */
sealed class DownloadStatus {
    data object Idle : DownloadStatus()
    data object FetchingInfo : DownloadStatus()        // 获取漫画信息中
    data object Downloading : DownloadStatus()          // 下载中
    data object Success : DownloadStatus()              // 下载成功
    data class Error(val message: String) : DownloadStatus()
}

/**
 * A single chapter/photo within an album.
 */
data class ChapterInfo(
    val index: Int,
    val title: String,
    val downloaded: Boolean = false   // true if this chapter already has a PDF
)

/**
 * Group of downloaded chapters belonging to one comic.
 */
data class ComicGroup(
    val albumId: String,
    val albumTitle: String,
    val chapters: List<DownloadRecord>,
    val expanded: Boolean = false
)

/**
 * Metadata stored alongside PDFs in comic directory.
 */
data class ComicInfo(
    val albumId: String,
    val title: String
)

/**
 * Result of downloading one chapter.
 */
data class ChapterDownloadResult(
    val chapterIndex: Int,
    val chapterTitle: String,
    val pdfPath: String? = null,      // null if failed
    val error: String? = null
)

/**
 * Persisted download record (saved to download_history.json).
 */
data class DownloadRecord(
    val albumId: String,
    val albumTitle: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val pdfPath: String,
    val downloadTime: Long,            // System.currentTimeMillis()
    val fileSize: Long                 // bytes
)

/**
 * Lightweight PDF info for display.
 */
data class PdfInfo(
    val name: String,                  // file name
    val path: String                   // absolute path
)

/**
 * Home tab UI state.
 */
data class HomeUiState(
    val albumId: String = "",
    val status: DownloadStatus = DownloadStatus.Idle,
    // Album info (fetched before download)
    val albumTitle: String = "",
    val chapters: List<ChapterInfo> = emptyList(),
    // Chapter selection dialog
    val showChapterDialog: Boolean = false,
    val selectedChapters: Set<Int> = emptySet(),
    // Per-chapter download progress
    val currentChapterIndex: Int = -1,
    val totalSelectedCount: Int = 0,
    val chapterResults: List<ChapterDownloadResult> = emptyList(),
    // Legacy single-chapter / success state
    val pdfPath: String? = null,
    val errorMessage: String? = null,
    val progressMessage: String = "",
    val progressFraction: Float? = null   // null = indeterminate
)

/**
 * Settings tab UI state.
 */
data class SettingsUiState(
    val records: List<DownloadRecord> = emptyList(),
    val searchQuery: String = "",
    val filteredRecords: List<DownloadRecord> = emptyList(),
    // v1.3: comic-grouped view — primary display mode
    val groups: List<ComicGroup> = emptyList(),
    val filteredGroups: List<ComicGroup> = emptyList()
)
