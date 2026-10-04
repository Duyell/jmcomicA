package com.jmcomic.pdfapp.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 全局背景图层：按路径解码位图（inSampleSize 限制长边 ≤2048），
 * 以用户设定的透明度绘制。path 变化时自动重载。
 */
@Composable
fun BackgroundLayer(
    path: String,
    opacity: Float,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(path) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(path) {
        withContext(Dispatchers.IO) {
            if (!File(path).exists()) return@withContext
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                val maxSide = maxOf(bounds.outWidth, bounds.outHeight)
                var sample = 1
                while (maxSide / sample > 2048) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                bitmap = BitmapFactory.decodeFile(path, opts)
            } catch (_: Exception) {
            }
        }
    }

    bitmap?.let { bmp ->
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize().graphicsLayer { alpha = opacity },
        )
    }
}
