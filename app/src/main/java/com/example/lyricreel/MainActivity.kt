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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.example.lyricreel.model.LyricLine
import com.example.lyricreel.model.MediaClip
import com.example.lyricreel.ui.components.CanvasTouchTarget
import com.example.lyricreel.ui.components.LyricsSyncEditor
import com.example.lyricreel.ui.components.ReelPreviewPlayer
import com.example.lyricreel.ui.components.TimelineEditor
import com.example.lyricreel.ui.theme.LyricReelTheme
import kotlinx.coroutines.delay
import java.util.UUID

enum class EditorTab {
    TIMELINE,
    LYRICS
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LyricReelTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Phase2EditorScreen()
                }
            }
        }
    }
}

@Composable
fun Phase2EditorScreen() {
    val context = LocalContext.current
    val storage = remember { ProjectStorage(context) }
    val accent = MaterialTheme.colorScheme.primary

    var project by remember { mutableStateOf(storage.loadProject()) }
    var selectedClipIndex by remember { mutableIntStateOf(0) }
    var currentTimelineMs by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }

    var activeTab by remember { mutableStateOf(EditorTab.TIMELINE) }
    var touchTarget by remember { mutableStateOf(CanvasTouchTarget.MEDIA) }

    // Autoguardado
    LaunchedEffect(project) {
        storage.saveProject(project)
    }

    // Validar índice seleccionado
    LaunchedEffect(project.clips.size) {
        if (project.clips.isEmpty()) {
            selectedClipIndex = 0
            currentTimelineMs = 0L
            isPlaying = false
        } else if (selectedClipIndex > project.clips.lastIndex) {
            selectedClipIndex = project.clips.lastIndex
        }
    }

    // Reloj maestro respetando velocidad 1.0x o 0.5x
    LaunchedEffect(isPlaying, project.totalDurationMs, playbackSpeed) {
        if (isPlaying) {
            val total = project.totalDurationMs
            if (total <= 0L) {
                isPlaying = false
                return@LaunchedEffect
            }
            val tickMs = 30L
            while (isPlaying) {
                delay(tickMs)
                val advance = (tickMs * playbackSpeed).toLong().coerceAtLeast(1L)
                val next = currentTimelineMs + advance
                if (next >= total) {
                    currentTimelineMs = 0L
                    isPlaying = false
                } else {
                    currentTimelineMs = next
                }
            }
        }
    }

    // Clip activo en la línea de tiempo
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

    // Línea de letra activa según el tiempo actual
    val activeLyricIndex = remember(project.lyrics, currentTimelineMs) {
        var found = -1
        for (i in project.lyrics.indices) {
            val start = project.lyrics[i].startTimeMs
            if (start != null && currentTimelineMs >= start) {
                found = i
            }
        }
        found
    }

    val activeLyricText = remember(project.lyrics, activeLyricIndex, activeTab, touchTarget) {
        if (activeLyricIndex in project.lyrics.indices) {
            project.lyrics[activeLyricIndex].text
        } else if (touchTarget == CanvasTouchTarget.LYRICS && project.lyrics.isNotEmpty()) {
            // Muestra la primera línea como guía mientras acomodas el texto en pantalla
            project.lyrics.first().text
        } else {
            null
        }
    }

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
        // Barra superior con selector táctil (Mover Fondo vs Mover Letra)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LyricReel",
                color = accent,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = touchTarget == CanvasTouchTarget.MEDIA,
                    onClick = { touchTarget = CanvasTouchTarget.MEDIA },
                    label = { Text("Mover Fondo", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accent,
                        selectedLabelColor = Color.Black,
                        labelColor = Color.White
                    ),
                    modifier = Modifier.height(28.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                FilterChip(
                    selected = touchTarget == CanvasTouchTarget.LYRICS,
                    onClick = { touchTarget = CanvasTouchTarget.LYRICS },
                    label = { Text("Mover Letra", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accent,
                        selectedLabelColor = Color.Black,
                        labelColor = Color.White
                    ),
                    modifier = Modifier.height(28.dp)
                )
            }
        }

        // Vista previa central 9:16
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            ReelPreviewPlayer(
                activeClip = activeClip,
                localClipMs = localClipMs,
                currentTimelineMs = currentTimelineMs,
                isPlaying = isPlaying,
                playbackSpeed = playbackSpeed,
                audioUri = project.audioUri,
                audioTrimStartMs = project.audioTrimStartMs,
                muteOriginalAudio = project.muteOriginalAudio,
                activeLyricText = activeLyricText,
                textXRatio = project.textXRatio,
                textYRatio = project.textYRatio,
                textScale = project.textScale,
                touchTarget = touchTarget,
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
                },
                onUpdateTextTransform = { newX, newY, newScale ->
                    project = project.copy(
                        textXRatio = newX,
                        textYRatio = newY,
                        textScale = newScale
                    )
                }
            )
        }

        // Selector de pestaña de edición (Clips/Audio vs Letras)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0E0E0E))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = activeTab == EditorTab.TIMELINE,
                onClick = {
                    activeTab = EditorTab.TIMELINE
                    touchTarget = CanvasTouchTarget.MEDIA
                },
                label = { Text("1. Clips y Canción", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accent,
                    selectedLabelColor = Color.Black,
                    labelColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = activeTab == EditorTab.LYRICS,
                onClick = {
                    activeTab = EditorTab.LYRICS
                    touchTarget = CanvasTouchTarget.LYRICS
                },
                label = { Text("2. Letras (Tap-to-Sync)", fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accent,
                    selectedLabelColor = Color.Black,
                    labelColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.weight(1f)
            )
        }

        // Panel inferior dinámico según pestaña activa
        when (activeTab) {
            EditorTab.TIMELINE -> {
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
                                val firstHalf = clip.copy(trimEndMs = clip.trimStartMs + splitPoint)
                                val secondHalf = clip.copy(
                                    id = UUID.randomUUID().toString(),
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

            EditorTab.LYRICS -> {
                LyricsSyncEditor(
                    lyrics = project.lyrics,
                    activeLyricIndex = activeLyricIndex,
                    currentTimelineMs = currentTimelineMs,
                    totalDurationMs = project.totalDurationMs,
                    isPlaying = isPlaying,
                    playbackSpeed = playbackSpeed,
                    onTogglePlay = {
                        if (project.clips.isNotEmpty()) {
                            isPlaying = !isPlaying
                        } else {
                            Toast.makeText(context, "Agrega al menos un video o foto primero", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onScrubTimeline = { targetMs ->
                        isPlaying = false
                        currentTimelineMs = targetMs.coerceIn(0L, project.totalDurationMs)
                    },
                    onToggleSpeed = {
                        playbackSpeed = if (playbackSpeed == 1.0f) 0.5f else 1.0f
                    },
                    onSavePastedLyrics = { rawText ->
                        val lines = rawText.lines()
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .mapIndexed { idx, text ->
                                val existingTime = project.lyrics.getOrNull(idx)?.takeIf { it.text == text }?.startTimeMs
                                LyricLine(text = text, startTimeMs = existingTime)
                            }
                        project = project.copy(lyrics = lines)
                    },
                    onTapSyncNext = {
                        val nextIdx = project.lyrics.indexOfFirst { it.startTimeMs == null }
                        if (nextIdx != -1) {
                            val updated = project.lyrics.toMutableList()
                            updated[nextIdx] = updated[nextIdx].copy(startTimeMs = currentTimelineMs)
                            project = project.copy(lyrics = updated)
                        }
                    },
                    onUndoLastSync = {
                        val lastSyncedIdx = project.lyrics.indexOfLast { it.startTimeMs != null }
                        if (lastSyncedIdx != -1) {
                            val updated = project.lyrics.toMutableList()
                            val prevTime = updated[lastSyncedIdx].startTimeMs ?: 0L
                            updated[lastSyncedIdx] = updated[lastSyncedIdx].copy(startTimeMs = null)
                            project = project.copy(lyrics = updated)
                            currentTimelineMs = prevTime
                        }
                    },
                    onNudgeLineTime = { index, deltaMs ->
                        if (index in project.lyrics.indices) {
                            val curTime = project.lyrics[index].startTimeMs ?: return@LyricsSyncEditor
                            val newTime = (curTime + deltaMs).coerceIn(0L, project.totalDurationMs)
                            val updated = project.lyrics.toMutableList()
                            updated[index] = updated[index].copy(startTimeMs = newTime)
                            project = project.copy(lyrics = updated)
                            if (!isPlaying) currentTimelineMs = newTime
                        }
                    },
                    onJumpToLine = { index ->
                        val startTime = project.lyrics.getOrNull(index)?.startTimeMs
                        if (startTime != null) {
                            isPlaying = false
                            currentTimelineMs = startTime
                        }
                    }
                )
            }
        }
    }
}
