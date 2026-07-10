package com.jmcomic.pdfapp.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
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
        Text("JMComic PDF", style = MaterialTheme.typography.headlineLarge, color = TextPrimary, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("输入车号 / 下载PDF", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Spacer(modifier = Modifier.height(48.dp))

        OutlinedTextField(
            value = uiState.albumId, onValueChange = viewModel::onAlbumIdChanged,
            label = { Text("漫画 ID / 车号") }, placeholder = { Text("例如: 123456") },
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

        Button(
            onClick = viewModel::startDownload, modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues(0.dp)
        ) {
            Box(
                Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(AccentPink, AccentPinkDim)), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) { Text("下载 PDF", style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.SemiBold) }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (val status = uiState.status) {
            is DownloadStatus.Idle -> {}
            is DownloadStatus.Downloading -> {
                CircularProgressIndicator(Modifier.size(48.dp), color = AccentPink, strokeWidth = 4.dp)
                Spacer(Modifier.height(8.dp))
                Text("正在下载中...", color = TextSecondary)
            }
            is DownloadStatus.Success -> {
                Box(Modifier.size(64.dp).clip(CircleShape).background(SuccessGreen.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Text("\u2714", fontSize = 28.sp, color = SuccessGreen)
                }
                Spacer(Modifier.height(8.dp))
                Text("PDF 生成成功!", color = TextPrimary, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(uiState.pdfPath?.substringAfterLast("/") ?: "", color = TextSecondary)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { val p = uiState.pdfPath; if (p != null) onOpenPdf(p) },
                    modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen)
                ) { Text("打开 PDF", color = Color.White, style = MaterialTheme.typography.titleLarge) }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = viewModel::reset, modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = SurfaceContainer)
                ) { Text("下载另一本", color = TextSecondary) }
            }
            is DownloadStatus.Error -> {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(ErrorRed.copy(alpha = 0.15f)).padding(12.dp)) {
                    Text(status.message, color = ErrorRed, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
                }
            }
        }

        if (uiState.debugStep.isNotBlank()) {
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(SurfaceContainer).padding(12.dp)) {
                Text(uiState.debugStep, color = AccentCyan, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
            }
        }
    }
}