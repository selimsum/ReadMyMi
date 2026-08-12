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
- 📊 **Rich Dashboard:** High-fidelity interactive charts (via HelloCharts) to visualize temperature and humidity trends.
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

- Android Studio Koala / Ladybug or newer
- Android SDK 34+
- A physical Android device with Bluetooth Low Energy (BLE) support (recommended for scanning tests)

### Compilation & Installation

1. **Clone the repository:**
   ```bash
   git clone https://github.com/yourusername/ReadMyMi.git
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

For performance benchmarks, you can check `Benchmark.kt` which evaluates optimizations for data formatting and date manipulations.

---

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
