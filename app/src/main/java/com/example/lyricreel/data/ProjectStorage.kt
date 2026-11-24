package com.example.lyricreel.data

import android.content.Context
import com.example.lyricreel.model.LyricLine
import com.example.lyricreel.model.MediaClip
import com.example.lyricreel.model.ReelProject
import org.json.JSONArray
import org.json.JSONObject

class ProjectStorage(context: Context) {
    private val prefs = context.getSharedPreferences("lyric_reel_autosave", Context.MODE_PRIVATE)

    fun saveProject(project: ReelProject) {
        val root = JSONObject().apply {
            put("audioUri", project.audioUri ?: JSONObject.NULL)
            put("audioName", project.audioName ?: JSONObject.NULL)
            put("audioDurationMs", project.audioDurationMs)
            put("audioTrimStartMs", project.audioTrimStartMs)
            put("muteOriginalAudio", project.muteOriginalAudio)
            put("textXRatio", project.textXRatio.toDouble())
            put("textYRatio", project.textYRatio.toDouble())
            put("textScale", project.textScale.toDouble())

            put("lyricAnimation", project.lyricAnimation)
            put("lyricFont", project.lyricFont)
            put("textColor", project.textColor)
            put("strokeColor", project.strokeColor)
            put("hasStroke", project.hasStroke)
            put("hasShadow", project.hasShadow)

            val clipsArray = JSONArray()
            project.clips.forEach { clip ->
                val obj = JSONObject().apply {
                    put("id", clip.id)
                    put("uri", clip.uri)
                    put("name", clip.name)
                    put("isImage", clip.isImage)
                    put("sourceDurationMs", clip.sourceDurationMs)
                    put("trimStartMs", clip.trimStartMs)
                    put("trimEndMs", clip.trimEndMs)
                    put("scale", clip.scale.toDouble())
                    put("offsetX", clip.offsetX.toDouble())
                    put("offsetY", clip.offsetY.toDouble())
                    put("fillScreen", clip.fillScreen)
                }
                clipsArray.put(obj)
            }
            put("clips", clipsArray)

            val lyricsArray = JSONArray()
            project.lyrics.forEach { line ->
                val lObj = JSONObject().apply {
                    put("id", line.id)
                    put("text", line.text)
                    put("startTimeMs", line.startTimeMs ?: JSONObject.NULL)
                }
                lyricsArray.put(lObj)
            }
            put("lyrics", lyricsArray)
        }
        prefs.edit().putString("current_project_json", root.toString()).apply()
    }

    fun loadProject(): ReelProject {
        val raw = prefs.getString("current_project_json", null) ?: return ReelProject()
        return try {
            val root = JSONObject(raw)
            val clipsArray = root.optJSONArray("clips") ?: JSONArray()
            val clips = mutableListOf<MediaClip>()
            for (i in 0 until clipsArray.length()) {
                val obj = clipsArray.getJSONObject(i)
                val sourceDur = obj.optLong("sourceDurationMs", 3000L)
                clips.add(
                    MediaClip(
                        id = obj.optString("id"),
                        uri = obj.optString("uri"),
                        name = obj.optString("name", "Clip ${i + 1}"),
                        isImage = obj.optBoolean("isImage", false),
                        sourceDurationMs = sourceDur,
                        trimStartMs = obj.optLong("trimStartMs", 0L),
                        trimEndMs = obj.optLong("trimEndMs", sourceDur),
                        scale = obj.optDouble("scale", 1.0).toFloat(),
                        offsetX = obj.optDouble("offsetX", 0.0).toFloat(),
                        offsetY = obj.optDouble("offsetY", 0.0).toFloat(),
                        fillScreen = obj.optBoolean("fillScreen", true)
                    )
                )
            }

            val lyricsArray = root.optJSONArray("lyrics") ?: JSONArray()
            val lyrics = mutableListOf<LyricLine>()
            for (i in 0 until lyricsArray.length()) {
                val lObj = lyricsArray.getJSONObject(i)
                val startMs = if (lObj.isNull("startTimeMs")) null else lObj.optLong("startTimeMs")
                lyrics.add(
                    LyricLine(
                        id = lObj.optString("id"),
                        text = lObj.optString("text", ""),
                        startTimeMs = startMs
                    )
                )
            }

            val audioUri = if (root.isNull("audioUri")) null else root.optString("audioUri", null)
            val audioName = if (root.isNull("audioName")) null else root.optString("audioName", null)
            ReelProject(
                clips = clips,
                audioUri = audioUri,
                audioName = audioName,
                audioDurationMs = root.optLong("audioDurationMs", 0L),
                audioTrimStartMs = root.optLong("audioTrimStartMs", 0L),
                muteOriginalAudio = root.optBoolean("muteOriginalAudio", true),
                lyrics = lyrics,
                textXRatio = root.optDouble("textXRatio", 0.5).toFloat(),
                textYRatio = root.optDouble("textYRatio", 0.72).toFloat(),
                textScale = root.optDouble("textScale", 1.0).toFloat(),
                
                lyricAnimation = root.optString("lyricAnimation", "SLIDE_UP"),
                lyricFont = root.optString("lyricFont", "SANS"),
                textColor = root.optString("textColor", "#FFFFFF"),
                strokeColor = root.optString("strokeColor", "#000000"),
                hasStroke = root.optBoolean("hasStroke", true),
                hasShadow = root.optBoolean("hasShadow", true)
            )
        } catch (e: Exception) {
            ReelProject()
        }
    }
}
