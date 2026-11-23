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

data class ReelProject(
    val clips: List<MediaClip> = emptyList(),
    val audioUri: String? = null,
    val audioName: String? = null,
    val audioDurationMs: Long = 0L,
    val audioTrimStartMs: Long = 0L,
    val muteOriginalAudio: Boolean = true
) {
    val totalDurationMs: Long
        get() = clips.sumOf { it.effectiveDurationMs }
}
