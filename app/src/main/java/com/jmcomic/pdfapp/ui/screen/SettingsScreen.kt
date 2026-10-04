package com.jmcomic.pdfapp.ui.screen

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jmcomic.pdfapp.viewmodel.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 存储统计结果。 */
private data class StorageStats(
    val comicCount: Int,
    val chapterCount: Int,
    val pdfBytes: Long,
    val coverBytes: Long,
)

/** 遍历 pdf_output 与 covers 目录统计占用（排除下载临时目录 downloads）。 */
private fun computeStorageStats(filesDir: File): StorageStats {
    var comics = 0
    var chapters = 0
    var pdfBytes = 0L
    val pdfDir = File(filesDir, "pdf_output")
    if (pdfDir.isDirectory) {
        pdfDir.listFiles()?.filter { it.isFile && it.extension == "pdf" }?.forEach {
            chapters++
            pdfBytes += it.length()
        }
        pdfDir.listFiles()?.filter { it.isDirectory && it.name != "downloads" }?.forEach { comicDir ->
            comics++
            comicDir.listFiles()?.filter { it.isFile && it.extension == "pdf" }?.forEach {
                chapters++
                pdfBytes += it.length()
            }
        }
    }
    var coverBytes = 0L
    val coversDir = File(filesDir, "covers")
    if (coversDir.isDirectory) {
        coverBytes = coversDir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
    }
    return StorageStats(comics, chapters, pdfBytes, coverBytes)
}

/**
 * 设置 tab：背景图片（选图→裁剪、透明度、恢复默认）+ 存储统计与缓存清理 + 版本号。
 * 背景状态由 MainActivity 持有并全局应用，本页通过回调修改。
 */
@Composable
fun SettingsScreen(
    backgroundPath: String?,
    backgroundOpacity: Float,
    onBackgroundChanged: (path: String?, opacity: Float) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pendingCropUri by remember { mutableStateOf<Uri?>(null) }
    var storageStats by remember { mutableStateOf<StorageStats?>(null) }
    var clearingCache by remember { mutableStateOf(false) }

    // 每次进入设置页重新统计（Crossfade 切 tab 会重建本页）
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            storageStats = computeStorageStats(context.filesDir)
        }
    }

    // 清除封面缓存后重新统计
    fun clearCoverCache() {
        if (clearingCache) return
        clearingCache = true
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    File(context.filesDir, "covers").listFiles()?.forEach { it.delete() }
                }
                storageStats = computeStorageStats(context.filesDir)
            }
            clearingCache = false
        }
    }

    // 选图（系统 Photo Picker，低版本自动降级文档选择器，无需权限）
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) pendingCropUri = uri
    }

    // 恢复默认背景：删文件 + 置空路径（透明度保留）
    fun resetBackground() {
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching {
                    File(context.filesDir, "backgrounds").listFiles()?.forEach { it.delete() }
                }
            }
            onBackgroundChanged(null, backgroundOpacity)
        }
    }

    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrDefault("")
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Top bar ──
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(top = 56.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "设置",
                style = MaterialTheme.typography.headlineMedium,
                color = scheme.onBackground,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

        // ── 背景图片卡片 ──
        val cardShape = RoundedCornerShape(20.dp)
        Column(
            modifier = Modifier.fillMaxWidth()
                .shadow(
                    elevation = 5.dp,
                    shape = cardShape,
                    spotColor = Color.Black.copy(alpha = 0.07f),
                    ambientColor = Color.Black.copy(alpha = 0.07f),
                )
                .clip(cardShape)
                .background(scheme.surface.copy(alpha = 0.95f))
                .border(1.dp, scheme.outlineVariant.copy(alpha = 0.55f), cardShape)
                .padding(vertical = 8.dp)
        ) {
            // 选图行
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable {
                        pickImage.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Wallpaper,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "背景图片",
                        color = scheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (backgroundPath != null) "已设置" else "未设置",
                        color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }

            // 分隔线
            Spacer(
                Modifier.fillMaxWidth().height(1.dp)
                    .background(scheme.outlineVariant.copy(alpha = 0.4f))
            )

            // 透明度行
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Opacity,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        "背景透明度",
                        color = scheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${(backgroundOpacity * 100).toInt()}%",
                        color = scheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Slider(
                    value = backgroundOpacity,
                    onValueChange = { onBackgroundChanged(backgroundPath, it) },
                    valueRange = 0.15f..1f,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )
            }

            // 恢复默认行
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center
            ) {
                TextButton(
                    onClick = ::resetBackground,
                    enabled = backgroundPath != null
                ) {
                    Icon(
                        Icons.Rounded.Restore,
                        contentDescription = null,
                        tint = if (backgroundPath != null) scheme.onSurfaceVariant
                        else scheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "恢复默认背景",
                        color = if (backgroundPath != null) scheme.onSurfaceVariant
                        else scheme.onSurfaceVariant.copy(alpha = 0.3f),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── 存储卡片 ──
        val stats = storageStats
        Column(
            modifier = Modifier.fillMaxWidth()
                .shadow(
                    elevation = 5.dp,
                    shape = cardShape,
                    spotColor = Color.Black.copy(alpha = 0.07f),
                    ambientColor = Color.Black.copy(alpha = 0.07f),
                )
                .clip(cardShape)
                .background(scheme.surface.copy(alpha = 0.95f))
                .border(1.dp, scheme.outlineVariant.copy(alpha = 0.55f), cardShape)
                .padding(vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Storage,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "存储占用",
                        color = scheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stats?.let {
                            "${it.comicCount} 部漫画 · ${it.chapterCount} 章 · ${formatBytes(it.pdfBytes)}"
                        } ?: "统计中...",
                        color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Spacer(
                Modifier.fillMaxWidth().height(1.dp)
                    .background(scheme.outlineVariant.copy(alpha = 0.4f))
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "封面缓存 ${stats?.let { formatBytes(it.coverBytes) } ?: "-"}",
                    color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = ::clearCoverCache,
                    enabled = (stats?.coverBytes ?: 0) > 0 && !clearingCache
                ) {
                    Icon(
                        Icons.Rounded.DeleteSweep,
                        contentDescription = null,
                        tint = if ((stats?.coverBytes ?: 0) > 0) scheme.onSurfaceVariant
                        else scheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (clearingCache) "清除中..." else "清除",
                        color = if ((stats?.coverBytes ?: 0) > 0) scheme.onSurfaceVariant
                        else scheme.onSurfaceVariant.copy(alpha = 0.3f),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "版本 $versionName",
            color = scheme.onSurfaceVariant.copy(alpha = 0.5f),
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(48.dp))
    }

    // ── 裁剪界面 ──
    pendingCropUri?.let { uri ->
        CropScreen(
            imageUri = uri,
            onConfirm = { path ->
                pendingCropUri = null
                onBackgroundChanged(path, backgroundOpacity)
            },
            onDismiss = { pendingCropUri = null },
        )
    }
}
