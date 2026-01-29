package com.arcowebdesign.hikingtracker.data

import com.google.android.gms.maps.model.LatLng

/**
 * Represents a single GPS track point
 */
data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val timestamp: Long,
    val accuracy: Float,
    val speed: Float
) {
    fun toLatLng(): LatLng = LatLng(latitude, longitude)
}

/**
 * Represents a complete hiking track/trail
 */
data class HikingTrack(
    val id: Long = System.currentTimeMillis(),
    val name: String = "Track ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}",
    val points: MutableList<TrackPoint> = mutableListOf(),
    val startTime: Long = System.currentTimeMillis(),
    var endTime: Long? = null,
    var totalDistance: Float = 0f,
    var elevationGain: Float = 0f,
    var elevationLoss: Float = 0f,
    var maxAltitude: Double = 0.0,
    var minAltitude: Double = Double.MAX_VALUE
) {
    val isActive: Boolean get() = endTime == null
    
    val duration: Long get() = (endTime ?: System.currentTimeMillis()) - startTime
    
    val durationFormatted: String get() {
        val seconds = duration / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, secs)
    }
    
    val distanceFormatted: String get() {
        return if (totalDistance >= 1000) {
            String.format("%.2f km", totalDistance / 1000)
        } else {
            String.format("%.0f m", totalDistance)
        }
    }
    
    fun addPoint(point: TrackPoint) {
        if (points.isNotEmpty()) {
            val lastPoint = points.last()
            val distance = calculateDistance(lastPoint, point)
            totalDistance += distance
            
            val elevationDiff = point.altitude - lastPoint.altitude
            if (elevationDiff > 0) {
                elevationGain += elevationDiff.toFloat()
            } else {
                elevationLoss += (-elevationDiff).toFloat()
            }
        }
        
        if (point.altitude > maxAltitude) maxAltitude = point.altitude
        if (point.altitude < minAltitude) minAltitude = point.altitude
        
        points.add(point)
    }
    
    private fun calculateDistance(p1: TrackPoint, p2: TrackPoint): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            p1.latitude, p1.longitude,
            p2.latitude, p2.longitude,
            results
        )
        return results[0]
    }
    
    fun getLatLngList(): List<LatLng> = points.map { it.toLatLng() }
}

/**
 * Track statistics for display
 */
data class TrackStats(
    val distance: Float,
    val duration: Long,
    val avgSpeed: Float,
    val currentSpeed: Float,
    val currentAltitude: Double,
    val elevationGain: Float,
    val elevationLoss: Float,
    val pointCount: Int
)
