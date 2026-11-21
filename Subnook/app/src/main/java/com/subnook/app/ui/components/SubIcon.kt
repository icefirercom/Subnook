package com.subnook.app.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subnook.app.data.IconType
import com.subnook.app.data.PresetCatalog
import java.io.File
import java.util.UUID

object IconFiles {
    fun dir(context: Context) = File(context.filesDir, "icons").apply { mkdirs() }
    fun file(context: Context, name: String) = File(dir(context), name)

    @SuppressLint("DiscouragedApi")
    fun presetRes(context: Context, key: String): Int =
        context.resources.getIdentifier("preset_$key", "drawable", context.packageName)

    /**
     * 读取相册图片，长边缩到 [maxSide] 以内，统一转成 ARGB_8888（耗时，需在后台线程调用）。
     * 部分系统相册「裁剪 / 编辑」后保存的图片 ImageDecoder 会报错（文件不完整、HDR/F16 格式等），
     * 所以 ImageDecoder 失败后再用 BitmapFactory 兜底。
     */
    fun decode(context: Context, uri: Uri, maxSide: Int = 2048): Bitmap {
        val cr = context.contentResolver
        var error: Throwable? = null
        if (Build.VERSION.SDK_INT >= 28) {
            runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(cr, uri)) { d, info, _ ->
                    d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    d.setOnPartialImageListener { true } // 文件末尾不完整也尽量显示
                    val w = info.size.width
                    val h = info.size.height
                    val long = maxOf(w, h)
                    if (long > maxSide) d.setTargetSize((w.toLong() * maxSide / long).toInt().coerceAtLeast(1), (h.toLong() * maxSide / long).toInt().coerceAtLeast(1))
                }
            }.onSuccess { return it.toArgb() }.onFailure { error = it }
        }
        // BitmapFactory：先读尺寸算采样率，再按 EXIF 方向旋转
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IllegalStateException("无法识别的图片格式", error)
        val rotation = runCatching {
            val o = cr.openInputStream(uri)?.use {
                android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)
            }
            when (o) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }.getOrDefault(0)
        if (rotation == 0) return bmp.toArgb()
        val m = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true).toArgb()
    }

    private fun Bitmap.toArgb(): Bitmap = if (config == Bitmap.Config.ARGB_8888) this else copy(Bitmap.Config.ARGB_8888, false)

    /** 从 [src] 裁出正方形区域（[left],[top],[side] 为 src 像素坐标），缩放到 256px 存入私有目录，返回文件名 */
    fun saveCropped(context: Context, src: Bitmap, left: Int, top: Int, side: Int): String {
        val s = side.coerceIn(1, minOf(src.width, src.height))
        val x = left.coerceIn(0, src.width - s)
        val y = top.coerceIn(0, src.height - s)
        val cropped = Bitmap.createBitmap(src, x, y, s, s)
        val scaled = Bitmap.createScaledBitmap(cropped, 256, 256, true)
        val name = "custom_${UUID.randomUUID()}.png"
        file(context, name).outputStream().use { check(scaled.compress(Bitmap.CompressFormat.PNG, 100, it)) { "图片保存失败" } }
        return name
    }

    /** 居中裁成正方形导入（不经过裁剪界面时用） */
    fun importImage(context: Context, uri: Uri): String {
        val src = decode(context, uri, maxSide = 1024)
        val side = minOf(src.width, src.height)
        return saveCropped(context, src, (src.width - side) / 2, (src.height - side) / 2, side)
    }
}

@Composable
fun SubIcon(
    iconType: IconType,
    iconValue: String,
    color: Int,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(size * 0.26f)
    val bg = Color(color)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .border(1.dp, androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f), shape),
        contentAlignment = Alignment.Center,
    ) {
        when (iconType) {
            IconType.PRESET -> {
                val res = remember(iconValue) { IconFiles.presetRes(context, iconValue) }
                if (res != 0) {
                    Image(painterResource(res), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    val mat = PresetCatalog.all.firstOrNull { it.key == iconValue }?.material
                    MaterialFill(mat ?: "", bg, size, name)
                }
            }
            IconType.CUSTOM -> {
                val bmp = remember(iconValue) {
                    runCatching { BitmapFactory.decodeFile(IconFiles.file(context, iconValue).path)?.asImageBitmap() }.getOrNull()
                }
                if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else LetterFill(name, bg, size)
            }
            IconType.MATERIAL -> MaterialFill(iconValue, bg, size, name)
            IconType.TEXT -> LetterFill(iconValue.ifBlank { name }, bg, size)
        }
    }
}

@Composable
private fun MaterialFill(iconName: String, bg: Color, size: Dp, fallback: String) {
    val vec = DesignIcons.byName(iconName)
    if (vec == null) {
        LetterFill(fallback, bg, size); return
    }
    Box(Modifier.fillMaxSize().background(bg), contentAlignment = Alignment.Center) {
        Icon(vec, null, tint = onColor(bg), modifier = Modifier.padding(size * 0.2f).fillMaxSize())
    }
}

@Composable
private fun LetterFill(text: String, bg: Color, size: Dp) {
    val letter = text.trim().take(if (text.trim().firstOrNull()?.let { it.code > 0x2E80 } == true) 1 else 2)
        .uppercase().ifBlank { "?" }
    Box(Modifier.fillMaxSize().background(bg), contentAlignment = Alignment.Center) {
        Text(
            letter,
            color = onColor(bg),
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * if (letter.length > 1) 0.36f else 0.46f).sp,
        )
    }
}

fun onColor(bg: Color): Color {
    val l = 0.299 * bg.red + 0.587 * bg.green + 0.114 * bg.blue
    return if (l > 0.65) Color(0xFF1C1B1F) else Color.White
}
