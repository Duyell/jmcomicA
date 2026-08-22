package com.jmcomic.pdfapp.ui.screen

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jmcomic.pdfapp.model.ChapterInfo
import com.jmcomic.pdfapp.ui.components.CoverImage
import com.jmcomic.pdfapp.ui.components.GradientButton
import com.jmcomic.pdfapp.ui.theme.AppTheme

@Composable
fun ChapterSelectDialog(
    albumId: String,
    albumTitle: String,
    chapters: List<ChapterInfo>,
    selectedChapters: Set<Int>,
    onToggle: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val success = AppTheme.colors.success

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
                .background(scheme.background)
                .padding(top = 32.dp)
        ) {
            // ── Top bar ──
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "关闭",
                        tint = scheme.onBackground,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                // 封面缩略图
                CoverImage(
                    albumId = albumId,
                    title = albumTitle,
                    modifier = Modifier.size(width = 40.dp, height = 54.dp),
                    shape = RoundedCornerShape(8.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "选择章节",
                        style = MaterialTheme.typography.titleLarge,
                        color = scheme.onBackground,
                        fontWeight = FontWeight.Bold
                    )
                    if (albumTitle.isNotBlank()) {
                        Text(
                            albumTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // ── Select all / deselect all ──
            Row(
                modifier = Modifier.fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onSelectAll,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = scheme.primary.copy(alpha = 0.12f)),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Rounded.SelectAll,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("全选", color = scheme.primary, style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = onDeselectAll,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = scheme.onSurfaceVariant.copy(alpha = 0.1f)),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Rounded.Deselect,
                        contentDescription = null,
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("取消全选", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                }
            }

            val downloadedCount = chapters.count { it.downloaded }
            Text(
                "已选 ${selectedChapters.size}/${chapters.size} 章" +
                    if (downloadedCount > 0) "  ·  已下载 $downloadedCount 章" else "",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            // ── Chapter list ──
            LazyColumn(
                modifier = Modifier.weight(1f)
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                itemsIndexed(chapters) { _, chapter ->
                    val isSelected = chapter.index in selectedChapters
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .padding(vertical = 2.dp, horizontal = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) scheme.primary.copy(alpha = 0.08f)
                                else Color.Transparent
                            )
                            .clickable { onToggle(chapter.index) }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (isSelected) Icons.Rounded.CheckBox
                            else Icons.Rounded.CheckBoxOutlineBlank,
                            contentDescription = null,
                            tint = if (isSelected) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = chapter.title,
                            color = if (isSelected) scheme.onBackground else scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (chapter.downloaded) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "已下载",
                                color = success,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(success.copy(alpha = 0.12f))
                                    .border(1.dp, success.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // ── Bottom confirm button ──
            Box(
                modifier = Modifier.fillMaxWidth()
                    .background(scheme.background)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                GradientButton(
                    text = "下载已选章节 (${selectedChapters.size})",
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedChapters.isNotEmpty(),
                    height = 52.dp,
                )
            }
        }
    }
}
