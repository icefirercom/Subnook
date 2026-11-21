package com.subnook.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import java.io.File
import java.util.UUID

/** 实物凭证照片存储（自动压缩长边 <= 1440px 的 JPEG，每张约 100~200KB，避免拖慢 WebDAV 备份） */
object AssetPhotos {
    private const val DIR = "asset_photos"
    private const val MAX_SIDE = 1440

    fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    fun file(context: Context, name: String): File = File(dir(context), name)

    fun delete(context: Context, name: String) {
        runCatching { file(context, name).delete() }
    }

    /** 读取相册图片并压缩保存到私有目录，返回生成的文件名 */
    fun saveFromUri(context: Context, uri: Uri): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val maxOrig = maxOf(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (maxOrig / sample > MAX_SIDE * 2) sample *= 2

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val raw = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) }
            ?: return@runCatching null

        val scaled = scaleDown(raw, MAX_SIDE)
        if (scaled !== raw) raw.recycle()

        val name = "ap_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg"
        val out = file(context, name)
        out.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        scaled.recycle()
        name
    }.getOrNull()

    private fun scaleDown(bmp: Bitmap, maxSide: Int): Bitmap {
        val w = bmp.width
        val h = bmp.height
        val longest = maxOf(w, h)
        if (longest <= maxSide) return bmp
        val ratio = maxSide.toFloat() / longest.toFloat()
        val m = Matrix().apply { postScale(ratio, ratio) }
        return Bitmap.createBitmap(bmp, 0, 0, w, h, m, true)
    }
}
