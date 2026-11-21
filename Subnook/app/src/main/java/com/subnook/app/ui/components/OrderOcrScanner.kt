package com.subnook.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.subnook.app.domain.OrderOcrParser
import com.subnook.app.domain.OrderOcrResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 离线本地订单截图 OCR 识别器（基于 ML Kit 内置中文模型，不联网、不上传图片）。
 */
object OrderOcrScanner {
    private const val MAX_SIDE = 2000

    suspend fun scan(context: Context, uri: Uri): OrderOcrResult = withContext(Dispatchers.IO) {
        val bmp = decodeScaled(context, uri) ?: error("无法读取所选图片")
        try {
            val text = recognizeBitmap(bmp)
            OrderOcrParser.parse(text)
        } finally {
            bmp.recycle()
        }
    }

    private suspend fun recognizeBitmap(bmp: Bitmap): String = suspendCancellableCoroutine { cont ->
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        val image = InputImage.fromBitmap(bmp, 0)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                recognizer.close()
                if (cont.isActive) cont.resume(result.text)
            }
            .addOnFailureListener { e ->
                recognizer.close()
                if (cont.isActive) cont.resumeWithException(e)
            }
        cont.invokeOnCancellation { runCatching { recognizer.close() } }
    }

    private fun decodeScaled(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        var sample = 1
        while (longest / sample > MAX_SIDE * 2) sample *= 2
        val raw = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val curLongest = maxOf(raw.width, raw.height)
        if (curLongest <= MAX_SIDE) return raw
        val ratio = MAX_SIDE.toFloat() / curLongest.toFloat()
        val m = Matrix().apply { postScale(ratio, ratio) }
        val scaled = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        if (scaled !== raw) raw.recycle()
        return scaled
    }
}
