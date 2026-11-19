package com.example.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object ImageCompressor {
    private const val MAX_DIMENSION = 420
    private const val MAX_BYTES = 135_000

    suspend fun compressUriToDataUri(context: Context, uri: Uri): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.applicationContext.contentResolver
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOpts)
                } ?: return@withContext Result.failure(IllegalStateException("Cannot open selected image"))

                val sampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, MAX_DIMENSION, MAX_DIMENSION)
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val decoded = resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOpts)
                } ?: return@withContext Result.failure(IllegalStateException("Failed to decode image"))

                val scaled = scaleDown(decoded, MAX_DIMENSION)
                if (scaled !== decoded) decoded.recycle()

                var quality = 80
                val baos = ByteArrayOutputStream()
                do {
                    baos.reset()
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                    quality -= 12
                } while (baos.size() > MAX_BYTES && quality >= 24)

                scaled.recycle()
                val encoded = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                Result.success("data:image/jpeg;base64,$encoded")
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun decodeDataUriToBytes(dataUri: String): ByteArray? {
        if (!dataUri.startsWith("data:image")) return null
        val commaIndex = dataUri.indexOf(',')
        if (commaIndex == -1) return null
        return try {
            Base64.decode(dataUri.substring(commaIndex + 1), Base64.DEFAULT)
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun scaleDown(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = minOf(maxDim.toFloat() / w, maxDim.toFloat() / h)
        val newW = (w * ratio).toInt().coerceAtLeast(1)
        val newH = (h * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newW, newH, true)
    }
}
