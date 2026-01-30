package com.arcowebdesign.hikingtracker.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.arcowebdesign.hikingtracker.R
import com.arcowebdesign.hikingtracker.data.TrackPoint
import com.arcowebdesign.hikingtracker.data.TrackStorageManager
import com.arcowebdesign.hikingtracker.service.GpsTrackingService
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    
    private lateinit var mapView: MapView
    private var googleMap: GoogleMap? = null
    
    // UI Elements
    private lateinit var btnStartStop: Button
    private lateinit var btnPause: Button
    private lateinit var btnCenter: Button
    private lateinit var tvDistance: TextView
    private lateinit var tvDuration: TextView
    private lateinit var tvAltitude: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var statsPanel: View
    
    // Service connection
    private var trackingService: GpsTrackingService? = null
    private var serviceBound = false
    
    // Map elements
    private var trackPolyline: Polyline? = null
    private var currentPositionMarker: Marker? = null
    private var startMarker: Marker? = null
    private var isFollowingUser = true
    
    // Track line styling
    private val trackLineColor = Color.parseColor("#FF5722")  // Orange trail
    private val trackLineWidth = 8f
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as GpsTrackingService.LocalBinder
            trackingService = binder.getService()
            serviceBound = true
            observeTrackingState()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            trackingService = null
            serviceBound = false
        }
    }
    
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        
        if (fineLocationGranted || coarseLocationGranted) {
            enableMyLocation()
            startTrackingService()
        } else {
            Toast.makeText(this, "Location permission required for tracking", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Keep screen on during tracking
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        initViews()
        initMap(savedInstanceState)
        setupClickListeners()
    }
    
    private fun initViews() {
        mapView = findViewById(R.id.mapView)
        btnStartStop = findViewById(R.id.btnStartStop)
        btnPause = findViewById(R.id.btnPause)
        btnCenter = findViewById(R.id.btnCenter)
        tvDistance = findViewById(R.id.tvDistance)
        tvDuration = findViewById(R.id.tvDuration)
        tvAltitude = findViewById(R.id.tvAltitude)
        tvSpeed = findViewById(R.id.tvSpeed)
        statsPanel = findViewById(R.id.statsPanel)
    }
    
    private fun initMap(savedInstanceState: Bundle?) {
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)
    }
    
    private fun setupClickListeners() {
        btnStartStop.setOnClickListener {
            if (hasLocationPermission()) {
                toggleTracking()
            } else {
                requestLocationPermission()
            }
        }
        
        btnPause.setOnClickListener {
            togglePause()
        }
        
        btnCenter.setOnClickListener {
            centerOnCurrentLocation()
            isFollowingUser = true
        }
    }
    
    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        
        // Configure map for hiking/outdoor use
        map.apply {
            mapType = GoogleMap.MAP_TYPE_TERRAIN  // Terrain view for hiking
            
            uiSettings.apply {
                isZoomControlsEnabled = false  // Use bezel on watch
                isCompassEnabled = true
                isMyLocationButtonEnabled = false  // We have our own button
                isRotateGesturesEnabled = true
                isScrollGesturesEnabled = true
                isTiltGesturesEnabled = false  // Simpler for watch
                isZoomGesturesEnabled = true
            }
            
            // Set initial camera
            moveCamera(CameraUpdateFactory.zoomTo(16f))
            
            // Listener to stop auto-follow when user drags
            setOnCameraMoveStartedListener { reason ->
                if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                    isFollowingUser = false
                }
            }
        }
        
        if (hasLocationPermission()) {
            enableMyLocation()
            bindTrackingService()
        } else {
            requestLocationPermission()
        }
    }
    
    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
    
    private fun enableMyLocation() {
        if (hasLocationPermission()) {
            try {
                googleMap?.isMyLocationEnabled = true
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }
    
    private fun bindTrackingService() {
        val intent = Intent(this, GpsTrackingService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }
    
    private fun startTrackingService() {
        val intent = Intent(this, GpsTrackingService::class.java).apply {
            action = GpsTrackingService.ACTION_START_TRACKING
        }
        ContextCompat.startForegroundService(this, intent)
        
        if (!serviceBound) {
            bindTrackingService()
        }
    }
    
    private fun observeTrackingState() {
        lifecycleScope.launch {
            trackingService?.trackingState?.collectLatest { state ->
                updateUI(state)
            }
        }
        
        lifecycleScope.launch {
            trackingService?.trackPoints?.collectLatest { points ->
                updateTrackLine(points)
            }
        }
        
        lifecycleScope.launch {
            trackingService?.currentLocation?.collectLatest { location ->
                location?.let {
                    updateCurrentPosition(LatLng(it.latitude, it.longitude))
                }
            }
        }
    }
    
    private fun updateUI(state: com.arcowebdesign.hikingtracker.service.TrackingState) {
        runOnUiThread {
            // Update button states
            when {
                state.isTracking -> {
                    btnStartStop.text = "STOP"
                    btnStartStop.setBackgroundColor(Color.parseColor("#F44336"))
                    btnPause.visibility = View.VISIBLE
                    btnPause.text = "PAUSE"
                    statsPanel.visibility = View.VISIBLE
                }
                state.isPaused -> {
                    btnStartStop.text = "STOP"
                    btnStartStop.setBackgroundColor(Color.parseColor("#F44336"))
                    btnPause.visibility = View.VISIBLE
                    btnPause.text = "RESUME"
                    statsPanel.visibility = View.VISIBLE
                }
                else -> {
                    btnStartStop.text = "START"
                    btnStartStop.setBackgroundColor(Color.parseColor("#4CAF50"))
                    btnPause.visibility = View.GONE
                    statsPanel.visibility = if (state.hasTrack) View.VISIBLE else View.GONE
                }
            }
            
            // Update stats
            state.stats?.let { stats ->
                tvDistance.text = formatDistance(stats.distance)
                tvDuration.text = formatDuration(stats.duration)
                tvAltitude.text = "${stats.currentAltitude.toInt()}m"
                tvSpeed.text = String.format("%.1f km/h", stats.currentSpeed * 3.6f)
            }
        }
    }
    
    private fun updateTrackLine(points: List<TrackPoint>) {
        googleMap?.let { map ->
            runOnUiThread {
                // Remove old polyline
                trackPolyline?.remove()
                
                if (points.isNotEmpty()) {
                    // Create polyline options with gradient effect
                    val polylineOptions = PolylineOptions()
                        .addAll(points.map { it.toLatLng() })
                        .color(trackLineColor)
                        .width(trackLineWidth)
                        .geodesic(true)
                        .jointType(JointType.ROUND)
                        .startCap(RoundCap())
                        .endCap(RoundCap())
                    
                    trackPolyline = map.addPolyline(polylineOptions)
                    
                    // Add start marker if not exists
                    if (startMarker == null && points.isNotEmpty()) {
                        val startPoint = points.first().toLatLng()
                        startMarker = map.addMarker(
                            MarkerOptions()
                                .position(startPoint)
                                .title("Start")
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                        )
                    }
                }
            }
        }
    }
    
    private fun updateCurrentPosition(position: LatLng) {
        googleMap?.let { map ->
            runOnUiThread {
                // Update or create current position marker
                if (currentPositionMarker == null) {
                    currentPositionMarker = map.addMarker(
                        MarkerOptions()
                            .position(position)
                            .title("Current Position")
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                            .anchor(0.5f, 0.5f)
                    )
                } else {
                    currentPositionMarker?.position = position
                }
                
                // Follow user if enabled
                if (isFollowingUser) {
                    map.animateCamera(CameraUpdateFactory.newLatLng(position))
                }
            }
        }
    }
    
    private fun toggleTracking() {
        val state = trackingService?.trackingState?.value
        
        if (state?.isTracking == true || state?.isPaused == true) {
            // Stop tracking
            trackingService?.stopTracking()
            Toast.makeText(this, "Track saved!", Toast.LENGTH_SHORT).show()
            clearMapElements()
        } else {
            // Start tracking
            startTrackingService()
        }
    }
    
    private fun togglePause() {
        val state = trackingService?.trackingState?.value
        
        if (state?.isTracking == true) {
            trackingService?.pauseTracking()
        } else if (state?.isPaused == true) {
            trackingService?.resumeTracking()
        }
    }
    
    private fun centerOnCurrentLocation() {
        trackingService?.currentLocation?.value?.let { location ->
            googleMap?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(location.latitude, location.longitude),
                    17f
                )
            )
        }
    }
    
    private fun clearMapElements() {
        trackPolyline?.remove()
        trackPolyline = null
        currentPositionMarker?.remove()
        currentPositionMarker = null
        startMarker?.remove()
        startMarker = null
    }
    
    private fun formatDistance(meters: Float): String {
        return if (meters >= 1000) {
            String.format("%.2f km", meters / 1000)
        } else {
            String.format("%.0f m", meters)
        }
    }
    
    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, secs)
    }
    
    // Lifecycle methods for MapView
    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }
    
    override fun onResume() {
        super.onResume()
        mapView.onResume()
        if (serviceBound) {
            observeTrackingState()
        }
    }
    
    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }
    
    override fun onStop() {
        mapView.onStop()
        super.onStop()
    }
    
    override fun onDestroy() {
        mapView.onDestroy()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
        super.onDestroy()
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }
    
    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }
}
