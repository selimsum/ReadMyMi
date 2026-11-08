# ReadMyMi 🌡️💧

[![Android Platform](https://img.shields.io/badge/Platform-Android-3DDC84.svg?style=flat-square&logo=android)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-7F52FF.svg?style=flat-square&logo=kotlin)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg?style=flat-square&logo=jetpackcompose)](https://developer.android.com/jetpack/compose)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=flat-square)](LICENSE)

**ReadMyMi** is a modern, lightweight, and battery-efficient Android application designed to scan, parse, display, and record environmental data from Bluetooth Low Energy (BLE) temperature and humidity sensors. 

It runs passively as a foreground service to provide real-time updates and historical graphs without draining your battery or needing permanent cloud connectivity.

> [!IMPORTANT]  
> **Disclaimer:** This project is an independent open-source tool and is not affiliated with, authorized, sponsored, or in any way officially connected to Xiaomi Inc. or any of its affiliates. "Xiaomi" and "Mijia" are trademarks of Xiaomi Inc.

---

## ✨ Features

- 📶 **Passive BLE Scanning:** Background scanning using an Android Foreground Service to automatically detect and log sensor broadcasts.
- 🧩 **Multi-protocol Parser:** Out-of-the-box support for popular open and proprietary BLE sensor advertising formats:
  - **BTHome** (v1/v2 - unencrypted temperature, humidity, voltage, and battery percentages)
  - **ATC** (Custom custom firmware formats for Mijia LYWSD03MMC sensors, e.g., puzankov/atc1441)
  - **Xiaomi Mijia** (Standard advertising payloads for temperature/humidity/battery)
- 📊 **Rich Dashboard:** Historical temperature and humidity trend charts (via HelloCharts).
- 💾 **Local History:** Fully local database storage powered by **Room DB** so your environmental history never leaves your device.
- ⏳ **Direct History Downloading:** Remotely fetch stored logs directly from compatible custom BLE sensors.
- ⚙️ **Customizable Settings:** Define time filters, temperature units (Celsius/Fahrenheit), view sensor connection states, and clear caches.

---

## 🛠️ Tech Stack & Architecture

- **Core Language:** Kotlin
- **UI Framework:** Jetpack Compose (Material 3)
- **Database:** Android Room Persistence Library
- **Background Processing:** Android Foreground Services with notification channels
- **Dependency Management:** Gradle (Groovy DSL)
- **Concurrency:** Kotlin Coroutines & Flow

---

## 🚀 Getting Started

### Prerequisites

- Android Studio with Android SDK 36 installed
- A physical Android device running Android 12 (API 31) or newer, with Bluetooth Low Energy (BLE) support

### Compilation & Installation

1. **Clone the repository:**
   ```bash
   git clone https://github.com/selimsum/ReadMyMi.git
   cd ReadMyMi
   ```

2. **Build the project:**
   Use Gradle to build the debug APK:
   ```bash
   ./gradlew assembleDebug
   ```

3. **Run on Device:**
   Connect your Android device via USB debugging and run:
   ```bash
   ./gradlew installDebug
   ```

---

## 📂 Project Structure

```
ReadMyMi/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/readmymi/
│   │   │   │   ├── data/                 # Database entities and DAOs (Room)
│   │   │   │   ├── ui/                   # Jetpack Compose screens (Dashboard, Settings, History)
│   │   │   │   ├── MainActivity.kt       # Application entry point
│   │   │   │   ├── MainViewModel.kt      # State management and service controls
│   │   │   │   ├── BluetoothSensorManager.kt # Core BLE scanning and reading logic
│   │   │   │   ├── SensorParser.kt       # BLE advertising packet parser
│   │   │   │   └── SensorForegroundService.kt # Foreground service for background tracking
│   │   │   └── res/                      # Android resources
│   │   └── test/                         # Unit tests for parsers and business logic
│   └── build.gradle                      # Module-level Gradle configuration
├── build.gradle                          # Project-level Gradle configuration
└── settings.gradle                       # Gradle project settings
```

---

## 🧪 Running Tests

The project includes unit tests for data parsers, preference managers, and utilities. You can execute them via command line:

```bash
./gradlew test
```

CI runs the unit tests and a debug build on every push and pull request.

---

## 📦 Releases & Versioning

ReadMyMi publishes a new GitHub Release automatically whenever the version is bumped and pushed to the `main` branch.

### How it works:
1. When `versionName` in `app/build.gradle` is updated (e.g., from `1.7.0` to `1.7.1`) and pushed to `main`, the GitHub Actions release workflow:
   - Verifies all unit tests pass.
   - Compiles both release and debug APKs (`ReadMyMi-v<version>.apk`, `ReadMyMi-v<version>-debug.apk`).
   - Computes SHA256 checksums (`sha256sums.txt`).
   - Automatically creates the Git tag `v<version>`.
   - Publishes the GitHub Release with automated changelog release notes and attaches the APKs.
2. Pushing a Git tag directly (`git push origin v<version>`) or manually triggering the **Publish Release** workflow in the Actions tab also triggers a release.

### Bumping the version:

You can bump the version and trigger a release with one command:

**On Windows (PowerShell):**
```powershell
# Bump patch version (e.g. 1.7.0 -> 1.7.1), commit, and push:
.\bump-version.ps1 -Patch -Commit -Push

# Or bump minor (e.g. 1.7.0 -> 1.8.0):
.\bump-version.ps1 -Minor -Commit -Push

# Or set an explicit version:
.\bump-version.ps1 1.7.1 -Commit -Push
```

**On Linux / macOS (Bash):**
```bash
# Bump patch version, commit, and push:
./bump-version.sh --patch --push

# Or bump minor:
./bump-version.sh --minor --push

# Or set an explicit version:
./bump-version.sh 1.7.1 --push
```

---

## 🔐 Permissions & Privacy

ReadMyMi requests only what BLE scanning actually needs:

- `BLUETOOTH_SCAN` (declared `neverForLocation`) and `BLUETOOTH_CONNECT` — discover and read sensor advertisements. No location permission is requested or required.
- `POST_NOTIFICATIONS` — ongoing scanner status and threshold alerts.
- `FOREGROUND_SERVICE` with the `connectedDevice` type — keeps the scanner running in the background.

There is **no `INTERNET` permission**: readings are stored locally in Room and never leave your device.

---

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
