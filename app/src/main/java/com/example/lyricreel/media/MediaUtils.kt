package com.example.lyricreel.media

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.util.Locale

object MediaUtils {

    fun persistUriPermission(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {
        }
    }

    fun getFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx != -1) result = cursor.getString(idx)
                    }
                }
            } catch (_: Exception) {
            }
        }
        return result ?: uri.lastPathSegment ?: "Archivo"
    }

    fun isImageUri(context: Context, uri: Uri): Boolean {
        val mime = context.contentResolver.getType(uri) ?: ""
        return mime.startsWith("image/")
    }

    fun getMediaDurationMs(context: Context, uri: Uri, defaultIfImage: Long = 3000L): Long {
        if (isImageUri(context, uri)) return defaultIfImage
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLongOrNull()?.coerceAtLeast(500L) ?: 3000L
        } catch (_: Exception) {
            3000L
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    fun loadSampledBitmap(context: Context, uriString: String, reqWidth: Int = 1080, reqHeight: Int = 1920): Bitmap? {
        return try {
            val uri = Uri.parse(uriString)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            var inSampleSize = 1
            val height = options.outHeight
            val width = options.outWidth
            if (height > reqHeight || width > reqWidth) {
                val halfHeight = height / 2
                val halfWidth = width / 2
                while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2
                }
            }
            val decodeOptions = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun formatTimeMs(ms: Long): String {
        val totalTenths = (ms.coerceAtLeast(0L)) / 100L
        val minutes = totalTenths / 600L
        val seconds = (totalTenths % 600L) / 10L
        val tenths = totalTenths % 10L
        return String.format(Locale.US, "%02d:%02d.%d", minutes, seconds, tenths)
    }
}
