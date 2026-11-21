package com.subnook.app.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

private const val MAX_ZOOM = 6f

/**
 * 相册图片裁剪：方形取景框，拖动 / 双指缩放选择区域，双击还原。
 * 读图和保存都在后台线程；失败时在对话框里显示原因，不再「点了没反应」。
 */
@Composable
fun ImageCropDialog(uri: Uri, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var error by remember(uri) { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var scale by remember(uri) { mutableFloatStateOf(1f) }
    var offset by remember(uri) { mutableStateOf(Offset.Zero) }
    var viewPx by remember { mutableIntStateOf(0) }

    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) { runCatching { IconFiles.decode(context, uri) } }
            .onSuccess { bitmap = it }
            .onFailure { error = "无法读取这张图片：${it.message ?: it.javaClass.simpleName}" }
    }

    /** 取景框边长 [s] 像素、缩放 [z] 时，图片每个像素对应的屏幕像素 */
    fun factor(b: Bitmap, s: Float, z: Float) = s / min(b.width, b.height) * z

    fun clampOffset(b: Bitmap, s: Float, z: Float, o: Offset): Offset {
        val k = factor(b, s, z)
        val maxX = ((b.width * k - s) / 2).coerceAtLeast(0f)
        val maxY = ((b.height * k - s) / 2).coerceAtLeast(0f)
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    val confirm: () -> Unit = confirm@{
        val b = bitmap ?: return@confirm
        val s = viewPx.toFloat().takeIf { it > 0 } ?: return@confirm
        val k = factor(b, s, scale)
        // 取景框左上角在原图中的位置
        val left = ((b.width * k - s) / 2 - offset.x) / k
        val top = ((b.height * k - s) / 2 - offset.y) / k
        val side = s / k
        saving = true
        scope.launch {
            withContext(Dispatchers.IO) {
                runCatching { IconFiles.saveCropped(context, b, left.roundToInt(), top.roundToInt(), side.roundToInt()) }
            }.onSuccess(onDone).onFailure {
                saving = false
                error = "保存失败：${it.message ?: it.javaClass.simpleName}"
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(28.dp), modifier = Modifier.fillMaxWidth().padding(20.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("裁剪图标", style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp))
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Color(0xFF111111))
                        .onSizeChanged { viewPx = it.width },
                    contentAlignment = Alignment.Center,
                ) {
                    val b = bitmap
                    when {
                        b != null -> {
                            val image = remember(b) { b.asImageBitmap() }
                            Canvas(
                                Modifier.fillMaxSize()
                                    .pointerInput(b) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            val s = size.width.toFloat()
                                            val z = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                                            offset = clampOffset(b, s, z, offset + pan)
                                            scale = z
                                        }
                                    }
                                    .pointerInput(b) {
                                        detectTapGestures(onDoubleTap = { scale = 1f; offset = Offset.Zero })
                                    },
                            ) {
                                val s = size.width
                                val k = factor(b, s, scale)
                                val dw = b.width * k
                                val dh = b.height * k
                                drawImage(
                                    image,
                                    dstOffset = IntOffset(((s - dw) / 2 + offset.x).roundToInt(), ((size.height - dh) / 2 + offset.y).roundToInt()),
                                    dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
                                    filterQuality = FilterQuality.High,
                                )
                                // 按图标的圆角形状预览：圆角外压暗，并描一圈边
                                val icon = Path().apply { addRoundRect(RoundRect(0f, 0f, s, size.height, CornerRadius(s * 0.26f))) }
                                clipPath(icon, ClipOp.Difference) { drawRect(Color.Black.copy(alpha = 0.55f)) }
                                drawPath(icon, Color.White.copy(alpha = 0.8f), style = Stroke(1.5.dp.toPx()))
                                // 三分线
                                val line = Color.White.copy(alpha = 0.25f)
                                for (i in 1..2) {
                                    drawLine(line, Offset(s * i / 3, 0f), Offset(s * i / 3, size.height), 1.dp.toPx())
                                    drawLine(line, Offset(0f, size.height * i / 3), Offset(s, size.height * i / 3), 1.dp.toPx())
                                }
                            }
                        }
                        error == null -> CircularProgressIndicator(color = Color.White)
                    }
                    error?.let {
                        Text(
                            it, color = Color.White, textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.7f)).padding(16.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(onClick = confirm, enabled = bitmap != null && !saving) { Text(if (saving) "保存中…" else "使用") }
                }
            }
        }
    }
}
