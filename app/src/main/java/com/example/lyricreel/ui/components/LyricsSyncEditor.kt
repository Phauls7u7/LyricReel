package com.example.lyricreel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lyricreel.media.MediaUtils
import com.example.lyricreel.model.LyricLine

@Composable
fun LyricsSyncEditor(
    lyrics: List<LyricLine>,
    activeLyricIndex: Int,
    currentTimelineMs: Long,
    totalDurationMs: Long,
    isPlaying: Boolean,
    playbackSpeed: Float,
    canUndo: Boolean,
    onTogglePlay: () -> Unit,
    onScrubTimeline: (Long) -> Unit,
    onToggleSpeed: () -> Unit,
    onSavePastedLyrics: (String) -> Unit,
    onTapSyncNext: () -> Unit,
    onUndo: () -> Unit,
    onNudgeLineTime: (index: Int, deltaMs: Long) -> Unit,
    onJumpToLine: (index: Int) -> Unit,
    onOpenStyleEditor: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val totalMs = totalDurationMs.coerceAtLeast(1L)
    var showPasteDialog by remember { mutableStateOf(false) }
    var draftLyrics by remember(lyrics) { mutableStateOf(lyrics.joinToString("\n") { it.text }) }

    val nextUnsyncedIndex = remember(lyrics) { lyrics.indexOfFirst { it.startTimeMs == null } }
    val listState = rememberLazyListState()

    LaunchedEffect(activeLyricIndex) {
        if (activeLyricIndex in lyrics.indices) {
            listState.animateScrollToItem(activeLyricIndex)
        }
    }

    if (showPasteDialog) {
        AlertDialog(
            onDismissRequest = { showPasteDialog = false },
            containerColor = Color(0xFF141414),
            title = { Text("Pegar letra", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = draftLyrics,
                    onValueChange = { draftLyrics = it },
                    modifier = Modifier.fillMaxWidth().height(220.dp)
                )
            },
            confirmButton = {
                Button(
                    onClick = { onSavePastedLyrics(draftLyrics); showPasteDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { showPasteDialog = false }) { Text("Cancelar", color = Color.Gray) }
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF080808)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier.size(38.dp).background(accent, RoundedCornerShape(19.dp))
            ) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text("${MediaUtils.formatTimeMs(currentTimelineMs)} / ${MediaUtils.formatTimeMs(totalDurationMs)}", color = Color.White, fontSize = 12.sp)
            Spacer(modifier = Modifier.weight(1f))

            OutlinedButton(onClick = onToggleSpeed, shape = RoundedCornerShape(8.dp), modifier = Modifier.height(34.dp)) {
                Text(if (playbackSpeed < 1f) "0.5x" else "1.0x", color = if (playbackSpeed < 1f) accent else Color.White, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.width(6.dp))
            
            // Botón Estilo
            FilledTonalButton(onClick = onOpenStyleEditor, shape = RoundedCornerShape(8.dp), modifier = Modifier.height(34.dp)) {
                Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Estilo", fontSize = 11.sp)
            }
            Spacer(modifier = Modifier.width(6.dp))
            FilledTonalButton(onClick = { showPasteDialog = true }, shape = RoundedCornerShape(8.dp), modifier = Modifier.height(34.dp)) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
            }
        }

        Slider(
            value = (currentTimelineMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f),
            onValueChange = { ratio -> onScrubTimeline((ratio * totalMs).toLong()) },
            colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = Color(0xFF262626)),
            modifier = Modifier.fillMaxWidth().height(22.dp)
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onTapSyncNext,
                enabled = lyrics.isNotEmpty() && nextUnsyncedIndex != -1,
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black, disabledContainerColor = Color(0xFF1F1F1F)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).height(44.dp)
            ) {
                Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                val lbl = if (lyrics.isEmpty()) "Pega la letra" else if (nextUnsyncedIndex == -1) "Listo" else "Marcar línea ${nextUnsyncedIndex + 1}"
                Text(lbl, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onUndo, enabled = canUndo, shape = RoundedCornerShape(10.dp), modifier = Modifier.height(44.dp)) {
                Icon(Icons.Default.Undo, contentDescription = null, tint = if (canUndo) Color.White else Color.DarkGray, modifier = Modifier.size(18.dp))
            }
        }

        if (lyrics.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(110.dp).background(Color(0xFF111111), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Text("Toca el icono del lápiz arriba para añadir letra", color = Color.Gray, fontSize = 12.sp)
            }
        } else {
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().height(135.dp)) {
                itemsIndexed(lyrics, key = { _, item -> item.id }) { index, line ->
                    val isActive = index == activeLyricIndex
                    val isNextToMark = index == nextUnsyncedIndex
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(if (isActive) Color(0xFF153238) else if (isNextToMark) Color(0xFF1B1B1B) else Color(0xFF111111))
                            .border(if (isActive || isNextToMark) 1.dp else 0.dp, if (isActive) accent else Color(0xFF3A3A3A), RoundedCornerShape(8.dp))
                            .clickable { onJumpToLine(index) }.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${index + 1}. ${line.text}", color = if (isActive) accent else Color.White, fontSize = 12.sp, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(line.startTimeMs?.let { MediaUtils.formatTimeMs(it) } ?: "Sin marcar", color = if (line.startTimeMs != null) Color.LightGray else Color.Gray, fontSize = 10.sp)
                        }
                        if (line.startTimeMs != null) {
                            TextButton(onClick = { onNudgeLineTime(index, -100L) }, modifier = Modifier.height(30.dp)) { Text("-0.1s", color = accent, fontSize = 11.sp) }
                            TextButton(onClick = { onNudgeLineTime(index, 100L) }, modifier = Modifier.height(30.dp)) { Text("+0.1s", color = accent, fontSize = 11.sp) }
                        }
                    }
                }
            }
        }
    }
}
