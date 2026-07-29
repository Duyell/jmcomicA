package com.jmcomic.pdfapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jmcomic.pdfapp.data.DownloadHistoryManager
import com.jmcomic.pdfapp.model.DownloadRecord
import com.jmcomic.pdfapp.model.SettingsUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val historyManager = DownloadHistoryManager(application.filesDir)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    /** Whether the search box is currently visible */
    private val _isSearchVisible = MutableStateFlow(false)
    val isSearchVisible: StateFlow<Boolean> = _isSearchVisible.asStateFlow()

    init {
        refreshHistory()
    }

    /** Reload records from disk and apply current search filter. */
    fun refreshHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            val query = _uiState.value.searchQuery
            val records = if (query.isBlank()) {
                historyManager.loadAll()
            } else {
                historyManager.search(query)
            }
            _uiState.value = _uiState.value.copy(
                records = records,
                filteredRecords = records,
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        viewModelScope.launch(Dispatchers.IO) {
            val filtered = if (query.isBlank()) {
                historyManager.loadAll()
            } else {
                historyManager.search(query)
            }
            _uiState.value = _uiState.value.copy(filteredRecords = filtered)
        }
    }

    fun toggleSearch() {
        val newState = !_isSearchVisible.value
        _isSearchVisible.value = newState
        if (!newState) {
            // Clear search when hiding
            onSearchQueryChanged("")
        }
    }

    fun deleteRecord(pdfPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            historyManager.remove(pdfPath)
            refreshHistory()
        }
    }

    /** Format file size for display. */
    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes} B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        }
    }

    /** Format download time for display. */
    fun formatTime(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}
