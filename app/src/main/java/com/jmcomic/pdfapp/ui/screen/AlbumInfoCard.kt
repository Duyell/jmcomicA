package com.jmcomic.pdfapp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jmcomic.pdfapp.model.AlbumInfo
import com.jmcomic.pdfapp.model.ChapterInfo
import com.jmcomic.pdfapp.ui.components.CoverImage
import com.jmcomic.pdfapp.ui.components.GradientButton
import com.jmcomic.pdfapp.ui.theme.AppTheme

/**
 * 解析成功的漫画信息卡片（两步流程第二步）：大封面 + 标题 + 作者 + 标签 + 下载按钮。
 */
@Composable
fun AlbumInfoCard(
    album: AlbumInfo,
    chapters: List<ChapterInfo>,
    onDownloadTapped: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val success = AppTheme.colors.success
    val downloadedCount = chapters.count { it.downloaded }
    val singleChapter = chapters.size == 1
    val singleAllDownloaded = singleChapter && downloadedCount == 1

    val cardShape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier.fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = cardShape,
                spotColor = Color.Black.copy(alpha = 0.08f),
                ambientColor = Color.Black.copy(alpha = 0.08f),
            )
            .clip(cardShape)
            .background(scheme.surface.copy(alpha = 0.95f))
            .border(1.dp, scheme.outlineVariant.copy(alpha = 0.6f), cardShape)
            .padding(20.dp),
    ) {
        // ── Cover + meta ──
        Row {
            CoverImage(
                albumId = album.albumId,
                title = album.title,
                modifier = Modifier.size(width = 120.dp, height = 160.dp),
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.title.ifBlank { "JM${album.albumId}" },
                    color = scheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (album.author.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Person,
                            contentDescription = null,
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            album.author,
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    buildString {
                        append("共 ${chapters.size} 章")
                        if (album.pageCount > 0) append(" · ${album.pageCount} 页")
                    },
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
                if (downloadedCount > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "已下载 $downloadedCount 章",
                        color = success,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "JM${album.albumId}",
                    color = scheme.onSurfaceVariant.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        // ── Tags ──
        if (album.tags.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            TagFlowRow(album.tags, scheme)
        }

        Spacer(Modifier.height(18.dp))

        // ── Download CTA ──
        val buttonText = when {
            singleAllDownloaded -> "已下载"
            singleChapter -> "下载 PDF"
            else -> "选择章节下载"
        }
        GradientButton(
            text = buttonText,
            onClick = onDownloadTapped,
            modifier = Modifier.fillMaxWidth(),
            enabled = !singleAllDownloaded,
            leadingIcon = Icons.Rounded.Download,
        )
    }
}

/** 标签胶囊流式布局，最多展示 8 个，超出显示 +N。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagFlowRow(tags: List<String>, scheme: ColorScheme) {
    val maxTags = 8
    val shown = tags.take(maxTags)
    val extra = tags.size - shown.size
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        shown.forEach { tag ->
            Text(
                tag,
                color = scheme.primary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(scheme.primary.copy(alpha = 0.1f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        if (extra > 0) {
            Text(
                "+$extra",
                color = scheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(scheme.onSurfaceVariant.copy(alpha = 0.08f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}
