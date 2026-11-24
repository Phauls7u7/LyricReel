package com.example.lyricreel.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.lyricreel.model.ReelProject

@Composable
fun LyricStyleDialog(
    project: ReelProject,
    onUpdateStyle: (ReelProject) -> Unit,
    onDismiss: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val colors = listOf("#FFFFFF", "#FFD700", "#00E5FF", "#00FF00", "#FF00FF", "#FF3333", "#000000")
    val fonts = listOf("SANS" to "Moderna", "SERIF" to "Clásica", "MONOSPACE" to "Máquina", "CURSIVE" to "Cursiva")
    val anims = listOf("NONE" to "Ninguna", "FADE" to "Aparecer", "SLIDE_UP" to "Subir", "ZOOM" to "Zoom In", "TYPEWRITER" to "Escribir", "WORD_BY_WORD" to "Palabra")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF141414),
            modifier = Modifier.fillMaxWidth().wrapContentHeight()
        ) {
            Column(
                modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Estilo de Letras", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)

                Text("Color de Texto", color = Color.LightGray, fontSize = 13.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(colors) { hex ->
                        val colorVal = Color(android.graphics.Color.parseColor(hex))
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(colorVal)
                                .border(2.dp, if (project.textColor == hex) accent else Color.Gray, CircleShape)
                                .clickable { onUpdateStyle(project.copy(textColor = hex)) }
                        )
                    }
                }

                Text("Tipografía", color = Color.LightGray, fontSize = 13.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(fonts) { (id, name) ->
                        FilterChip(
                            selected = project.lyricFont == id,
                            onClick = { onUpdateStyle(project.copy(lyricFont = id)) },
                            label = { Text(name) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent, selectedLabelColor = Color.Black)
                        )
                    }
                }

                Text("Animación de Entrada", color = Color.LightGray, fontSize = 13.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(anims) { (id, name) ->
                        FilterChip(
                            selected = project.lyricAnimation == id,
                            onClick = { onUpdateStyle(project.copy(lyricAnimation = id)) },
                            label = { Text(name) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accent, selectedLabelColor = Color.Black)
                        )
                    }
                }

                Text("Efectos", color = Color.LightGray, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = project.hasStroke, onCheckedChange = { onUpdateStyle(project.copy(hasStroke = it)) }, colors = CheckboxDefaults.colors(checkedColor = accent))
                        Text("Contorno", color = Color.White, fontSize = 14.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = project.hasShadow, onCheckedChange = { onUpdateStyle(project.copy(hasShadow = it)) }, colors = CheckboxDefaults.colors(checkedColor = accent))
                        Text("Sombra", color = Color.White, fontSize = 14.sp)
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
                ) {
                    Text("Listo", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
