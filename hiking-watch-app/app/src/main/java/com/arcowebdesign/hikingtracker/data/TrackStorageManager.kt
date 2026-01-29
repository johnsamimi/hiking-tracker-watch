package com.arcowebdesign.hikingtracker.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Manages saving and loading tracks to/from local storage
 */
class TrackStorageManager(private val context: Context) {
    
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val tracksDir: File = File(context.filesDir, "tracks")
    
    init {
        if (!tracksDir.exists()) {
            tracksDir.mkdirs()
        }
    }
    
    /**
     * Save a track to storage
     */
    fun saveTrack(track: HikingTrack): Boolean {
        return try {
            val file = File(tracksDir, "track_${track.id}.json")
            file.writeText(gson.toJson(track))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Load a specific track by ID
     */
    fun loadTrack(trackId: Long): HikingTrack? {
        return try {
            val file = File(tracksDir, "track_${trackId}.json")
            if (file.exists()) {
                gson.fromJson(file.readText(), HikingTrack::class.java)
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Load all saved tracks
     */
    fun loadAllTracks(): List<HikingTrack> {
        return try {
            tracksDir.listFiles()
                ?.filter { it.name.endsWith(".json") }
                ?.mapNotNull { file ->
                    try {
                        gson.fromJson(file.readText(), HikingTrack::class.java)
                    } catch (e: Exception) {
                        null
                    }
                }
                ?.sortedByDescending { it.startTime }
                ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * Delete a track
     */
    fun deleteTrack(trackId: Long): Boolean {
        return try {
            val file = File(tracksDir, "track_${trackId}.json")
            file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Export track to GPX format for sharing
     */
    fun exportToGpx(track: HikingTrack): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<gpx version="1.1" creator="HikingTracker">""")
        sb.appendLine("  <trk>")
        sb.appendLine("    <name>${track.name}</name>")
        sb.appendLine("    <trkseg>")
        
        track.points.forEach { point ->
            sb.appendLine("""      <trkpt lat="${point.latitude}" lon="${point.longitude}">""")
            sb.appendLine("        <ele>${point.altitude}</ele>")
            sb.appendLine("        <time>${java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date(point.timestamp))}</time>")
            sb.appendLine("      </trkpt>")
        }
        
        sb.appendLine("    </trkseg>")
        sb.appendLine("  </trk>")
        sb.appendLine("</gpx>")
        
        return sb.toString()
    }
    
    /**
     * Save current track as auto-save (for crash recovery)
     */
    fun autoSave(track: HikingTrack) {
        try {
            val file = File(context.filesDir, "autosave_track.json")
            file.writeText(gson.toJson(track))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Load auto-saved track
     */
    fun loadAutoSave(): HikingTrack? {
        return try {
            val file = File(context.filesDir, "autosave_track.json")
            if (file.exists()) {
                gson.fromJson(file.readText(), HikingTrack::class.java)
            } else null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Clear auto-save
     */
    fun clearAutoSave() {
        try {
            val file = File(context.filesDir, "autosave_track.json")
            file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
