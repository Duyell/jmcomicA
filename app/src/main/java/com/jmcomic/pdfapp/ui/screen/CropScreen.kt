package com.jmcomic.pdfapp.ui.screen

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jmcomic.pdfapp.ui.components.GradientButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** 裁剪框（px，屏幕坐标系）。 */
private data class CropFrame(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/** 裁剪框：屏幕宽高比、留 margin、垂直居中。 */
private fun frameOf(w: Int, h: Int, margin: Float): CropFrame? {
    if (w <= 0 || h <= 0) return null
    val fw = w - 2 * margin
    val fh = fw * h / w
    val left = margin
    val top = (h - fh) / 2f
    return CropFrame(left, top, left + fw, top + fh)
}

/**
 * 背景图片裁剪界面：全屏黑底，缩放/平移图片，固定屏幕宽高比裁剪框。
 * 确认后裁出位图存至 filesDir/backgrounds/，回传保存路径。
 */
@Composable
fun CropScreen(
    imageUri: Uri,
    onConfirm: (savedPath: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val marginPx = with(LocalDensity.current) { 12.dp.toPx() }
    val strokePx = with(LocalDensity.current) { 1.5.dp.toPx() }
    val cornerLenPx = with(LocalDensity.current) { 20.dp.toPx() }
    val cornerWPx = with(LocalDensity.current) { 3.dp.toPx() }

    var source by remember { mutableStateOf<Bitmap?>(null) }
    var loadError by remember { mutableStateOf(false) }
    var userScale by remember { mutableStateOf(1f) }        // 1f = 初始铺满裁剪框
    var offset by remember { mutableStateOf(Offset.Zero) }  // 相对屏幕中心，px
    var saving by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    // ── 加载：降采样解码 + EXIF 旋正 ──
    LaunchedEffect(imageUri) {
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(imageUri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                val maxSide = max(bounds.outWidth, bounds.outHeight)
                var sample = 1
                while (maxSide / sample > 2048) sample *= 2
                val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                var bmp = resolver.openInputStream(imageUri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }

                val orientation = resolver.openInputStream(imageUri)?.use {
                    runCatching {
                        ExifInterface(it).getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL
                        )
                    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
                } ?: ExifInterface.ORIENTATION_NORMAL
                val degrees = when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
                if (degrees != 0f && bmp != null) {
                    val matrix = Matrix().apply { postRotate(degrees) }
                    val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
                    bmp.recycle()
                    bmp = rotated
                }

                if (bmp == null) {
                    loadError = true
                } else {
                    source = bmp
                    userScale = 1f
                    offset = Offset.Zero
                }
            } catch (e: Exception) {
                loadError = true
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { source?.recycle() }
    }

    // 位图包装在 remember 中，避免手势每帧重新分配 ImageBitmap
    val srcImage = remember(source) { source?.asImageBitmap() }

    // ── 确认裁剪并保存 ──
    fun confirmCrop() {
        val src = source ?: return
        if (saving) return
        val cs = canvasSize
        val frame = frameOf(cs.width, cs.height, marginPx) ?: return
        saving = true
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val baseScale = max(frame.width / src.width, frame.height / src.height)
                    val totalScale = baseScale * userScale
                    val dw = src.width * totalScale
                    val dh = src.height * totalScale
                    val imgLeft = (cs.width - dw) / 2f + offset.x
                    val imgTop = (cs.height - dh) / 2f + offset.y
                    val srcLeft = ((frame.left - imgLeft) / totalScale).roundToInt()
                        .coerceIn(0, src.width - 1)
                    val srcTop = ((frame.top - imgTop) / totalScale).roundToInt()
                        .coerceIn(0, src.height - 1)
                    val srcRight = ((frame.right - imgLeft) / totalScale).roundToInt()
                        .coerceIn(srcLeft + 1, src.width)
                    val srcBottom = ((frame.bottom - imgTop) / totalScale).roundToInt()
                        .coerceIn(srcTop + 1, src.height)

                    var cropped = Bitmap.createBitmap(
                        src, srcLeft, srcTop, srcRight - srcLeft, srcBottom - srcTop
                    )
                    // 长边 >4096 减半，限制文件大小
                    while (max(cropped.width, cropped.height) > 4096) {
                        val half = Bitmap.createScaledBitmap(
                            cropped, cropped.width / 2, cropped.height / 2, true
                        )
                        cropped.recycle()
                        cropped = half
                    }
                    val dir = File(context.filesDir, "backgrounds").apply { mkdirs() }
                    val out = File(dir, "background_${System.currentTimeMillis()}.jpg")
                    out.outputStream().use {
                        cropped.compress(Bitmap.CompressFormat.JPEG, 92, it)
                    }
                    cropped.recycle()
                    // 删除旧背景文件，保证 path 变化触发 BackgroundLayer 重载
                    dir.listFiles()?.filter {
                        it.name.startsWith("background_") && it != out
                    }?.forEach { it.delete() }
                    withContext(Dispatchers.Main) { onConfirm(out.absolutePath) }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { saving = false }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black)
        ) {
            // ── 画布：位图 + 遮罩 + 边框 + 手势 ──
            Canvas(
                modifier = Modifier.fillMaxSize()
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val src = source ?: return@detectTransformGestures
                            val cs = canvasSize
                            val frame = frameOf(cs.width, cs.height, marginPx)
                                ?: return@detectTransformGestures
                            userScale = (userScale * zoom).coerceIn(1f, 4f)
                            val totalScale =
                                max(frame.width / src.width, frame.height / src.height) * userScale
                            val maxDx = ((src.width * totalScale - frame.width) / 2f)
                                .coerceAtLeast(0f)
                            val maxDy = ((src.height * totalScale - frame.height) / 2f)
                                .coerceAtLeast(0f)
                            offset = Offset(
                                (offset.x + pan.x).coerceIn(-maxDx, maxDx),
                                (offset.y + pan.y).coerceIn(-maxDy, maxDy),
                            )
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            userScale = 1f
                            offset = Offset.Zero
                        })
                    }
            ) {
                val src = source ?: return@Canvas
                val img = srcImage ?: return@Canvas
                val frame = frameOf(size.width.toInt(), size.height.toInt(), marginPx)
                    ?: return@Canvas
                val totalScale = max(frame.width / src.width, frame.height / src.height) * userScale
                val dw = src.width * totalScale
                val dh = src.height * totalScale
                val imgLeft = (size.width - dw) / 2f + offset.x
                val imgTop = (size.height - dh) / 2f + offset.y

                // 位图
                drawImage(
                    image = img,
                    dstOffset = IntOffset(imgLeft.roundToInt(), imgTop.roundToInt()),
                    dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
                    filterQuality = FilterQuality.Medium,
                )

                // 框外遮罩（上下左右四块）
                val maskColor = Color.Black.copy(alpha = 0.55f)
                drawRect(maskColor, topLeft = Offset.Zero,
                    size = Size(size.width, frame.top.coerceAtLeast(0f)))
                drawRect(maskColor, topLeft = Offset(0f, frame.bottom),
                    size = Size(size.width, (size.height - frame.bottom).coerceAtLeast(0f)))
                drawRect(maskColor, topLeft = Offset(0f, frame.top),
                    size = Size(frame.left.coerceAtLeast(0f), frame.height))
                drawRect(maskColor, topLeft = Offset(frame.right, frame.top),
                    size = Size((size.width - frame.right).coerceAtLeast(0f), frame.height))

                // 边框 + 四角 L 形装饰
                drawRect(
                    color = Color.White.copy(alpha = 0.6f),
                    topLeft = Offset(frame.left, frame.top),
                    size = Size(frame.width, frame.height),
                    style = Stroke(width = strokePx),
                )
                val c = Color.White
                // 左上
                drawRect(c, Offset(frame.left, frame.top), Size(cornerLenPx, cornerWPx))
                drawRect(c, Offset(frame.left, frame.top), Size(cornerWPx, cornerLenPx))
                // 右上
                drawRect(c, Offset(frame.right - cornerLenPx, frame.top), Size(cornerLenPx, cornerWPx))
                drawRect(c, Offset(frame.right - cornerWPx, frame.top), Size(cornerWPx, cornerLenPx))
                // 左下
                drawRect(c, Offset(frame.left, frame.bottom - cornerWPx), Size(cornerLenPx, cornerWPx))
                drawRect(c, Offset(frame.left, frame.bottom - cornerLenPx), Size(cornerWPx, cornerLenPx))
                // 右下
                drawRect(c, Offset(frame.right - cornerLenPx, frame.bottom - cornerWPx), Size(cornerLenPx, cornerWPx))
                drawRect(c, Offset(frame.right - cornerWPx, frame.bottom - cornerLenPx), Size(cornerWPx, cornerLenPx))
            }

            // ── 顶栏 ──
            Row(
                modifier = Modifier.fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss, enabled = !saving) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "关闭",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    "裁剪背景",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // ── 底栏确认 ──
            Box(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                GradientButton(
                    text = if (saving) "保存中..." else "确认",
                    onClick = ::confirmCrop,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = source != null && !saving,
                    height = 50.dp,
                )
            }

            // ── 加载 / 错误态 ──
            if (source == null && !loadError) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            if (loadError) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "无法读取该图片",
                        color = Color.White.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
