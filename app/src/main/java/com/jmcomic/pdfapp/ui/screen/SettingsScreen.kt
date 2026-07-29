package com.jmcomic.pdfapp.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jmcomic.pdfapp.model.ComicGroup
import com.jmcomic.pdfapp.model.DownloadRecord
import com.jmcomic.pdfapp.ui.theme.AccentBlue
import com.jmcomic.pdfapp.ui.theme.ErrorRed
import com.jmcomic.pdfapp.ui.theme.SuccessGreen
import com.jmcomic.pdfapp.ui.theme.SurfaceContainer
import com.jmcomic.pdfapp.ui.theme.SurfaceDark
import com.jmcomic.pdfapp.ui.theme.TextPrimary
import com.jmcomic.pdfapp.ui.theme.TextSecondary
import com.jmcomic.pdfapp.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenPdf: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isSearchVisible by viewModel.isSearchVisible.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize()
            .background(SurfaceDark)
    ) {
        // ── Top bar ──
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 56.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "下载管理",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { viewModel.toggleSearch() }) {
                Icon(
                    if (isSearchVisible) Icons.Rounded.SearchOff
                    else Icons.Rounded.Search,
                    contentDescription = "搜索",
                    tint = if (isSearchVisible) AccentBlue else TextSecondary
                )
            }
        }

        // ── Search bar ──
        AnimatedVisibility(
            visible = isSearchVisible,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            val focusManager = LocalFocusManager.current
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                placeholder = { Text("搜索漫画ID或标题...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null,
                        tint = TextSecondary, modifier = Modifier.size(20.dp))
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = AccentBlue,
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                    focusedContainerColor = SurfaceContainer.copy(alpha = 0.3f),
                    unfocusedContainerColor = SurfaceContainer.copy(alpha = 0.3f),
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }

        // ── Count ──
        val totalChapters = uiState.filteredGroups.sumOf { it.chapters.size }
        Text(
            if (uiState.searchQuery.isNotBlank())
                "${uiState.filteredGroups.size} 部漫画 · $totalChapters 章"
            else "${uiState.filteredGroups.size} 部漫画 · $totalChapters 章",
            color = TextSecondary.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 4.dp)
        )

        // ── List ──
        if (uiState.filteredGroups.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Settings, contentDescription = null,
                        tint = TextSecondary.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (uiState.searchQuery.isNotBlank()) "未找到匹配的下载记录"
                        else "暂无下载记录",
                        color = TextSecondary.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
            ) {
                itemsIndexed(
                    items = uiState.filteredGroups,
                    key = { _, group -> group.albumId }
                ) { _, group ->
                    ComicGroupCard(
                        group = group,
                        formatSize = viewModel::formatSize,
                        formatTime = viewModel::formatTime,
                        onToggle = { viewModel.toggleGroup(group.albumId) },
                        onOpenPdf = onOpenPdf,
                        onDeleteChapter = viewModel::deleteChapter,
                        onDeleteComic = { viewModel.deleteComic(group.albumId) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun ComicGroupCard(
    group: ComicGroup,
    formatSize: (Long) -> String,
    formatTime: (Long) -> String,
    onToggle: () -> Unit,
    onOpenPdf: (String) -> Unit,
    onDeleteChapter: (String) -> Unit,
    onDeleteComic: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceContainer)
    ) {
        // ── Comic header (always visible) ──
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                    .background(AccentBlue.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Description, contentDescription = null,
                    tint = AccentBlue, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.albumTitle.ifBlank { "JM${group.albumId}" },
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "JM${group.albumId} · ${group.chapters.size} 章",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = onDeleteComic) {
                Icon(Icons.Rounded.DeleteForever, contentDescription = "删除全部",
                    tint = ErrorRed.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
            }
            Icon(
                if (group.expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        // ── Chapter list (expandable) ──
        AnimatedVisibility(
            visible = group.expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                for (record in group.chapters) {
                    ChapterRow(
                        record = record,
                        formatSize = formatSize,
                        formatTime = formatTime,
                        onOpen = { onOpenPdf(record.pdfPath) },
                        onDelete = { onDeleteChapter(record.pdfPath) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterRow(
    record: DownloadRecord,
    formatSize: (Long) -> String,
    formatTime: (Long) -> String,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AccentBlue.copy(alpha = 0.03f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.chapterTitle,
                color = TextPrimary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(formatTime(record.downloadTime),
                    color = TextSecondary.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelSmall)
                Text(formatSize(record.fileSize),
                    color = TextSecondary.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelSmall)
            }
        }
        Button(
            onClick = onOpen,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null,
                tint = Color.White, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text("查看", color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = "删除",
                tint = ErrorRed.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
        }
    }
}
