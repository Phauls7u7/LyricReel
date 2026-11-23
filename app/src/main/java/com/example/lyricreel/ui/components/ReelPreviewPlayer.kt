package com.example.lyricreel.ui.components

import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.lyricreel.media.MediaUtils
import com.example.lyricreel.model.MediaClip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

@OptIn(UnstableApi::class)
@Composable
fun ReelPreviewPlayer(
    activeClip: MediaClip?,
    localClipMs: Long,
    currentTimelineMs: Long,
    isPlaying: Boolean,
    audioUri: String?,
    audioTrimStartMs: Long,
    muteOriginalAudio: Boolean,
    onUpdateClipTransform: (scale: Float, offsetX: Float, offsetY: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val videoPlayer = remember {
        ExoPlayer.Builder(context).build()
    }
    val audioPlayer = remember {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(Unit) {
        onDispose {
            videoPlayer.release()
            audioPlayer.release()
        }
    }

    // Sincronización del reproductor de video
    LaunchedEffect(activeClip?.id, activeClip?.uri, isPlaying, muteOriginalAudio) {
        if (activeClip != null && !activeClip.isImage) {
            val currentUri = videoPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
            if (currentUri != activeClip.uri) {
                videoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(activeClip.uri)))
                videoPlayer.prepare()
            }
            videoPlayer.volume = if (muteOriginalAudio) 0f else 1f
            val targetPos = (activeClip.trimStartMs + localClipMs).coerceIn(0L, activeClip.sourceDurationMs)
            if (abs(videoPlayer.currentPosition - targetPos) > 200L) {
                videoPlayer.seekTo(targetPos)
            }
            videoPlayer.playWhenReady = isPlaying
        } else {
            videoPlayer.playWhenReady = false
        }
    }

    // Sincronización cuadro a cuadro al arrastrar línea de tiempo en pausa
    LaunchedEffect(localClipMs, activeClip?.id, isPlaying) {
        if (!isPlaying && activeClip != null && !activeClip.isImage) {
            val targetPos = (activeClip.trimStartMs + localClipMs).coerceIn(0L, activeClip.sourceDurationMs)
            if (abs(videoPlayer.currentPosition - targetPos) > 60L) {
                videoPlayer.seekTo(targetPos)
            }
        }
    }

    // Sincronización de la canción de fondo
    LaunchedEffect(audioUri, isPlaying, audioTrimStartMs) {
        if (audioUri != null) {
            val currentAudioUri = audioPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
            if (currentAudioUri != audioUri) {
                audioPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(audioUri)))
                audioPlayer.prepare()
            }
            val targetAudio = (audioTrimStartMs + currentTimelineMs).coerceAtLeast(0L)
            if (abs(audioPlayer.currentPosition - targetAudio) > 200L) {
                audioPlayer.seekTo(targetAudio)
            }
            audioPlayer.playWhenReady = isPlaying
        } else {
            audioPlayer.playWhenReady = false
        }
    }

    LaunchedEffect(currentTimelineMs, audioUri, audioTrimStartMs, isPlaying) {
        if (!isPlaying && audioUri != null) {
            val targetAudio = (audioTrimStartMs + currentTimelineMs).coerceAtLeast(0L)
            if (abs(audioPlayer.currentPosition - targetAudio) > 80L) {
                audioPlayer.seekTo(targetAudio)
            }
        }
    }

    // Cargar bitmap si el clip activo es imagen
    var imageBitmap by remember(activeClip?.uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(activeClip?.uri, activeClip?.isImage) {
        if (activeClip != null && activeClip.isImage) {
            val bmp = withContext(Dispatchers.IO) {
                MediaUtils.loadSampledBitmap(context, activeClip.uri)
            }
            imageBitmap = bmp?.asImageBitmap()
        } else {
            imageBitmap = null
        }
    }

    // Marco 9:16 con gestos de zoom y desplazamiento
    Box(
        modifier = modifier
            .fillMaxHeight()
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0A0A0A))
            .border(1.dp, Color(0xFF222222), RoundedCornerShape(12.dp))
            .pointerInput(activeClip?.id) {
                if (activeClip != null) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (activeClip.scale * zoom).coerceIn(0.5f, 4.0f)
                        val newOffsetX = (activeClip.offsetX + pan.x).coerceIn(-600f, 600f)
                        val newOffsetY = (activeClip.offsetY + pan.y).coerceIn(-900f, 900f)
                        onUpdateClipTransform(newScale, newOffsetX, newOffsetY)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (activeClip == null) {
            Text(
                text = "Toca '+ Clip' abajo para importar\nun video o foto (9:16)",
                color = Color.Gray,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )
        } else if (activeClip.isImage) {
            imageBitmap?.let { bmp ->
                Image(
                    bitmap = bmp,
                    contentDescription = activeClip.name,
                    contentScale = if (activeClip.fillScreen) ContentScale.Crop else ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = activeClip.scale,
                            scaleY = activeClip.scale,
                            translationX = activeClip.offsetX,
                            translationY = activeClip.offsetY
                        )
                )
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        player = videoPlayer
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { playerView ->
                    playerView.resizeMode = if (activeClip.fillScreen) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = activeClip.scale,
                        scaleY = activeClip.scale,
                        translationX = activeClip.offsetX,
                        translationY = activeClip.offsetY
                    )
            )
        }
    }
}
