package com.jmcomic.pdfapp.ui.screen

import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jmcomic.pdfapp.ui.theme.AccentCyan
import com.jmcomic.pdfapp.ui.theme.AccentPink
import com.jmcomic.pdfapp.ui.theme.AccentPinkDim
import com.jmcomic.pdfapp.ui.theme.ErrorRed
import com.jmcomic.pdfapp.ui.theme.SuccessGreen
import com.jmcomic.pdfapp.ui.theme.SurfaceContainer
import com.jmcomic.pdfapp.ui.theme.SurfaceDark
import com.jmcomic.pdfapp.ui.theme.TextPrimary
import com.jmcomic.pdfapp.ui.theme.TextSecondary
import com.jmcomic.pdfapp.viewmodel.DownloadStatus
import com.jmcomic.pdfapp.viewmodel.MainViewModel

@Composable
fun MainScreen(viewModel: MainViewModel, onOpenPdf: (String) -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(SurfaceDark, Color(0xFF16162A), SurfaceDark)))
            .verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Text("JMComic PDF", style = MaterialTheme.typography.headlineLarge,
            color = TextPrimary, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("输入车号 / 下载PDF", style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary)
        Spacer(modifier = Modifier.height(48.dp))

        // --- Input ---
        OutlinedTextField(
            value = uiState.albumId, onValueChange = viewModel::onAlbumIdChanged,
            label = { Text("漫画 ID / 车号") }, placeholder = { Text("例如: 350234") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                cursorColor = AccentPink, focusedBorderColor = AccentPink,
                unfocusedBorderColor = TextSecondary.copy(alpha = 0.4f),
                focusedLabelColor = AccentPink, unfocusedLabelColor = TextSecondary
            ), shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // --- Download Button ---
        Button(
            onClick = viewModel::startDownload,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues(0.dp)
        ) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(listOf(AccentPink, AccentPinkDim)),
                    RoundedCornerShape(16.dp)
                ),
                contentAlignment = Alignment.Center
            ) {
                Text("下载 PDF", style = MaterialTheme.typography.titleLarge,
                    color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Status ---
        when (val status = uiState.status) {
            is DownloadStatus.Idle -> {}
            is DownloadStatus.Downloading -> {
                // Progress bar
                if (uiState.progressFraction != null) {
                    LinearProgressIndicator(
                        progress = { uiState.progressFraction!! },
                        modifier = Modifier.fillMaxWidth().height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = AccentPink,
                        trackColor = SurfaceContainer,
                    )
                } else {
                    CircularProgressIndicator(
                        Modifier.size(36.dp), color = AccentPink, strokeWidth = 3.dp
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(uiState.progressMessage.ifBlank { "下载中..." },
                    color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            is DownloadStatus.Success -> {
                Box(Modifier.size(64.dp).clip(CircleShape)
                    .background(SuccessGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center) {
                    Text("✔", fontSize = 28.sp, color = SuccessGreen)
                }
                Spacer(Modifier.height(8.dp))
                Text("PDF 生成成功!", color = TextPrimary,
                    style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(uiState.pdfPath?.substringAfterLast("/") ?: "",
                    color = TextSecondary)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { val p = uiState.pdfPath; if (p != null) onOpenPdf(p) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                ) {
                    Text("打开 PDF", color = Color.White,
                        style = MaterialTheme.typography.titleLarge)
                }
            }
            is DownloadStatus.Error -> {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(ErrorRed.copy(alpha = 0.15f)).padding(12.dp)) {
                    Text(status.message, color = ErrorRed,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace)
                }
            }
        }

        // --- 上次查看 ---
        uiState.lastPdf?.let { pdf ->
            if (uiState.status !is DownloadStatus.Success) {
                Spacer(modifier = Modifier.height(32.dp))
                HorizontalDivider(
                    color = TextSecondary.copy(alpha = 0.2f),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("上次查看", color = TextSecondary,
                    style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceContainer)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = pdf.name,
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = { onOpenPdf(pdf.path) },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("查看", color = Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = viewModel::deleteLastPdf,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = ErrorRed)
                    ) {
                        Text("删除")
                    }
                }
            }
        }
    }
}
