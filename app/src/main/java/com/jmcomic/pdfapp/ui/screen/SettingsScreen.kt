package com.jmcomic.pdfapp.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.jmcomic.pdfapp.ui.components.CoverImage
import com.jmcomic.pdfapp.ui.theme.AppTheme
import com.jmcomic.pdfapp.ui.theme.ThemeMode
import com.jmcomic.pdfapp.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenPdf: (String) -> Unit = {},
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onCycleTheme: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isSearchVisible by viewModel.isSearchVisible.collectAsStateWithLifecycle()
    val scheme = MaterialTheme.colorScheme

    // 待确认删除的漫画（先弹二次确认框）
    var pendingDelete by remember { mutableStateOf<ComicGroup?>(null) }

    Column(
        modifier = Modifier.fillMaxSize()
            .background(scheme.background)
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
                color = scheme.onBackground,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            // 主题模式切换：跟随系统 → 浅色 → 深色
            IconButton(onClick = onCycleTheme) {
                Icon(
                    when (themeMode) {
                        ThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
                        ThemeMode.LIGHT -> Icons.Rounded.LightMode
                        ThemeMode.DARK -> Icons.Rounded.DarkMode
                    },
                    contentDescription = "切换主题",
                    tint = scheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { viewModel.toggleSearch() }) {
                Icon(
                    if (isSearchVisible) Icons.Rounded.SearchOff
                    else Icons.Rounded.Search,
                    contentDescription = "搜索",
                    tint = if (isSearchVisible) scheme.primary else scheme.onSurfaceVariant
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
                        tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = scheme.onSurface,
                    unfocusedTextColor = scheme.onSurface,
                    cursorColor = scheme.primary,
                    focusedBorderColor = scheme.primary,
                    unfocusedBorderColor = scheme.outlineVariant,
                    focusedContainerColor = scheme.surfaceContainerLow,
                    unfocusedContainerColor = scheme.surfaceContainerLow,
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
            color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
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
                    Icon(Icons.Rounded.CollectionsBookmark, contentDescription = null,
                        tint = scheme.onSurfaceVariant.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (uiState.searchQuery.isNotBlank()) "未找到匹配的下载记录"
                        else "暂无下载记录",
                        color = scheme.onSurfaceVariant.copy(alpha = 0.5f),
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
                        onDeleteComic = { pendingDelete = group },
                    )
                    Spacer(Modifier.height(10.dp))
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        // ── 删除整部漫画二次确认 ──
        pendingDelete?.let { group ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                containerColor = scheme.surface,
                titleContentColor = scheme.onSurface,
                textContentColor = scheme.onSurfaceVariant,
                title = { Text("删除整部漫画？") },
                text = {
                    Text(
                        "将删除《${group.albumTitle.ifBlank { "JM${group.albumId}" }}》" +
                            "的全部 ${group.chapters.size} 章 PDF，且无法恢复。"
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteComic(group.albumId)
                        pendingDelete = null
                    }) {
                        Text("删除", color = scheme.error, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text("取消", color = scheme.onSurfaceVariant)
                    }
                },
            )
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
    val scheme = MaterialTheme.colorScheme
    val cardShape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier.fillMaxWidth()
            .shadow(
                elevation = 5.dp,
                shape = cardShape,
                spotColor = Color.Black.copy(alpha = 0.07f),
                ambientColor = Color.Black.copy(alpha = 0.07f),
            )
            .clip(cardShape)
            .background(scheme.surface)
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.55f), cardShape)
    ) {
        // ── Comic header (always visible) ──
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 封面缩略图（缺失时渐变占位）
            CoverImage(
                albumId = group.albumId,
                title = group.albumTitle,
                modifier = Modifier.size(width = 50.dp, height = 68.dp),
                shape = RoundedCornerShape(10.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.albumTitle.ifBlank { "JM${group.albumId}" },
                    color = scheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "JM${group.albumId}",
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${group.chapters.size} 章",
                        color = scheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(scheme.primary.copy(alpha = 0.10f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            IconButton(onClick = onDeleteComic) {
                Icon(Icons.Rounded.DeleteForever, contentDescription = "删除全部",
                    tint = scheme.error.copy(alpha = 0.55f), modifier = Modifier.size(20.dp))
            }
            Icon(
                if (group.expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
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
    val scheme = MaterialTheme.colorScheme
    val success = AppTheme.colors.success
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.chapterTitle,
                color = scheme.onSurface,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(formatTime(record.downloadTime),
                    color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall)
                Text(formatSize(record.fileSize),
                    color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall)
            }
        }
        Button(
            onClick = onOpen,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = success),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null,
                tint = Color.White, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text("查看", color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = "删除",
                tint = scheme.error.copy(alpha = 0.55f), modifier = Modifier.size(18.dp))
        }
    }
}
