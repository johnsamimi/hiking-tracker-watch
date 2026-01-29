package com.arcowebdesign.hikingtracker.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Binder
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.arcowebdesign.hikingtracker.HikingTrackerApp
import com.arcowebdesign.hikingtracker.R
import com.arcowebdesign.hikingtracker.data.HikingTrack
import com.arcowebdesign.hikingtracker.data.TrackPoint
import com.arcowebdesign.hikingtracker.data.TrackStats
import com.arcowebdesign.hikingtracker.data.TrackStorageManager
import com.arcowebdesign.hikingtracker.ui.MainActivity
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GpsTrackingService : Service() {
    
    private val binder = LocalBinder()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var storageManager: TrackStorageManager
    private var wakeLock: PowerManager.WakeLock? = null
    
    // Current tracking state
    private var currentTrack: HikingTrack? = null
    private var isTracking = false
    
    // State flows for UI updates
    private val _trackingState = MutableStateFlow(TrackingState())
    val trackingState: StateFlow<TrackingState> = _trackingState.asStateFlow()
    
    private val _currentLocation = MutableStateFlow<Location?>(null)
    val currentLocation: StateFlow<Location?> = _currentLocation.asStateFlow()
    
    private val _trackPoints = MutableStateFlow<List<TrackPoint>>(emptyList())
    val trackPoints: StateFlow<List<TrackPoint>> = _trackPoints.asStateFlow()
    
    // Configuration
    companion object {
        const val ACTION_START_TRACKING = "start_tracking"
        const val ACTION_STOP_TRACKING = "stop_tracking"
        const val ACTION_PAUSE_TRACKING = "pause_tracking"
        const val ACTION_RESUME_TRACKING = "resume_tracking"
        
        // GPS update interval in milliseconds
        const val GPS_UPDATE_INTERVAL = 3000L  // 3 seconds for accuracy
        const val GPS_FASTEST_INTERVAL = 1000L  // 1 second minimum
        const val GPS_MIN_DISTANCE = 2f  // 2 meters minimum movement
        
        // Auto-save interval (every N points)
        const val AUTO_SAVE_INTERVAL = 10
    }
    
    inner class LocalBinder : Binder() {
        fun getService(): GpsTrackingService = this@GpsTrackingService
    }
    
    override fun onBind(intent: Intent?): IBinder = binder
    
    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        storageManager = TrackStorageManager(this)
        setupLocationCallback()
        
        // Check for auto-saved track
        storageManager.loadAutoSave()?.let { savedTrack ->
            if (savedTrack.isActive) {
                currentTrack = savedTrack
                _trackPoints.value = savedTrack.points
                updateTrackingState()
            }
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRACKING -> startTracking()
            ACTION_STOP_TRACKING -> stopTracking()
            ACTION_PAUSE_TRACKING -> pauseTracking()
            ACTION_RESUME_TRACKING -> resumeTracking()
        }
        return START_STICKY
    }
    
    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    _currentLocation.value = location
                    
                    if (isTracking && currentTrack != null) {
                        addTrackPoint(location)
                    }
                }
            }
        }
    }
    
    fun startTracking() {
        if (isTracking) return
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            return
        }
        
        // Create new track
        currentTrack = HikingTrack()
        _trackPoints.value = emptyList()
        isTracking = true
        
        // Acquire wake lock to keep GPS active
        acquireWakeLock()
        
        // Start foreground service
        startForeground(HikingTrackerApp.TRACKING_NOTIFICATION_ID, createNotification())
        
        // Request location updates
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            GPS_UPDATE_INTERVAL
        ).apply {
            setMinUpdateIntervalMillis(GPS_FASTEST_INTERVAL)
            setMinUpdateDistanceMeters(GPS_MIN_DISTANCE)
            setWaitForAccurateLocation(true)
        }.build()
        
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
        
        updateTrackingState()
    }
    
    fun stopTracking(): HikingTrack? {
        if (!isTracking) return null
        
        isTracking = false
        
        // Finalize track
        currentTrack?.let { track ->
            track.endTime = System.currentTimeMillis()
            storageManager.saveTrack(track)
            storageManager.clearAutoSave()
        }
        
        val completedTrack = currentTrack
        
        // Stop location updates
        fusedLocationClient.removeLocationUpdates(locationCallback)
        
        // Release wake lock
        releaseWakeLock()
        
        // Stop foreground
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        
        updateTrackingState()
        
        return completedTrack
    }
    
    fun pauseTracking() {
        if (!isTracking) return
        isTracking = false
        fusedLocationClient.removeLocationUpdates(locationCallback)
        updateNotification()
        updateTrackingState()
    }
    
    fun resumeTracking() {
        if (isTracking || currentTrack == null) return
        
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) 
            != PackageManager.PERMISSION_GRANTED) {
            return
        }
        
        isTracking = true
        
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            GPS_UPDATE_INTERVAL
        ).apply {
            setMinUpdateIntervalMillis(GPS_FASTEST_INTERVAL)
            setMinUpdateDistanceMeters(GPS_MIN_DISTANCE)
        }.build()
        
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
        
        updateNotification()
        updateTrackingState()
    }
    
    private fun addTrackPoint(location: Location) {
        val trackPoint = TrackPoint(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = location.altitude,
            timestamp = System.currentTimeMillis(),
            accuracy = location.accuracy,
            speed = location.speed
        )
        
        currentTrack?.let { track ->
            // Filter out inaccurate points
            if (location.accuracy <= 30f) {  // 30 meters accuracy threshold
                track.addPoint(trackPoint)
                _trackPoints.value = track.points.toList()
                
                // Auto-save periodically
                if (track.points.size % AUTO_SAVE_INTERVAL == 0) {
                    storageManager.autoSave(track)
                }
                
                updateNotification()
                updateTrackingState()
            }
        }
    }
    
    private fun updateTrackingState() {
        val track = currentTrack
        val location = _currentLocation.value
        
        _trackingState.value = TrackingState(
            isTracking = isTracking,
            isPaused = !isTracking && track != null,
            hasTrack = track != null,
            stats = track?.let { t ->
                TrackStats(
                    distance = t.totalDistance,
                    duration = t.duration,
                    avgSpeed = if (t.duration > 0) (t.totalDistance / (t.duration / 1000f)) else 0f,
                    currentSpeed = location?.speed ?: 0f,
                    currentAltitude = location?.altitude ?: t.points.lastOrNull()?.altitude ?: 0.0,
                    elevationGain = t.elevationGain,
                    elevationLoss = t.elevationLoss,
                    pointCount = t.points.size
                )
            }
        )
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val track = currentTrack
        val contentText = if (track != null) {
            "${track.distanceFormatted} • ${track.durationFormatted}"
        } else {
            "Starting GPS..."
        }
        
        return NotificationCompat.Builder(this, HikingTrackerApp.TRACKING_CHANNEL_ID)
            .setContentTitle(if (isTracking) "Tracking Active" else "Tracking Paused")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_hiking)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    private fun updateNotification() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(HikingTrackerApp.TRACKING_NOTIFICATION_ID, createNotification())
    }
    
    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "HikingTracker::GpsWakeLock"
        ).apply {
            acquire(12 * 60 * 60 * 1000L)  // 12 hours max
        }
    }
    
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }
    
    fun getCurrentTrack(): HikingTrack? = currentTrack
    
    fun getTrackPointsSnapshot(): List<TrackPoint> = currentTrack?.points?.toList() ?: emptyList()
    
    override fun onDestroy() {
        super.onDestroy()
        if (isTracking) {
            currentTrack?.let { storageManager.autoSave(it) }
        }
        fusedLocationClient.removeLocationUpdates(locationCallback)
        releaseWakeLock()
    }
}

data class TrackingState(
    val isTracking: Boolean = false,
    val isPaused: Boolean = false,
    val hasTrack: Boolean = false,
    val stats: TrackStats? = null
)
