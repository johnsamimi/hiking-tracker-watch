# 🥾 Hiking Tracker for Galaxy Watch 4

A powerful GPS tracking app for mountaineering and off-road hiking designed specifically for Samsung Galaxy Watch 4 (Wear OS 3+).

## Features

### Core Features
- **GPS Track Recording** - Continuous GPS tracking with path visualization
- **Track Line Display** - Orange trail line showing your hiking path on the map
- **Google Maps Integration** - Terrain view optimized for outdoor use
- **Offline Map Support** - Works with Google Maps offline areas
- **Return Route** - Visual breadcrumb trail to help navigate back

### Statistics Displayed
- Distance traveled (km/m)
- Duration (HH:MM:SS)
- Current altitude (meters)
- Current speed (km/h)
- Elevation gain/loss

### Smart Features
- Auto-save protection (crash recovery)
- GPS accuracy filtering (ignores inaccurate points)
- Battery-optimized tracking
- Pause/Resume tracking
- Track storage and history

---

## 📋 Prerequisites

1. **Android Studio** (Hedgehog or newer)
2. **Galaxy Watch 4** or compatible Wear OS 3+ device
3. **Google Maps API Key** (free from Google Cloud Console)
4. **Wear OS emulator** (optional for testing)

---

## 🔧 Setup Instructions

### Step 1: Get Google Maps API Key

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select existing
3. Enable **Maps SDK for Android**
4. Go to **Credentials** → **Create Credentials** → **API Key**
5. Restrict the key to your app's package name: `com.arcowebdesign.hikingtracker`

### Step 2: Configure API Key

Open `app/build.gradle` and replace:
```groovy
manifestPlaceholders = [
    MAPS_API_KEY: "YOUR_GOOGLE_MAPS_API_KEY"  // ← Replace this
]
```

### Step 3: Build the Project

```bash
# Clone or copy the project
cd hiking-watch-app

# Build with Gradle
./gradlew assembleDebug
```

### Step 4: Install on Watch

**Option A: Via ADB (Developer Mode)**
```bash
# Enable Developer Options on watch
# Settings → About Watch → Software → tap Build Number 7 times

# Enable ADB Debugging
# Settings → Developer Options → ADB Debugging → ON

# Connect watch via WiFi ADB
adb connect <watch-ip-address>:5555

# Install APK
adb install app/build/outputs/apk/debug/app-debug.apk
```

**Option B: Via Wear Installer app**
- Use the Wear Installer app on your phone to sideload

---

## 📱 Using the App

### Starting a Hike

1. Open **Hiking Tracker** on your watch
2. Wait for GPS lock (map will center on your position)
3. Tap **START** to begin tracking
4. Your path will appear as an orange line on the map

### During the Hike

- **Stats Panel** shows distance, duration, altitude, speed
- **Orange Line** = your hiking trail
- **Green Marker** = starting point
- **Blue Marker** = current position
- Tap **◎** button to re-center map on your location
- Tap **PAUSE** to pause tracking (GPS stops)
- Tap **RESUME** to continue

### Ending the Hike

1. Tap **STOP** to finish tracking
2. Track is automatically saved
3. You can view the trail to return via same path

### Following Your Trail Back

The orange track line remains visible, allowing you to:
1. See exactly where you came from
2. Follow the line back to your starting point (green marker)
3. Avoid getting lost in unfamiliar terrain

---

## 🗺️ Offline Maps Setup

For hiking in areas without cellular coverage:

### On Your Phone (before the hike):

1. Open **Google Maps** app
2. Search for your hiking area
3. Tap on the location name
4. Scroll down and tap **Download**
5. Adjust the area to cover your hike
6. Download while on WiFi

The watch will use cached map data when available.

---

## ⚙️ Configuration Options

### GPS Settings (in GpsTrackingService.kt)

```kotlin
// Update interval (milliseconds)
const val GPS_UPDATE_INTERVAL = 3000L  // Default: 3 seconds

// Fastest interval (minimum time between updates)
const val GPS_FASTEST_INTERVAL = 1000L  // Default: 1 second

// Minimum movement to record point (meters)
const val GPS_MIN_DISTANCE = 2f  // Default: 2 meters

// Auto-save frequency (every N points)
const val AUTO_SAVE_INTERVAL = 10
```

### Track Line Styling (in MainActivity.kt)

```kotlin
// Trail color (orange by default)
private val trackLineColor = Color.parseColor("#FF5722")

// Line width
private val trackLineWidth = 8f
```

---

## 📁 Project Structure

```
hiking-watch-app/
├── app/
│   ├── src/main/
│   │   ├── java/com/arcowebdesign/hikingtracker/
│   │   │   ├── HikingTrackerApp.kt      # Application class
│   │   │   ├── data/
│   │   │   │   ├── TrackData.kt         # Data models
│   │   │   │   └── TrackStorageManager.kt # Save/load tracks
│   │   │   ├── service/
│   │   │   │   └── GpsTrackingService.kt # Background GPS service
│   │   │   ├── ui/
│   │   │   │   └── MainActivity.kt      # Main map screen
│   │   │   └── util/
│   │   │       └── MapUtils.kt          # Map utilities
│   │   ├── res/
│   │   │   ├── layout/activity_main.xml
│   │   │   ├── drawable/                # Icons and shapes
│   │   │   └── values/                  # Strings, colors, themes
│   │   └── AndroidManifest.xml
│   ├── build.gradle
│   └── proguard-rules.pro
├── build.gradle
├── settings.gradle
└── README.md
```

---

## 🔋 Battery Optimization Tips

1. **Lower GPS interval** for longer hikes (5-10 seconds)
2. **Enable battery saver** mode on watch
3. **Turn off always-on display** during hike
4. **Download offline maps** to reduce network usage

---

## 🐛 Troubleshooting

### GPS Not Working
- Ensure location permission is granted
- Wait for GPS lock (can take 30-60 seconds)
- Move to open area with clear sky view

### Map Not Loading
- Check Google Maps API key is correct
- Ensure internet connection or offline maps
- Verify API key restrictions match package name

### Track Not Saving
- Check storage permissions
- Ensure enough storage space
- Tracks auto-save every 10 points

---

## 📄 Permissions Required

| Permission | Purpose |
|------------|---------|
| `ACCESS_FINE_LOCATION` | GPS tracking |
| `ACCESS_COARSE_LOCATION` | Backup location |
| `ACCESS_BACKGROUND_LOCATION` | Track when screen off |
| `FOREGROUND_SERVICE` | Keep GPS running |
| `WAKE_LOCK` | Prevent sleep during tracking |
| `INTERNET` | Load map tiles |
| `POST_NOTIFICATIONS` | Show tracking notification |

---

## 📧 Support

Developed by **Arco Web Design**

For questions or customization requests, contact through your preferred channel.

---

## 📜 License

This project is provided for personal use. Commercial use requires permission.

---

## 🚀 Future Enhancements

- [ ] Heart rate integration
- [ ] Waypoint markers
- [ ] GPX import/export
- [ ] Track sharing
- [ ] Compass widget
- [ ] Weather alerts
- [ ] Voice alerts for distance milestones
