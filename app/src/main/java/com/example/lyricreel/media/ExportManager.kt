package com.example.lyricreel.media

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.example.lyricreel.model.ReelProject
import java.io.File

object ExportManager {
    fun exportProject(
        context: Context,
        project: ReelProject,
        onComplete: (Boolean, String) -> Unit
    ) {
        try {
            val outputDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "LyricReel")
            if (!outputDir.exists()) outputDir.mkdirs()
            val outputFile = File(outputDir, "Reel_${System.currentTimeMillis()}.mp4")

            val transformer = Transformer.Builder(context)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        onComplete(true, outputFile.absolutePath)
                    }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        onComplete(false, exportException.message ?: "Error de renderizado")
                    }
                })
                .build()

            val videoItems = project.clips.map { clip ->
                val mediaItem = MediaItem.Builder().setUri(Uri.parse(clip.uri)).build()
                EditedMediaItem.Builder(mediaItem).build()
            }
            
            if (videoItems.isEmpty()) {
                onComplete(false, "No hay clips para exportar")
                return
            }

            val videoSequence = EditedMediaItemSequence(videoItems)
            val composition = Composition.Builder(listOf(videoSequence)).build()
            
            transformer.start(composition, outputFile.absolutePath)

        } catch (e: Exception) {
            onComplete(false, e.message ?: "Fallo al inicializar")
        }
    }
}
