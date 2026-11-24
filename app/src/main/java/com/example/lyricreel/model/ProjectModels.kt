package com.example.lyricreel.model

import java.util.UUID

data class MediaClip(
    val id: String = UUID.randomUUID().toString(),
    val uri: String,
    val name: String,
    val isImage: Boolean,
    val sourceDurationMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = sourceDurationMs,
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val fillScreen: Boolean = true
) {
    val effectiveDurationMs: Long
        get() = (trimEndMs - trimStartMs).coerceAtLeast(300L)
}

data class LyricLine(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val startTimeMs: Long? = null
)

data class ReelProject(
    val clips: List<MediaClip> = emptyList(),
    val audioUri: String? = null,
    val audioName: String? = null,
    val audioDurationMs: Long = 0L,
    val audioTrimStartMs: Long = 0L,
    val muteOriginalAudio: Boolean = true,
    val lyrics: List<LyricLine> = emptyList(),
    val textXRatio: Float = 0.5f,
    val textYRatio: Float = 0.72f,
    val textScale: Float = 1.0f,
    
    // Nuevas propiedades de Estilo (Fase 3)
    val lyricAnimation: String = "SLIDE_UP",
    val lyricFont: String = "SANS",
    val textColor: String = "#FFFFFF",
    val strokeColor: String = "#000000",
    val hasStroke: Boolean = true,
    val hasShadow: Boolean = true
) {
    val totalDurationMs: Long
        get() = clips.sumOf { it.effectiveDurationMs }
}
