package com.example.lyricreel.ui.components

import android.net.Uri
import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.lyricreel.R
import com.example.lyricreel.media.MediaUtils
import com.example.lyricreel.model.MediaClip
import com.example.lyricreel.model.ReelProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

enum class CanvasTouchTarget { MEDIA, LYRICS }

@OptIn(UnstableApi::class)
@Composable
fun ReelPreviewPlayer(
    activeClip: MediaClip?,
    localClipMs: Long,
    currentTimelineMs: Long,
    isPlaying: Boolean,
    playbackSpeed: Float,
    project: ReelProject,
    activeLyricText: String?,
    activeLyricStartTime: Long,
    touchTarget: CanvasTouchTarget,
    onUpdateClipTransform: (scale: Float, offsetX: Float, offsetY: Float) -> Unit,
    onUpdateTextTransform: (xRatio: Float, yRatio: Float, scale: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val videoPlayer = remember { ExoPlayer.Builder(context).build() }
    val audioPlayer = remember { ExoPlayer.Builder(context).build() }

    DisposableEffect(Unit) {
        onDispose {
            videoPlayer.release()
            audioPlayer.release()
        }
    }

    LaunchedEffect(playbackSpeed) {
        videoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
        audioPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
    }

    LaunchedEffect(activeClip?.id, activeClip?.uri, isPlaying, project.muteOriginalAudio) {
        if (activeClip != null && !activeClip.isImage) {
            val currentUri = videoPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
            if (currentUri != activeClip.uri) {
                videoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(activeClip.uri)))
                videoPlayer.prepare()
            }
            videoPlayer.volume = if (project.muteOriginalAudio) 0f else 1f
            val targetPos = (activeClip.trimStartMs + localClipMs).coerceIn(0L, activeClip.sourceDurationMs)
            if (abs(videoPlayer.currentPosition - targetPos) > 220L) videoPlayer.seekTo(targetPos)
            videoPlayer.playWhenReady = isPlaying
        } else {
            videoPlayer.playWhenReady = false
        }
    }

    LaunchedEffect(localClipMs, activeClip?.id, isPlaying) {
        if (!isPlaying && activeClip != null && !activeClip.isImage) {
            val targetPos = (activeClip.trimStartMs + localClipMs).coerceIn(0L, activeClip.sourceDurationMs)
            if (abs(videoPlayer.currentPosition - targetPos) > 60L) videoPlayer.seekTo(targetPos)
        }
    }

    LaunchedEffect(project.audioUri, isPlaying, project.audioTrimStartMs) {
        if (project.audioUri != null) {
            val currentAudioUri = audioPlayer.currentMediaItem?.localConfiguration?.uri?.toString()
            if (currentAudioUri != project.audioUri) {
                audioPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(project.audioUri)))
                audioPlayer.prepare()
            }
            val targetAudio = (project.audioTrimStartMs + currentTimelineMs).coerceAtLeast(0L)
            if (abs(audioPlayer.currentPosition - targetAudio) > 220L) audioPlayer.seekTo(targetAudio)
            audioPlayer.playWhenReady = isPlaying
        } else {
            audioPlayer.playWhenReady = false
        }
    }

    LaunchedEffect(currentTimelineMs, project.audioUri, project.audioTrimStartMs, isPlaying) {
        if (!isPlaying && project.audioUri != null) {
            val targetAudio = (project.audioTrimStartMs + currentTimelineMs).coerceAtLeast(0L)
            if (abs(audioPlayer.currentPosition - targetAudio) > 80L) audioPlayer.seekTo(targetAudio)
        }
    }

    var imageBitmap by remember(activeClip?.uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(activeClip?.uri, activeClip?.isImage) {
        if (activeClip != null && activeClip.isImage) {
            val bmp = withContext(Dispatchers.IO) { MediaUtils.loadSampledBitmap(context, activeClip.uri) }
            imageBitmap = bmp?.asImageBitmap()
        } else imageBitmap = null
    }

    val latestClip by rememberUpdatedState(activeClip)
    val latestTouchTarget by rememberUpdatedState(touchTarget)
    val latestTextX by rememberUpdatedState(project.textXRatio)
    val latestTextY by rememberUpdatedState(project.textYRatio)
    val latestTextScale by rememberUpdatedState(project.textScale)
    val latestOnClipTransform by rememberUpdatedState(onUpdateClipTransform)
    val latestOnTextTransform by rememberUpdatedState(onUpdateTextTransform)

    BoxWithConstraints(
        modifier = modifier.fillMaxHeight().aspectRatio(9f / 16f).clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0A0A0A)).border(1.dp, Color(0xFF262626), RoundedCornerShape(12.dp))
    ) {
        val boxWidthPx = with(density) { maxWidth.toPx().coerceAtLeast(1f) }
        val boxHeightPx = with(density) { maxHeight.toPx().coerceAtLeast(1f) }

        if (activeClip == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Toca '+ Clip' abajo para importar\nun video o foto (9:16)", color = Color.Gray, textAlign = TextAlign.Center)
            }
        } else if (activeClip.isImage) {
            imageBitmap?.let { bmp ->
                Image(
                    bitmap = bmp, contentDescription = null, contentScale = if (activeClip.fillScreen) ContentScale.Crop else ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = activeClip.scale, scaleY = activeClip.scale, translationX = activeClip.offsetX, translationY = activeClip.offsetY)
                )
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    val view = LayoutInflater.from(ctx).inflate(R.layout.texture_player_view, null, false) as PlayerView
                    view.player = videoPlayer
                    view
                },
                update = { playerView -> playerView.resizeMode = if (activeClip.fillScreen) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT },
                modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = activeClip.scale, scaleY = activeClip.scale, translationX = activeClip.offsetX, translationY = activeClip.offsetY)
            )
        }

        Box(
            modifier = Modifier.fillMaxSize().pointerInput(boxWidthPx, boxHeightPx) {
                detectTransformGestures(panZoomLock = false) { _, pan, zoom, _ ->
                    if (latestTouchTarget == CanvasTouchTarget.MEDIA) {
                        val clip = latestClip ?: return@detectTransformGestures
                        latestOnClipTransform((clip.scale * zoom).coerceIn(0.4f, 4.5f), (clip.offsetX + pan.x).coerceIn(-boxWidthPx, boxWidthPx), (clip.offsetY + pan.y).coerceIn(-boxHeightPx, boxHeightPx))
                    } else {
                        latestOnTextTransform((latestTextX + (pan.x / boxWidthPx)).coerceIn(0.08f, 0.92f), (latestTextY + (pan.y / boxHeightPx)).coerceIn(0.08f, 0.92f), (latestTextScale * zoom).coerceIn(0.5f, 3.0f))
                    }
                }
            }
        )

        // ANIMACIÓN DE LETRAS
        if (!activeLyricText.isNullOrBlank()) {
            val posX = (boxWidthPx * project.textXRatio).roundToInt()
            val posY = (boxHeightPx * project.textYRatio).roundToInt()
            
            // Calculos de animación
            val elapsedMs = (currentTimelineMs - activeLyricStartTime).coerceAtLeast(0L)
            val progress = (elapsedMs / 350f).coerceIn(0f, 1f)
            
            val animAlpha = if (project.lyricAnimation in listOf("FADE", "SLIDE_UP", "ZOOM")) progress else 1f
            val animOffsetY = if (project.lyricAnimation == "SLIDE_UP") 40f * (1f - progress) else 0f
            val animScale = if (project.lyricAnimation == "ZOOM") 0.6f + (0.4f * progress) else 1f

            val displayText = when (project.lyricAnimation) {
                "TYPEWRITER" -> {
                    val chars = (elapsedMs / 40L).toInt().coerceIn(0, activeLyricText.length)
                    activeLyricText.substring(0, chars)
                }
                "WORD_BY_WORD" -> {
                    val words = activeLyricText.split(" ")
                    val wordsToShow = (elapsedMs / 180L).toInt().coerceIn(0, words.size)
                    words.take(wordsToShow).joinToString(" ")
                }
                else -> activeLyricText
            }

            val fontFamily = when(project.lyricFont) {
                "SERIF" -> FontFamily.Serif
                "MONOSPACE" -> FontFamily.Monospace
                "CURSIVE" -> FontFamily.Cursive
                else -> FontFamily.SansSerif
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(posX, posY) }
                    .graphicsLayer(
                        translationX = -boxWidthPx * 0.42f, // Ajuste para centrar respecto al tap
                        translationY = -40f + animOffsetY,
                        scaleX = project.textScale * animScale,
                        scaleY = project.textScale * animScale,
                        alpha = animAlpha
                    )
                    .widthIn(max = maxWidth * 0.84f)
                    .then(if (touchTarget == CanvasTouchTarget.LYRICS) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha=0.7f), RoundedCornerShape(6.dp)) else Modifier)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                // Capa de Contorno (Stroke)
                if (project.hasStroke) {
                    Text(
                        text = displayText,
                        color = Color(android.graphics.Color.parseColor(project.strokeColor)),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = fontFamily,
                        textAlign = TextAlign.Center,
                        style = TextStyle(drawStyle = Stroke(width = 9f, join = StrokeJoin.Round))
                    )
                }
                // Capa Principal (Relleno y Sombra)
                Text(
                    text = displayText,
                    color = Color(android.graphics.Color.parseColor(project.textColor)),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily,
                    textAlign = TextAlign.Center,
                    style = if (project.hasShadow) TextStyle(shadow = Shadow(color = Color.Black, offset = Offset(4f, 4f), blurRadius = 8f)) else TextStyle.Default
                )
            }
        }
    }
}
