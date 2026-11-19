package com.mentalcoach.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max

interface PhotoStore {
    /** Downscales a freshly captured JPEG into the visit folder, deletes [source], and returns the new path. */
    suspend fun importCapture(
        visitId: String,
        source: File,
        mirror: Boolean,
    ): String

    suspend fun delete(path: String)

    fun deleteVisit(visitId: String)

    /** Removes photo folders of visits that no longer exist (e.g. an abandoned check-in). */
    fun deleteAllExcept(visitId: String?)

    /** A scratch file for the camera to write into. */
    fun tempFile(): File
}

/** Photos stay in app-private storage: filesDir/visits/<visitId>/. Never uploaded. */
class FilePhotoStore(
    private val filesDir: File,
    private val cacheDir: File,
) : PhotoStore {
    private fun dir(visitId: String) = File(File(filesDir, "visits"), visitId).apply { mkdirs() }

    override fun tempFile(): File = File.createTempFile("capture", ".jpg", cacheDir)

    override suspend fun importCapture(
        visitId: String,
        source: File,
        mirror: Boolean,
    ): String =
        withContext(Dispatchers.IO) {
            try {
                val out = File(dir(visitId), "${UUID.randomUUID()}.jpg")
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(source.path, bounds)
                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
                val raw =
                    BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })
                        ?: error("Could not decode photo")
                val scale = minOf(1f, MAX_SIDE.toFloat() / max(raw.width, raw.height))
                val m = Matrix()
                // Rotate upright first, then mirror horizontally like the front-camera preview.
                m.postRotate(exifRotation(source).toFloat())
                m.postScale(if (mirror) -scale else scale, scale)
                val bmp = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
                out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                if (bmp !== raw) bmp.recycle()
                raw.recycle()
                out.path
            } finally {
                source.delete()
            }
        }

    private fun exifRotation(f: File): Int =
        when (ExifInterface(f.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }

    override suspend fun delete(path: String) {
        withContext(Dispatchers.IO) { File(path).delete() }
    }

    override fun deleteVisit(visitId: String) {
        File(File(filesDir, "visits"), visitId).deleteRecursively()
    }

    override fun deleteAllExcept(visitId: String?) {
        File(filesDir, "visits").listFiles()?.filter { it.name != visitId }?.forEach { it.deleteRecursively() }
        cacheDir.listFiles { f -> f.name.startsWith("capture") }?.forEach { it.delete() }
    }

    companion object {
        const val MAX_SIDE = 1024
        const val JPEG_QUALITY = 82
    }
}
