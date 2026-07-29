package com.jmcomic.pdfapp.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jmcomic.pdfapp.model.ChapterDownloadResult
import com.jmcomic.pdfapp.model.DownloadStatus
import com.jmcomic.pdfapp.ui.theme.AccentBlue
import com.jmcomic.pdfapp.ui.theme.AccentBlueDim
import com.jmcomic.pdfapp.ui.theme.ErrorRed
import com.jmcomic.pdfapp.ui.theme.SuccessGreen
import com.jmcomic.pdfapp.ui.theme.SurfaceContainer
import com.jmcomic.pdfapp.ui.theme.SurfaceDark
import com.jmcomic.pdfapp.ui.theme.TextPrimary
import com.jmcomic.pdfapp.ui.theme.TextSecondary
import com.jmcomic.pdfapp.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenPdf: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Chapter selection dialog
    if (uiState.showChapterDialog) {
        ChapterSelectDialog(
            albumTitle = uiState.albumTitle,
            chapters = uiState.chapters,
            selectedChapters = uiState.selectedChapters,
            onToggle = viewModel::onToggleChapter,
            onSelectAll = viewModel::onSelectAll,
            onDeselectAll = viewModel::onDeselectAll,
            onConfirm = viewModel::onConfirmChapterSelection,
            onDismiss = viewModel::onChapterDialogDismiss,
        )
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .background(SurfaceDark)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(56.dp))

        // ── Header ──
        Text(
            "PDF下载器",
            style = MaterialTheme.typography.headlineLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        Spacer(Modifier.height(32.dp))

        // ── Input Card ──
        Column(
            modifier = Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(SurfaceContainer.copy(alpha = 0.5f))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = uiState.albumId,
                onValueChange = viewModel::onAlbumIdChanged,
                label = { Text("漫画ID") },
                placeholder = { Text("例如: 350234") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = AccentBlue,
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                    focusedLabelColor = AccentBlue,
                    unfocusedLabelColor = TextSecondary,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(Modifier.height(20.dp))

            val isBusy = uiState.status is DownloadStatus.Downloading
                    || uiState.status is DownloadStatus.FetchingInfo

            Button(
                onClick = viewModel::onDownloadTapped,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp),
                enabled = !isBusy
            ) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(listOf(AccentBlue, AccentBlueDim)),
                        RoundedCornerShape(14.dp)
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (isBusy) "处理中..." else "下载 PDF",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ── Status ──
        AnimatedVisibility(
            visible = uiState.status !is DownloadStatus.Idle,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                when (val status = uiState.status) {
                    is DownloadStatus.Idle -> {}

                    is DownloadStatus.FetchingInfo -> FetchingSection(uiState.progressMessage)

                    is DownloadStatus.Downloading -> DownloadingSection(
                        uiState.progressMessage,
                        uiState.progressFraction,
                    )

                    is DownloadStatus.Success -> {
                        // Show per-chapter results if available
                        if (uiState.chapterResults.isNotEmpty()) {
                            MultiChapterSuccessSection(
                                results = uiState.chapterResults,
                                onOpenPdf = onOpenPdf,
                                onDismiss = viewModel::dismissSuccess,
                            )
                        } else {
                            SingleSuccessSection(uiState, onOpenPdf)
                        }
                    }

                    is DownloadStatus.Error -> ErrorSection(status.message, viewModel::dismissError)
                }
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

// ── Status sub-sections ──────────────────────────────────────

@Composable
private fun FetchingSection(message: String) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceContainer.copy(alpha = 0.5f))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = AccentBlue,
            trackColor = SurfaceContainer,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            message.ifBlank { "获取漫画信息..." },
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun DownloadingSection(message: String, fraction: Float?) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceContainer.copy(alpha = 0.5f))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (fraction != null) {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = AccentBlue,
                trackColor = SurfaceContainer,
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = AccentBlue,
                trackColor = SurfaceContainer,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            message.ifBlank { "正在下载..." },
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        if (fraction != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                "${(fraction * 100).toInt()}%",
                color = AccentBlue,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SingleSuccessSection(
    uiState: com.jmcomic.pdfapp.model.HomeUiState,
    onOpenPdf: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SuccessGreen.copy(alpha = 0.08f))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape)
                .background(SuccessGreen.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.PictureAsPdf,
                contentDescription = null,
                tint = SuccessGreen,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "PDF 生成成功",
            color = TextPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            uiState.pdfPath?.substringAfterLast("/") ?: "",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { uiState.pdfPath?.let { onOpenPdf(it) } },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("打开 PDF", color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun MultiChapterSuccessSection(
    results: List<ChapterDownloadResult>,
    onOpenPdf: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SuccessGreen.copy(alpha = 0.08f))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape)
                .background(SuccessGreen.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = SuccessGreen,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "下载完成 (${results.count { it.pdfPath != null }}/${results.size})",
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))

        // Per-chapter result cards
        for (r in results) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceContainer.copy(alpha = 0.6f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (r.pdfPath != null) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = SuccessGreen,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(
                        Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = r.chapterTitle,
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (r.pdfPath != null) {
                    Button(
                        onClick = { onOpenPdf(r.pdfPath) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("查看", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    Text(
                        r.error ?: "失败",
                        color = ErrorRed,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(8.dp))

        // Dismiss button
        Button(
            onClick = onDismiss,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TextSecondary.copy(alpha = 0.15f)),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Text("完成", color = TextPrimary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ErrorSection(message: String, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ErrorRed.copy(alpha = 0.08f))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "下载失败",
            color = ErrorRed,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            color = TextPrimary.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Start,
            lineHeight = 22.sp
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onDismiss,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ErrorRed.copy(alpha = 0.12f)),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)
        ) {
            Text("关闭", color = ErrorRed, style = MaterialTheme.typography.labelLarge)
        }
    }
}
