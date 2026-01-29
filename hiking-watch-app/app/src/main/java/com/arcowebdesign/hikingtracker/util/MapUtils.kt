package com.arcowebdesign.hikingtracker.util

import android.content.Context
import android.graphics.Color
import android.location.Location
import com.arcowebdesign.hikingtracker.data.TrackPoint
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.*
import kotlin.math.*

/**
 * Utility functions for map operations
 */
object MapUtils {
    
    /**
     * Calculate the bounding box for a list of points
     */
    fun calculateBounds(points: List<TrackPoint>): LatLngBounds? {
        if (points.isEmpty()) return null
        
        val builder = LatLngBounds.Builder()
        points.forEach { point ->
            builder.include(point.toLatLng())
        }
        return builder.build()
    }
    
    /**
     * Zoom to fit all track points with padding
     */
    fun zoomToFitTrack(map: GoogleMap, points: List<TrackPoint>, padding: Int = 50) {
        calculateBounds(points)?.let { bounds ->
            map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding))
        }
    }
    
    /**
     * Calculate bearing between two points
     */
    fun calculateBearing(from: LatLng, to: LatLng): Float {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val diffLng = Math.toRadians(to.longitude - from.longitude)
        
        val x = sin(diffLng) * cos(lat2)
        val y = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(diffLng)
        
        return ((Math.toDegrees(atan2(x, y)) + 360) % 360).toFloat()
    }
    
    /**
     * Calculate distance between two points in meters
     */
    fun calculateDistance(from: LatLng, to: LatLng): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            from.latitude, from.longitude,
            to.latitude, to.longitude,
            results
        )
        return results[0]
    }
    
    /**
     * Create a gradient polyline based on elevation or speed
     */
    fun createGradientPolyline(
        points: List<TrackPoint>,
        colorMode: ColorMode = ColorMode.SOLID
    ): PolylineOptions {
        val options = PolylineOptions()
            .geodesic(true)
            .jointType(JointType.ROUND)
            .startCap(RoundCap())
            .endCap(RoundCap())
            .width(8f)
        
        when (colorMode) {
            ColorMode.SOLID -> {
                options.color(Color.parseColor("#FF5722"))
                points.forEach { options.add(it.toLatLng()) }
            }
            ColorMode.ELEVATION -> {
                // Create gradient based on elevation
                if (points.size >= 2) {
                    val minAlt = points.minOf { it.altitude }
                    val maxAlt = points.maxOf { it.altitude }
                    val range = maxAlt - minAlt
                    
                    val spans = mutableListOf<StyleSpan>()
                    points.forEachIndexed { index, point ->
                        options.add(point.toLatLng())
                        if (index < points.size - 1 && range > 0) {
                            val normalized = ((point.altitude - minAlt) / range).toFloat()
                            val color = interpolateColor(
                                Color.parseColor("#4CAF50"),  // Low = green
                                Color.parseColor("#F44336"),  // High = red
                                normalized
                            )
                            spans.add(StyleSpan(color))
                        }
                    }
                    options.addAllSpans(spans)
                }
            }
            ColorMode.SPEED -> {
                // Create gradient based on speed
                if (points.size >= 2) {
                    val maxSpeed = points.maxOf { it.speed }
                    
                    val spans = mutableListOf<StyleSpan>()
                    points.forEachIndexed { index, point ->
                        options.add(point.toLatLng())
                        if (index < points.size - 1 && maxSpeed > 0) {
                            val normalized = point.speed / maxSpeed
                            val color = interpolateColor(
                                Color.parseColor("#2196F3"),  // Slow = blue
                                Color.parseColor("#FF9800"),  // Fast = orange
                                normalized
                            )
                            spans.add(StyleSpan(color))
                        }
                    }
                    options.addAllSpans(spans)
                }
            }
        }
        
        return options
    }
    
    /**
     * Interpolate between two colors
     */
    private fun interpolateColor(colorStart: Int, colorEnd: Int, fraction: Float): Int {
        val startA = Color.alpha(colorStart)
        val startR = Color.red(colorStart)
        val startG = Color.green(colorStart)
        val startB = Color.blue(colorStart)
        
        val endA = Color.alpha(colorEnd)
        val endR = Color.red(colorEnd)
        val endG = Color.green(colorEnd)
        val endB = Color.blue(colorEnd)
        
        return Color.argb(
            (startA + (endA - startA) * fraction).toInt(),
            (startR + (endR - startR) * fraction).toInt(),
            (startG + (endG - startG) * fraction).toInt(),
            (startB + (endB - startB) * fraction).toInt()
        )
    }
    
    /**
     * Simplify track using Douglas-Peucker algorithm for performance
     */
    fun simplifyTrack(points: List<TrackPoint>, tolerance: Double = 0.00001): List<TrackPoint> {
        if (points.size <= 2) return points
        
        var maxDistance = 0.0
        var maxIndex = 0
        
        val first = points.first()
        val last = points.last()
        
        for (i in 1 until points.size - 1) {
            val distance = perpendicularDistance(points[i], first, last)
            if (distance > maxDistance) {
                maxDistance = distance
                maxIndex = i
            }
        }
        
        return if (maxDistance > tolerance) {
            val left = simplifyTrack(points.subList(0, maxIndex + 1), tolerance)
            val right = simplifyTrack(points.subList(maxIndex, points.size), tolerance)
            left.dropLast(1) + right
        } else {
            listOf(first, last)
        }
    }
    
    private fun perpendicularDistance(point: TrackPoint, lineStart: TrackPoint, lineEnd: TrackPoint): Double {
        val dx = lineEnd.longitude - lineStart.longitude
        val dy = lineEnd.latitude - lineStart.latitude
        
        val mag = sqrt(dx * dx + dy * dy)
        if (mag == 0.0) return 0.0
        
        val u = ((point.longitude - lineStart.longitude) * dx + 
                 (point.latitude - lineStart.latitude) * dy) / (mag * mag)
        
        val closestX: Double
        val closestY: Double
        
        if (u < 0) {
            closestX = lineStart.longitude
            closestY = lineStart.latitude
        } else if (u > 1) {
            closestX = lineEnd.longitude
            closestY = lineEnd.latitude
        } else {
            closestX = lineStart.longitude + u * dx
            closestY = lineStart.latitude + u * dy
        }
        
        return sqrt((point.longitude - closestX).pow(2) + (point.latitude - closestY).pow(2))
    }
    
    enum class ColorMode {
        SOLID,
        ELEVATION,
        SPEED
    }
}

/**
 * Offline map tile provider for when network is unavailable
 * Note: Full offline maps require downloading map tiles beforehand
 */
class OfflineMapManager(private val context: Context) {
    
    private val tileCache = mutableMapOf<String, ByteArray>()
    
    /**
     * Check if offline maps are available for the current area
     */
    fun hasOfflineArea(bounds: LatLngBounds): Boolean {
        // Implementation would check local storage for cached tiles
        // This is a placeholder - full implementation requires tile caching
        return false
    }
    
    /**
     * Get the map type based on network availability
     */
    fun getRecommendedMapType(hasNetwork: Boolean): Int {
        return if (hasNetwork) {
            GoogleMap.MAP_TYPE_TERRAIN  // Best for hiking with network
        } else {
            GoogleMap.MAP_TYPE_NORMAL   // Basic map for offline
        }
    }
    
    /**
     * Instructions for downloading offline maps
     * Google Maps allows downloading specific areas for offline use
     */
    fun getOfflineMapInstructions(): String {
        return """
            To use offline maps:
            1. Open Google Maps app on your phone
            2. Search for your hiking area
            3. Tap the location name at bottom
            4. Tap "Download" to save offline map
            5. The watch will use cached data when available
            
            For best results, download maps before your hike
            while connected to WiFi.
        """.trimIndent()
    }
}

/**
 * Compass utility for showing heading direction
 */
object CompassUtils {
    
    /**
     * Get cardinal direction from bearing
     */
    fun getCardinalDirection(bearing: Float): String {
        val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val index = ((bearing + 22.5) / 45).toInt() % 8
        return directions[index]
    }
    
    /**
     * Get arrow character for direction
     */
    fun getDirectionArrow(bearing: Float): String {
        return when {
            bearing < 22.5 || bearing >= 337.5 -> "↑"
            bearing < 67.5 -> "↗"
            bearing < 112.5 -> "→"
            bearing < 157.5 -> "↘"
            bearing < 202.5 -> "↓"
            bearing < 247.5 -> "↙"
            bearing < 292.5 -> "←"
            else -> "↖"
        }
    }
}
