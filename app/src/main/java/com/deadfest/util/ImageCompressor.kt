package com.deadfest.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Turns a picked photo into upload-ready JPEGs: EXIF rotation fixed, scaled down, and
 * compressed until it fits comfortably inside a Firestore document (1 MiB hard limit).
 * Never decodes the full-resolution photo, so a 50 MP camera shot won't run a low-end
 * phone out of memory.
 */
object ImageCompressor {
    const val FULL_MAX_DIM = 1600
    const val THUMB_MAX_DIM = 320
    const val MAX_FULL_BYTES = 700_000

    class Processed(val full: ByteArray, val thumb: ByteArray, val width: Int, val height: Int)

    fun process(context: Context, uri: Uri): Processed {
        val resolver = context.contentResolver

        // 1. Read dimensions only.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "That file isn't a supported image." }

        // 2. Decode at a power-of-two sample size close to the target size.
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= FULL_MAX_DIM) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalArgumentException("Couldn't read that image.")

        // 3. Exact scale + EXIF rotation.
        val rotation = readRotation(context, uri)
        var bitmap = transform(decoded, FULL_MAX_DIM, rotation)

        // 4. Compress, stepping quality then size down until it fits.
        var quality = 82
        var bytes = jpeg(bitmap, quality)
        while (bytes.size > MAX_FULL_BYTES && quality > 52) {
            quality -= 10
            bytes = jpeg(bitmap, quality)
        }
        var dim = max(bitmap.width, bitmap.height)
        while (bytes.size > MAX_FULL_BYTES && dim > 640) {
            dim = (dim * 0.75f).roundToInt()
            bitmap = transform(bitmap, dim, 0)
            bytes = jpeg(bitmap, quality)
        }

        val thumbBitmap = transform(bitmap, THUMB_MAX_DIM, 0, recycleSource = false)
        val thumb = jpeg(thumbBitmap, 65)
        val result = Processed(bytes, thumb, bitmap.width, bitmap.height)
        if (thumbBitmap !== bitmap) thumbBitmap.recycle()
        bitmap.recycle()
        return result
    }

    private fun readRotation(context: Context, uri: Uri): Int = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    } catch (_: Exception) {
        0
    }

    /** Scales so the longest side is at most [maxDim], then rotates. Recycles the source if replaced. */
    private fun transform(src: Bitmap, maxDim: Int, rotation: Int, recycleSource: Boolean = true): Bitmap {
        val longest = max(src.width, src.height)
        val scale = if (longest > maxDim) maxDim.toFloat() / longest else 1f
        if (scale == 1f && rotation == 0) return src
        val matrix = Matrix().apply {
            postScale(scale, scale)
            if (rotation != 0) postRotate(rotation.toFloat())
        }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        if (recycleSource && out !== src) src.recycle()
        return out
    }

    private fun jpeg(bitmap: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
}

/**
 * Decoded-thumbnail cache, capped at 8 MB. Thumbnails decode as RGB_565 (half the memory
 * of ARGB) since JPEGs have no transparency anyway.
 */
object ThumbnailCache {
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun get(id: String): Bitmap? = cache.get(id)

    fun decode(id: String, bytes: ByteArray): Bitmap? {
        cache.get(id)?.let { return it }
        val bmp = BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
        ) ?: return null
        cache.put(id, bmp)
        return bmp
    }

    /** Full image for the viewer, sampled down if it's far bigger than the screen. */
    fun decodeFull(bytes: ByteArray, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
