# INS Terminal & System Optimizer

Android terminal utility and system-level performance optimizer built with modern Kotlin and Jetpack Compose.

## Features

- **Terminal Environment**: Command execution engine supporting built-in UNIX utilities, custom pipelines, media previews, and interactive shell sessions.
- **Hardware & Performance Tweaks**: Kernel governor presets, CPU throttle management, ZRAM swap tuning, TCP congestion algorithms, and background throttling control.
- **System Diagnostics**: Real-time hardware telemetry, thermal sensors monitoring, memory allocation tracking, and battery health analytics.
- **Package & Symlink Manager**: Custom binary path management and environment variable configuration.
- **Audio & Display Routing**: Direct system audio routing toggles, display refresh rate switcher, and floating booster overlay.

## Tech Stack

- **Language**: Kotlin 2.0+
- **UI Framework**: Jetpack Compose with Material Design 3
- **Local Persistence**: Room Database
- **Networking & Async**: OkHttp, Retrofit, Kotlin Coroutines & Flow
- **Architecture**: MVVM with unidirectional data flow

## Building from Source

Prerequisites:
- Android Studio Hedgehog or newer
- JDK 17
- Android SDK 35

```bash
git clone https://github.com/INsITdeveloper/INS-terminal.git
cd INS-terminal
./gradlew assembleDebug
```

## License

MIT License.
