package com.example.lyricreel

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lyricreel.data.ProjectStorage
import com.example.lyricreel.media.MediaUtils
import com.example.lyricreel.model.MediaClip
import com.example.lyricreel.model.ReelProject
import com.example.lyricreel.ui.components.ReelPreviewPlayer
import com.example.lyricreel.ui.components.TimelineEditor
import com.example.lyricreel.ui.theme.LyricReelTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LyricReelTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Phase1EditorScreen()
                }
            }
        }
    }
}

@Composable
fun Phase1EditorScreen() {
    val context = LocalContext.current
    val storage = remember { ProjectStorage(context) }

    var project by remember { mutableStateOf(storage.loadProject()) }
    var selectedClipIndex by remember { mutableIntStateOf(0) }
    var currentTimelineMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }

    // Autoguardado cada vez que cambia el proyecto
    LaunchedEffect(project) {
        storage.saveProject(project)
    }

    // Asegurar índice seleccionado válido
    LaunchedEffect(project.clips.size) {
        if (project.clips.isEmpty()) {
            selectedClipIndex = 0
            currentTimelineMs = 0L
            isPlaying = false
        } else if (selectedClipIndex > project.clips.lastIndex) {
            selectedClipIndex = project.clips.lastIndex
        }
    }

    // Reloj maestro de reproducción
    LaunchedEffect(isPlaying, project.totalDurationMs) {
        if (isPlaying) {
            val total = project.totalDurationMs
            if (total <= 0L) {
                isPlaying = false
                return@LaunchedEffect
            }
            while (isPlaying) {
                delay(30L)
                val next = currentTimelineMs + 30L
                if (next >= total) {
                    currentTimelineMs = 0L
                    isPlaying = false
                } else {
                    currentTimelineMs = next
                }
            }
        }
    }

    // Calcular clip activo según currentTimelineMs
    val activeClipInfo = remember(project.clips, currentTimelineMs) {
        var accumulated = 0L
        var foundClip: MediaClip? = null
        var foundIndex = 0
        var localOffset = 0L
        for ((idx, clip) in project.clips.withIndex()) {
            val dur = clip.effectiveDurationMs
            if (currentTimelineMs < accumulated + dur || idx == project.clips.lastIndex) {
                foundClip = clip
                foundIndex = idx
                localOffset = (currentTimelineMs - accumulated).coerceIn(0L, dur)
                break
            }
            accumulated += dur
        }
        Triple(foundClip, foundIndex, localOffset)
    }

    val activeClip = activeClipInfo.first
    val activeClipIndexFromPlayhead = activeClipInfo.second
    val localClipMs = activeClipInfo.third

    LaunchedEffect(activeClipIndexFromPlayhead, isPlaying) {
        if (isPlaying && project.clips.isNotEmpty()) {
            selectedClipIndex = activeClipIndexFromPlayhead
        }
    }

    // Selectores de archivos (Videos/Fotos y Canción)
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val newClips = uris.map { uri ->
                MediaUtils.persistUriPermission(context, uri)
                val isImg = MediaUtils.isImageUri(context, uri)
                val dur = MediaUtils.getMediaDurationMs(context, uri, defaultIfImage = 3000L)
                val name = MediaUtils.getFileName(context, uri)
                MediaClip(
                    uri = uri.toString(),
                    name = name,
                    isImage = isImg,
                    sourceDurationMs = if (isImg) 15000L else dur,
                    trimStartMs = 0L,
                    trimEndMs = if (isImg) 3000L else dur
                )
            }
            val updated = project.clips + newClips
            project = project.copy(clips = updated)
            selectedClipIndex = updated.lastIndex
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            MediaUtils.persistUriPermission(context, uri)
            val dur = MediaUtils.getMediaDurationMs(context, uri, defaultIfImage = 0L)
            val name = MediaUtils.getFileName(context, uri)
            project = project.copy(
                audioUri = uri.toString(),
                audioName = name,
                audioDurationMs = dur,
                audioTrimStartMs = 0L
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Barra superior minimalista
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LyricReel",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Fase 1 · Autoguardado activo",
                color = Color.Gray,
                fontSize = 11.sp
            )
        }

        // Vista previa central 9:16
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            ReelPreviewPlayer(
                activeClip = activeClip,
                localClipMs = localClipMs,
                currentTimelineMs = currentTimelineMs,
                isPlaying = isPlaying,
                audioUri = project.audioUri,
                audioTrimStartMs = project.audioTrimStartMs,
                muteOriginalAudio = project.muteOriginalAudio,
                onUpdateClipTransform = { newScale, newOffsetX, newOffsetY ->
                    val idx = activeClipIndexFromPlayhead
                    if (idx in project.clips.indices) {
                        val updated = project.clips.toMutableList()
                        updated[idx] = updated[idx].copy(
                            scale = newScale,
                            offsetX = newOffsetX,
                            offsetY = newOffsetY
                        )
                        project = project.copy(clips = updated)
                    }
                }
            )
        }

        // Línea de tiempo y controles
        TimelineEditor(
            project = project,
            selectedClipIndex = selectedClipIndex,
            currentTimelineMs = currentTimelineMs,
            isPlaying = isPlaying,
            onTogglePlay = {
                if (project.clips.isNotEmpty()) {
                    isPlaying = !isPlaying
                } else {
                    Toast.makeText(context, "Agrega al menos un video o foto", Toast.LENGTH_SHORT).show()
                }
            },
            onScrubTimeline = { targetMs ->
                isPlaying = false
                currentTimelineMs = targetMs.coerceIn(0L, project.totalDurationMs)
            },
            onSelectClip = { idx ->
                isPlaying = false
                selectedClipIndex = idx
                var acc = 0L
                for (i in 0 until idx) {
                    acc += project.clips[i].effectiveDurationMs
                }
                currentTimelineMs = acc
            },
            onAddClipClick = {
                isPlaying = false
                mediaPickerLauncher.launch(arrayOf("video/*", "image/*"))
            },
            onAddAudioClick = {
                isPlaying = false
                audioPickerLauncher.launch(arrayOf("audio/*"))
            },
            onSplitClip = {
                isPlaying = false
                val idx = activeClipIndexFromPlayhead
                val clip = project.clips.getOrNull(idx)
                if (clip != null) {
                    val splitPoint = localClipMs
                    if (splitPoint > 300L && splitPoint < clip.effectiveDurationMs - 300L) {
                        val firstHalf = clip.copy(
                            trimEndMs = clip.trimStartMs + splitPoint
                        )
                        val secondHalf = clip.copy(
                            id = java.util.UUID.randomUUID().toString(),
                            trimStartMs = clip.trimStartMs + splitPoint
                        )
                        val updated = project.clips.toMutableList()
                        updated.removeAt(idx)
                        updated.add(idx, firstHalf)
                        updated.add(idx + 1, secondHalf)
                        project = project.copy(clips = updated)
                        selectedClipIndex = idx + 1
                        Toast.makeText(context, "Clip dividido", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Mueve el cabezal hacia el centro del clip para dividir", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onDeleteSelectedClip = {
                if (selectedClipIndex in project.clips.indices) {
                    isPlaying = false
                    val updated = project.clips.toMutableList()
                    updated.removeAt(selectedClipIndex)
                    project = project.copy(clips = updated)
                    selectedClipIndex = (selectedClipIndex - 1).coerceAtLeast(0)
                    currentTimelineMs = 0L
                }
            },
            onMoveClip = { from, to ->
                if (from in project.clips.indices && to in project.clips.indices) {
                    isPlaying = false
                    val updated = project.clips.toMutableList()
                    val item = updated.removeAt(from)
                    updated.add(to, item)
                    project = project.copy(clips = updated)
                    selectedClipIndex = to
                }
            },
            onTrimSelectedClip = { newStart, newEnd ->
                if (selectedClipIndex in project.clips.indices) {
                    isPlaying = false
                    val updated = project.clips.toMutableList()
                    updated[selectedClipIndex] = updated[selectedClipIndex].copy(
                        trimStartMs = newStart,
                        trimEndMs = newEnd
                    )
                    project = project.copy(clips = updated)
                }
            },
            onToggleFillScreen = {
                if (selectedClipIndex in project.clips.indices) {
                    val updated = project.clips.toMutableList()
                    val cur = updated[selectedClipIndex]
                    updated[selectedClipIndex] = cur.copy(fillScreen = !cur.fillScreen)
                    project = project.copy(clips = updated)
                }
            },
            onResetTransform = {
                if (selectedClipIndex in project.clips.indices) {
                    val updated = project.clips.toMutableList()
                    updated[selectedClipIndex] = updated[selectedClipIndex].copy(
                        scale = 1f,
                        offsetX = 0f,
                        offsetY = 0f
                    )
                    project = project.copy(clips = updated)
                }
            },
            onToggleMuteOriginal = {
                project = project.copy(muteOriginalAudio = !project.muteOriginalAudio)
            },
            onUpdateAudioStart = { newAudioStart ->
                isPlaying = false
                project = project.copy(audioTrimStartMs = newAudioStart)
            },
            onRemoveAudio = {
                isPlaying = false
                project = project.copy(
                    audioUri = null,
                    audioName = null,
                    audioDurationMs = 0L,
                    audioTrimStartMs = 0L
                )
            }
        )
    }
}
