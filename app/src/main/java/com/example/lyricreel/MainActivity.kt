package com.example.lyricreel

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.lyricreel.ui.components.LyricStyleDialog
import com.example.lyricreel.ui.components.LyricsSyncEditor
import com.example.lyricreel.ui.components.ReelPreviewPlayer
import com.example.lyricreel.ui.components.TimelineEditor
import com.example.lyricreel.ui.theme.LyricReelTheme
import kotlinx.coroutines.delay
import java.util.UUID

enum class EditorTab { TIMELINE, LYRICS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LyricReelTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Phase3EditorScreen()
                }
            }
        }
    }
}

@Composable
fun Phase3EditorScreen() {
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
    var showStyleDialog by remember { mutableStateOf(false) }

    val lyricsUndoStack = remember { mutableStateListOf<List<LyricLine>>() }

    fun updateLyricsWithHistory(newLyrics: List<LyricLine>) {
        lyricsUndoStack.add(project.lyrics)
        if (lyricsUndoStack.size > 40) lyricsUndoStack.removeAt(0)
        project = project.copy(lyrics = newLyrics)
    }

    LaunchedEffect(project) { storage.saveProject(project) }

    LaunchedEffect(project.clips.size) {
        if (project.clips.isEmpty()) { selectedClipIndex = 0; currentTimelineMs = 0L; isPlaying = false }
        else if (selectedClipIndex > project.clips.lastIndex) selectedClipIndex = project.clips.lastIndex
    }

    LaunchedEffect(isPlaying, project.totalDurationMs, playbackSpeed) {
        if (isPlaying) {
            val total = project.totalDurationMs
            if (total <= 0L) { isPlaying = false; return@LaunchedEffect }
            val tickMs = 30L
            while (isPlaying) {
                delay(tickMs)
                val next = currentTimelineMs + (tickMs * playbackSpeed).toLong().coerceAtLeast(1L)
                if (next >= total) { currentTimelineMs = 0L; isPlaying = false }
                else currentTimelineMs = next
            }
        }
    }

    val activeClipInfo = remember(project.clips, currentTimelineMs) {
        var acc = 0L
        var found: MediaClip? = null
        var foundIdx = 0
        var localOffset = 0L
        for ((idx, clip) in project.clips.withIndex()) {
            val dur = clip.effectiveDurationMs
            if (currentTimelineMs < acc + dur || idx == project.clips.lastIndex) {
                found = clip; foundIdx = idx; localOffset = (currentTimelineMs - acc).coerceIn(0L, dur); break
            }
            acc += dur
        }
        Triple(found, foundIdx, localOffset)
    }
    val activeClip = activeClipInfo.first
    val activeClipIdx = activeClipInfo.second
    val localClipMs = activeClipInfo.third

    LaunchedEffect(activeClipIdx, isPlaying) {
        if (isPlaying && project.clips.isNotEmpty()) selectedClipIndex = activeClipIdx
    }

    val activeLyricIndex = remember(project.lyrics, currentTimelineMs) {
        var found = -1
        for (i in project.lyrics.indices) {
            val start = project.lyrics[i].startTimeMs
            if (start != null && currentTimelineMs >= start) found = i
        }
        found
    }

    val activeLyricText = remember(project.lyrics, activeLyricIndex, touchTarget) {
        if (activeLyricIndex in project.lyrics.indices) project.lyrics[activeLyricIndex].text
        else if (touchTarget == CanvasTouchTarget.LYRICS && project.lyrics.isNotEmpty()) project.lyrics.first().text
        else null
    }

    val activeLyricStartTime = remember(project.lyrics, activeLyricIndex, touchTarget) {
        if (activeLyricIndex in project.lyrics.indices) project.lyrics[activeLyricIndex].startTimeMs ?: currentTimelineMs
        else currentTimelineMs
    }

    if (showStyleDialog) {
        LyricStyleDialog(
            project = project,
            onUpdateStyle = { project = it },
            onDismiss = { showStyleDialog = false }
        )
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

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = touchTarget == CanvasTouchTarget.MEDIA, onClick = { touchTarget = CanvasTouchTarget.MEDIA }, label = { Text("Fondo", fontSize = 11.sp) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent, selectedLabelColor = Color.Black, labelColor = Color.White), modifier = Modifier.height(28.dp))
                Spacer(modifier = Modifier.width(6.dp))
                FilterChip(selected = touchTarget == CanvasTouchTarget.LYRICS, onClick = { touchTarget = CanvasTouchTarget.LYRICS }, label = { Text("Letra", fontSize = 11.sp) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent, selectedLabelColor = Color.Black, labelColor = Color.White), modifier = Modifier.height(28.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if(touchTarget == CanvasTouchTarget.MEDIA && activeClipIdx in project.clips.indices) { val m = project.clips.toMutableList(); m[activeClipIdx] = m[activeClipIdx].copy(scale = (m[activeClipIdx].scale - 0.15f).coerceIn(0.4f, 4.5f)); project = project.copy(clips = m) } else project = project.copy(textScale = (project.textScale - 0.15f).coerceIn(0.5f, 3.0f)) }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.ZoomOut, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                IconButton(onClick = { if(touchTarget == CanvasTouchTarget.MEDIA && activeClipIdx in project.clips.indices) { val m = project.clips.toMutableList(); m[activeClipIdx] = m[activeClipIdx].copy(scale = (m[activeClipIdx].scale + 0.15f).coerceIn(0.4f, 4.5f)); project = project.copy(clips = m) } else project = project.copy(textScale = (project.textScale + 0.15f).coerceIn(0.5f, 3.0f)) }, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.ZoomIn, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp)) }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 2.dp), contentAlignment = Alignment.Center) {
            ReelPreviewPlayer(
                activeClip = activeClip, localClipMs = localClipMs, currentTimelineMs = currentTimelineMs,
                isPlaying = isPlaying, playbackSpeed = playbackSpeed, project = project,
                activeLyricText = activeLyricText, activeLyricStartTime = activeLyricStartTime, touchTarget = touchTarget,
                onUpdateClipTransform = { s, x, y -> val m = project.clips.toMutableList(); if (activeClipIdx in m.indices) { m[activeClipIdx] = m[activeClipIdx].copy(scale = s, offsetX = x, offsetY = y); project = project.copy(clips = m) } },
                onUpdateTextTransform = { x, y, s -> project = project.copy(textXRatio = x, textYRatio = y, textScale = s) }
            )
        }

        Row(modifier = Modifier.fillMaxWidth().background(Color(0xFF0E0E0E)).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = activeTab == EditorTab.TIMELINE, onClick = { activeTab = EditorTab.TIMELINE; touchTarget = CanvasTouchTarget.MEDIA }, label = { Text("1. Clips", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent, selectedLabelColor = Color.Black, labelColor = Color.White), shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f))
            FilterChip(selected = activeTab == EditorTab.LYRICS, onClick = { activeTab = EditorTab.LYRICS; touchTarget = CanvasTouchTarget.LYRICS }, label = { Text("2. Letras", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent, selectedLabelColor = Color.Black, labelColor = Color.White), shape = RoundedCornerShape(8.dp), modifier = Modifier.weight(1f))
        }

        when (activeTab) {
            EditorTab.TIMELINE -> {
                TimelineEditor(
                    project = project, selectedClipIndex = selectedClipIndex, currentTimelineMs = currentTimelineMs, isPlaying = isPlaying,
                    onTogglePlay = { if (project.clips.isNotEmpty()) isPlaying = !isPlaying else Toast.makeText(context, "Agrega al menos un video o foto", Toast.LENGTH_SHORT).show() }, 
                    onScrubTimeline = { currentTimelineMs = it; isPlaying = false },
                    onSelectClip = { idx -> isPlaying = false; selectedClipIndex = idx; var acc = 0L; for (i in 0 until idx) acc += project.clips[i].effectiveDurationMs; currentTimelineMs = acc }, 
                    onAddClipClick = { isPlaying = false; mediaPickerLauncher.launch(arrayOf("video/*", "image/*")) }, 
                    onAddAudioClick = { isPlaying = false; audioPickerLauncher.launch(arrayOf("audio/*")) }, 
                    onSplitClip = { 
                        isPlaying = false
                        val clip = project.clips.getOrNull(activeClipIdx)
                        if (clip != null && localClipMs > 300L && localClipMs < clip.effectiveDurationMs - 300L) {
                            val firstHalf = clip.copy(trimEndMs = clip.trimStartMs + localClipMs)
                            val secondHalf = clip.copy(id = UUID.randomUUID().toString(), trimStartMs = clip.trimStartMs + localClipMs)
                            val updated = project.clips.toMutableList()
                            updated.removeAt(activeClipIdx)
                            updated.add(activeClipIdx, firstHalf)
                            updated.add(activeClipIdx + 1, secondHalf)
                            project = project.copy(clips = updated)
                            selectedClipIndex = activeClipIdx + 1
                            Toast.makeText(context, "Clip dividido", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Mueve el cabezal hacia el centro del clip para dividir", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDeleteSelectedClip = { if (selectedClipIndex in project.clips.indices) { isPlaying = false; val updated = project.clips.toMutableList(); updated.removeAt(selectedClipIndex); project = project.copy(clips = updated); selectedClipIndex = (selectedClipIndex - 1).coerceAtLeast(0); currentTimelineMs = 0L } }, 
                    onMoveClip = { from, to -> if (from in project.clips.indices && to in project.clips.indices) { isPlaying = false; val updated = project.clips.toMutableList(); val item = updated.removeAt(from); updated.add(to, item); project = project.copy(clips = updated); selectedClipIndex = to } }, 
                    onTrimSelectedClip = { start, end -> if (selectedClipIndex in project.clips.indices) { isPlaying = false; val updated = project.clips.toMutableList(); updated[selectedClipIndex] = updated[selectedClipIndex].copy(trimStartMs = start, trimEndMs = end); project = project.copy(clips = updated) } }, 
                    onToggleFillScreen = { if (selectedClipIndex in project.clips.indices) { val updated = project.clips.toMutableList(); updated[selectedClipIndex] = updated[selectedClipIndex].copy(fillScreen = !updated[selectedClipIndex].fillScreen); project = project.copy(clips = updated) } },
                    onResetTransform = { if (selectedClipIndex in project.clips.indices) { val updated = project.clips.toMutableList(); updated[selectedClipIndex] = updated[selectedClipIndex].copy(scale = 1f, offsetX = 0f, offsetY = 0f); project = project.copy(clips = updated) } }, 
                    onToggleMuteOriginal = { project = project.copy(muteOriginalAudio = !project.muteOriginalAudio) }, 
                    onUpdateAudioStart = { isPlaying = false; project = project.copy(audioTrimStartMs = it) }, 
                    onRemoveAudio = { isPlaying = false; project = project.copy(audioUri = null, audioName = null, audioDurationMs = 0L, audioTrimStartMs = 0L) }
                )
            }
            EditorTab.LYRICS -> {
                LyricsSyncEditor(
                    lyrics = project.lyrics, activeLyricIndex = activeLyricIndex, currentTimelineMs = currentTimelineMs, totalDurationMs = project.totalDurationMs,
                    isPlaying = isPlaying, playbackSpeed = playbackSpeed, canUndo = lyricsUndoStack.isNotEmpty(),
                    onTogglePlay = { if (project.clips.isNotEmpty()) isPlaying = !isPlaying else Toast.makeText(context, "Agrega al menos un video o foto primero", Toast.LENGTH_SHORT).show() }, 
                    onScrubTimeline = { currentTimelineMs = it; isPlaying = false }, 
                    onToggleSpeed = { playbackSpeed = if (playbackSpeed == 1.0f) 0.5f else 1.0f },
                    onSavePastedLyrics = { raw -> 
                        val newLines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }.mapIndexed { idx, t -> 
                            LyricLine(text = t, startTimeMs = project.lyrics.getOrNull(idx)?.takeIf { it.text == t }?.startTimeMs) 
                        }
                        updateLyricsWithHistory(newLines) 
                    },
                    onTapSyncNext = { val n = project.lyrics.indexOfFirst { it.startTimeMs == null }; if (n != -1) { val m = project.lyrics.toMutableList(); m[n] = m[n].copy(startTimeMs = currentTimelineMs); updateLyricsWithHistory(m) } },
                    onUndo = { if (lyricsUndoStack.isNotEmpty()) { project = project.copy(lyrics = lyricsUndoStack.removeAt(lyricsUndoStack.lastIndex)) } },
                    onNudgeLineTime = { idx, delta -> val cur = project.lyrics[idx].startTimeMs; if (cur != null) { val m = project.lyrics.toMutableList(); m[idx] = m[idx].copy(startTimeMs = (cur + delta).coerceIn(0L, project.totalDurationMs)); updateLyricsWithHistory(m); if (!isPlaying) currentTimelineMs = m[idx].startTimeMs!! } },
                    onJumpToLine = { project.lyrics[it].startTimeMs?.let { t -> currentTimelineMs = t; isPlaying = false } },
                    onOpenStyleEditor = { showStyleDialog = true }
                )
            }
        }
    }
}
