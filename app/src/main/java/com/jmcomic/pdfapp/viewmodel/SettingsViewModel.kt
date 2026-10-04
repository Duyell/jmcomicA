package com.jmcomic.pdfapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jmcomic.pdfapp.data.DownloadHistoryManager
import com.jmcomic.pdfapp.model.ComicGroup
import com.jmcomic.pdfapp.model.SettingsUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 字节数格式化（B / KB / MB），供设置页与漫画页共用。 */
internal fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val historyManager = DownloadHistoryManager(application.filesDir)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _isSearchVisible = MutableStateFlow(false)
    val isSearchVisible: StateFlow<Boolean> = _isSearchVisible.asStateFlow()

    init { refreshHistory() }

    fun refreshHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val query = _uiState.value.searchQuery
            val groups = if (query.isBlank()) {
                historyManager.loadGroups()
            } else {
                historyManager.search(query)
            }
            _uiState.value = _uiState.value.copy(
                groups = groups,
                filteredGroups = groups,
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        viewModelScope.launch(Dispatchers.IO) {
            val filtered = if (query.isBlank()) {
                historyManager.loadGroups()
            } else {
                historyManager.search(query)
            }
            _uiState.value = _uiState.value.copy(filteredGroups = filtered)
        }
    }

    fun toggleSearch() {
        val newState = !_isSearchVisible.value
        _isSearchVisible.value = newState
        if (!newState) onSearchQueryChanged("")
    }

    fun toggleGroup(albumId: String) {
        val groups = _uiState.value.filteredGroups.map { g ->
            if (g.albumId == albumId) g.copy(expanded = !g.expanded) else g
        }
        _uiState.value = _uiState.value.copy(filteredGroups = groups)
    }

    fun deleteChapter(pdfPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            historyManager.remove(pdfPath)
            refreshHistory()
        }
    }

    fun deleteComic(albumId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            historyManager.removeComic(albumId)
            refreshHistory()
        }
    }

    fun formatSize(bytes: Long): String = formatBytes(bytes)

    fun formatTime(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}
