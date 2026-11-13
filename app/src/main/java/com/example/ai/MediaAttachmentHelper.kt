package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "MediaAttachmentHelper"
private const val MAX_INLINE_VIDEO_BYTES = 19 * 1024 * 1024 // < 20MB inline payload limit

enum class AttachmentKind {
    PHOTO,
    VIDEO
}

data class PendingMediaAttachment(
    val uri: Uri,
    val kind: AttachmentKind,
    val mimeType: String,
    val previewBitmap: Bitmap?,
    val localPreviewPath: String?,
    val thumbBase64: String?,
    val videoBytes: ByteArray? = null,
    val displayName: String
)

object MediaAttachmentHelper {

    suspend fun loadPhotoAttachment(context: Context, uri: Uri): Result<PendingMediaAttachment> =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val mimeType = resolver.getType(uri) ?: "image/jpeg"

                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOptions)
                }

                val maxDim = 1536
                var sampleSize = 1
                while (
                    boundsOptions.outWidth / sampleSize > maxDim ||
                    boundsOptions.outHeight / sampleSize > maxDim
                ) {
                    sampleSize *= 2
                }

                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val decoded = resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOptions)
                } ?: return@withContext Result.failure(IllegalArgumentException("Unable to decode selected image."))

                val localPath = saveBitmapToInternalFile(context, decoded, "photo")
                val thumbB64 = bitmapToCompactBase64(decoded, maxEdge = 384, quality = 72)

                Result.success(
                    PendingMediaAttachment(
                        uri = uri,
                        kind = AttachmentKind.PHOTO,
                        mimeType = mimeType,
                        previewBitmap = decoded,
                        localPreviewPath = localPath,
                        thumbBase64 = thumbB64,
                        displayName = "Photo attached"
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load photo attachment", e)
                Result.failure(e)
            }
        }

    suspend fun loadVideoAttachment(context: Context, uri: Uri): Result<PendingMediaAttachment> =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val mimeType = resolver.getType(uri) ?: "video/mp4"

                val bytes = resolver.openInputStream(uri)?.use { input ->
                    val buffer = ByteArrayOutputStream()
                    val chunk = ByteArray(16 * 1024)
                    var total = 0
                    while (true) {
                        val read = input.read(chunk)
                        if (read <= 0) break
                        total += read
                        if (total > MAX_INLINE_VIDEO_BYTES) {
                            return@withContext Result.failure(
                                IllegalArgumentException("Please select a video under 19 MB.")
                            )
                        }
                        buffer.write(chunk, 0, read)
                    }
                    buffer.toByteArray()
                } ?: return@withContext Result.failure(IllegalArgumentException("Unable to read selected video."))

                val frameBitmap = extractVideoThumbnail(context, uri)
                val localPath = frameBitmap?.let { saveBitmapToInternalFile(context, it, "video_thumb") }
                val thumbB64 = frameBitmap?.let { bitmapToCompactBase64(it, maxEdge = 384, quality = 70) }

                Result.success(
                    PendingMediaAttachment(
                        uri = uri,
                        kind = AttachmentKind.VIDEO,
                        mimeType = mimeType,
                        previewBitmap = frameBitmap,
                        localPreviewPath = localPath,
                        thumbBase64 = thumbB64,
                        videoBytes = bytes,
                        displayName = "Video (${bytes.size / 1024} KB)"
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load video attachment", e)
                Result.failure(e)
            }
        }

    fun decodeDisplayBitmap(localPath: String?, thumbBase64: String?): Bitmap? {
        if (!localPath.isNullOrBlank()) {
            val file = File(localPath)
            if (file.exists()) {
                val bmp = BitmapFactory.decodeFile(file.absolutePath)
                if (bmp != null) return bmp
            }
        }
        if (!thumbBase64.isNullOrBlank()) {
            return try {
                val bytes = Base64.decode(thumbBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: Exception) {
                null
            }
        }
        return null
    }

    private fun extractVideoThumbnail(context: Context, uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (e: Exception) {
            Log.w(TAG, "Video frame extraction fallback", e)
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun saveBitmapToInternalFile(context: Context, bitmap: Bitmap, prefix: String): String? {
        return try {
            val dir = File(context.filesDir, "nova_media")
            if (!dir.exists()) dir.mkdirs()
            val outFile = File(dir, "${prefix}_${UUID.randomUUID().toString().take(12)}.jpg")
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            }
            outFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed saving bitmap locally", e)
            null
        }
    }

    private fun bitmapToCompactBase64(bitmap: Bitmap, maxEdge: Int, quality: Int): String? {
        return try {
            val width = bitmap.width.coerceAtLeast(1)
            val height = bitmap.height.coerceAtLeast(1)
            val scale = (maxEdge.toFloat() / maxOf(width, height).toFloat()).coerceAtMost(1f)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (width * scale).toInt().coerceAtLeast(1),
                    (height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                bitmap
            }
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }
}
