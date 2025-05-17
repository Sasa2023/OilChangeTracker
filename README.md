# OilChangeTracker

An Android app for tracking vehicle oil changes based on odometer readings.

## Features
- Take photos of your odometer when changing oil
- Manually enter the current mileage reading
- Set custom oil change intervals (default 5,000 km)
- Track your last oil change date and mileage
- In-app notification when approaching the next oil change
- Store all history locally on your device

## Build & Install
1. Clone this repository:
   ```bash
   git clone https://github.com/yourusername/OilChangeTracker.git
   cd OilChangeTracker
   ```
2. Build the debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
3. Install on your device:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```