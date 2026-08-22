package com.jmcomic.pdfapp.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 漫画封面组件：优先读取磁盘缓存 covers/{albumId}.jpg，
 * 缺失时显示渐变首字占位图。
 */
@Composable
fun CoverImage(
    albumId: String,
    title: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
) {
    val context = LocalContext.current
    var bitmap by remember(albumId) { mutableStateOf(CoverCache.get(albumId)) }

    LaunchedEffect(albumId) {
        if (bitmap != null) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            val file = File(context.filesDir, "covers/$albumId.jpg")
            if (!file.exists()) return@withContext
            try {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                val bmp = BitmapFactory.decodeFile(file.absolutePath, opts)
                if (bmp != null) {
                    CoverCache.put(albumId, bmp)
                    bitmap = bmp
                }
            } catch (_: Exception) {
            }
        }
    }

    Box(modifier.clip(shape)) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            CoverPlaceholder(title, Modifier.fillMaxSize())
        }
    }
}

/** 渐变底 + 标题首字符的占位封面。 */
@Composable
fun CoverPlaceholder(title: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier.background(
            Brush.linearGradient(
                listOf(
                    scheme.primary.copy(alpha = 0.85f),
                    scheme.secondary.copy(alpha = 0.65f)
                )
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title.trim().firstOrNull()?.toString() ?: "?",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** 简单的进程内 Bitmap 缓存（LRU，上限 32 张）。 */
private object CoverCache {
    private const val MAX = 32
    private val map = object : LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) =
            size > MAX
    }

    @Synchronized
    fun get(key: String): Bitmap? = map[key]

    @Synchronized
    fun put(key: String, bmp: Bitmap) {
        map[key] = bmp
    }
}
