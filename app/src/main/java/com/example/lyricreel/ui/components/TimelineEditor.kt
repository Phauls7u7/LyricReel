package com.example.lyricreel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lyricreel.media.MediaUtils
import com.example.lyricreel.model.MediaClip
import com.example.lyricreel.model.ReelProject

@Composable
fun TimelineEditor(
    project: ReelProject,
    selectedClipIndex: Int,
    currentTimelineMs: Long,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    onScrubTimeline: (Long) -> Unit,
    onSelectClip: (Int) -> Unit,
    onAddClipClick: () -> Unit,
    onAddAudioClick: () -> Unit,
    onSplitClip: () -> Unit,
    onDeleteSelectedClip: () -> Unit,
    onMoveClip: (fromIndex: Int, toIndex: Int) -> Unit,
    onTrimSelectedClip: (newStartMs: Long, newEndMs: Long) -> Unit,
    onToggleFillScreen: () -> Unit,
    onResetTransform: () -> Unit,
    onToggleMuteOriginal: () -> Unit,
    onUpdateAudioStart: (Long) -> Unit,
    onRemoveAudio: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val totalMs = project.totalDurationMs.coerceAtLeast(1L)
    val selectedClip = project.clips.getOrNull(selectedClipIndex)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF080808))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 1. Barra de reproducción y tiempo
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier
                    .size(40.dp)
                    .background(accent, RoundedCornerShape(20.dp))
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Reproducir o Pausar",
                    tint = Color.Black
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "${MediaUtils.formatTimeMs(currentTimelineMs)} / ${MediaUtils.formatTimeMs(project.totalDurationMs)}",
                color = Color.White,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = onToggleMuteOriginal, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = if (project.muteOriginalAudio) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = "Audio original del video",
                    tint = if (project.muteOriginalAudio) Color.Gray else accent,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = onSplitClip, enabled = selectedClip != null, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Default.ContentCut,
                    contentDescription = "Dividir clip",
                    tint = if (selectedClip != null) accent else Color.DarkGray,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = onToggleFillScreen, enabled = selectedClip != null, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Default.AspectRatio,
                    contentDescription = "Ajustar o llenar 9:16",
                    tint = if (selectedClip != null) Color.White else Color.DarkGray,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(onClick = onResetTransform, enabled = selectedClip != null, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Resetear zoom",
                    tint = if (selectedClip != null) Color.White else Color.DarkGray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 2. Scrubber general de la línea de tiempo
        Slider(
            value = (currentTimelineMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f),
            onValueChange = { ratio -> onScrubTimeline((ratio * totalMs).toLong()) },
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = Color(0xFF262626)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
        )

        // 3. Pista de clips (Videos / Fotos) + Botón Agregar
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(project.clips, key = { _, clip -> clip.id }) { index, clip ->
                val isSelected = index == selectedClipIndex
                Box(
                    modifier = Modifier
                        .width(108.dp)
                        .height(50.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Color(0xFF142A2E) else Color(0xFF161616))
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) accent else Color(0xFF2E2E2E),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelectClip(index) }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Column(verticalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = "${if (clip.isImage) "IMG" else "VID"} · ${clip.name}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = MediaUtils.formatTimeMs(clip.effectiveDurationMs),
                            color = if (isSelected) accent else Color.Gray,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            item {
                FilledTonalButton(
                    onClick = onAddClipClick,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(50.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clip", fontSize = 12.sp)
                }
            }
        }

        // 4. Controles de recorte (Trim) y orden del clip seleccionado
        if (selectedClip != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111111), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedClip.isImage) {
                            "Duración foto: ${MediaUtils.formatTimeMs(selectedClip.effectiveDurationMs)}"
                        } else {
                            "Recorte: ${MediaUtils.formatTimeMs(selectedClip.trimStartMs)} - ${MediaUtils.formatTimeMs(selectedClip.trimEndMs)}"
                        },
                        color = accent,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(
                        onClick = { onMoveClip(selectedClipIndex, selectedClipIndex - 1) },
                        enabled = selectedClipIndex > 0,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowLeft,
                            contentDescription = "Mover izquierda",
                            tint = if (selectedClipIndex > 0) Color.White else Color.DarkGray
                        )
                    }
                    IconButton(
                        onClick = { onMoveClip(selectedClipIndex, selectedClipIndex + 1) },
                        enabled = selectedClipIndex < project.clips.lastIndex,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowRight,
                            contentDescription = "Mover derecha",
                            tint = if (selectedClipIndex < project.clips.lastIndex) Color.White else Color.DarkGray
                        )
                    }
                    IconButton(
                        onClick = onDeleteSelectedClip,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Eliminar clip",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (selectedClip.isImage) {
                    val maxPhotoMs = 15000f
                    Slider(
                        value = selectedClip.effectiveDurationMs.toFloat().coerceIn(500f, maxPhotoMs),
                        onValueChange = { newDur ->
                            onTrimSelectedClip(0L, newDur.toLong())
                        },
                        valueRange = 500f..maxPhotoMs,
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
                        modifier = Modifier.height(24.dp)
                    )
                } else {
                    val maxDur = selectedClip.sourceDurationMs.coerceAtLeast(500L).toFloat()
                    RangeSlider(
                        value = selectedClip.trimStartMs.toFloat()..selectedClip.trimEndMs.toFloat(),
                        onValueChange = { range ->
                            val start = range.start.toLong()
                            val end = range.endInclusive.toLong()
                            if (end - start >= 300L) {
                                onTrimSelectedClip(start, end)
                            }
                        },
                        valueRange = 0f..maxDur,
                        colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
                        modifier = Modifier.height(24.dp)
                    )
                }
            }
        }

        // 5. Pista de Canción
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF111111), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            if (project.audioUri == null) {
                Text(
                    text = "Sin canción agregada",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(onClick = onAddAudioClick, shape = RoundedCornerShape(8.dp)) {
                    Text("+ Canción", fontSize = 12.sp)
                }
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.audioName ?: "Canción",
                        color = Color.White,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (project.audioDurationMs > 1000L) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Inicio: ${MediaUtils.formatTimeMs(project.audioTrimStartMs)}",
                                color = accent,
                                fontSize = 10.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Slider(
                                value = project.audioTrimStartMs.toFloat().coerceIn(0f, project.audioDurationMs.toFloat()),
                                onValueChange = { onUpdateAudioStart(it.toLong()) },
                                valueRange = 0f..project.audioDurationMs.toFloat(),
                                colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(20.dp)
                            )
                        }
                    }
                }
                IconButton(onClick = onAddAudioClick, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.MusicNote, contentDescription = "Cambiar canción", tint = Color.White, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onRemoveAudio, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Quitar canción", tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
