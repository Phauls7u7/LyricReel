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
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lyricreel.data.ProjectStorage
import com.example.lyricreel.media.ExportManager
import com.example.lyricreel.media.MediaUtils
import com.example.lyricreel.model.LyricLine
import com.example.lyricreel.model.MediaClip
import com.example.lyricreel.ui.components.*
import com.example.lyricreel.ui.theme.LyricReelTheme
import kotlinx.coroutines.delay
import java.util.UUID

enum class EditorTab { TIMELINE, LYRICS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LyricReelTheme { Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Phase4EditorScreen() } } }
    }
}

@Composable
fun Phase4EditorScreen() {
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
    var isExporting by remember { mutableStateOf(false) }

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
                if (next >= total) { currentTimelineMs = 0L; isPlaying = false } else currentTimelineMs = next
            }
        }
    }
