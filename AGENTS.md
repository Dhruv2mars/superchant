# AGENTS.md - SuperChant Codebase Guide

This document provides guidance for AI coding agents working on the SuperChant Android app.

## Project Overview

SuperChant is a Kotlin/Jetpack Compose Android app for tracking chanting/meditation sessions. It uses volume keys to increment counts via an AccessibilityService, runs as a foreground service, and persists data using DataStore Preferences.

## Build Commands

```bash
# Build
./gradlew assembleDebug          # Debug APK
./gradlew assembleRelease        # Release APK
./gradlew bundleRelease          # Release AAB (for Play Store)
./gradlew installDebug           # Install debug on device
./gradlew clean                  # Clean build

# Custom tasks (output to project root)
./gradlew buildReleaseApk        # Build & copy APK as SuperChant-v{version}.apk
./gradlew buildReleaseBundle     # Build & copy AAB as SuperChant-v{version}.aab

# Testing
./gradlew test                                                    # All unit tests
./gradlew test --tests "com.superchant.ExampleTest"              # Single test class
./gradlew test --tests "com.superchant.ExampleTest.testMethod"   # Single test method
./gradlew connectedAndroidTest                                    # Instrumentation tests
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.superchant.ExampleTest

# Lint
./gradlew lint
```

## Project Configuration

| Setting | Value |
|---------|-------|
| Namespace | `com.superchant` |
| Min SDK | 26 (Android 8.0) |
| Target/Compile SDK | 35 |
| Java/Kotlin Target | 17 |
| Kotlin Version | 2.0.21 |
| Compose BOM | 2024.12.01 |

## Code Style Guidelines

### Import Order
1. Android imports (`android.*`, `androidx.*`)
2. Third-party imports (`kotlinx.*`, `org.*`)
3. Project imports (`com.superchant.*`)

### Naming Conventions
| Type | Convention | Example |
|------|------------|---------|
| Classes | PascalCase | `ChantingService`, `PreferencesRepository` |
| Functions | camelCase | `incrementCount()`, `startSession()` |
| Composables | PascalCase | `HomeScreen`, `CounterDisplay` |
| Constants | SCREAMING_SNAKE_CASE | `NOTIFICATION_ID`, `COUNTS_PER_ROUND` |
| Private backing fields | Underscore prefix | `_sharedChantState`, `_instance` |
| Flow properties | `*Flow` suffix | `themeModeFlow`, `goalsFlow` |
| Boolean getters | `is*` prefix | `isRunning`, `isGoalComplete` |

### Formatting
- 4-space indentation (no tabs)
- Trailing lambda style for Compose DSL
- `when` expressions preferred over if-else chains
- Extension properties for computed values: `val progress: Float get() = ...`
- `@Serializable` annotation for JSON-serialized data classes
- Composable modifier parameter: always last with default `modifier: Modifier = Modifier`

### Error Handling
```kotlin
// Silent failure for non-critical operations
try {
    ringtone?.play()
} catch (e: Exception) {
    // Silently fail
}

// Safe fallbacks for JSON parsing
val items = try {
    Json.decodeFromString<List<Item>>(jsonString)
} catch (e: Exception) {
    emptyList()
}
```

### Threading & Concurrency
```kotlin
// Service scope with SupervisorJob
private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

// Thread-safe singleton access
@Volatile private var _instance: Service? = null

// Synchronous persistence in lifecycle methods
override fun onDestroy() {
    runBlocking { repository.saveData(data) }
}
```

### Android SDK Compatibility
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    // Use new API
} else {
    @Suppress("DEPRECATION")
    // Use deprecated fallback
}
```

## Architecture

### State Management
- `ChantState`: Immutable data class as single source of truth
- `StateFlow` for reactive state, collected via `collectAsState()` in UI
- State owned by foreground service, not ViewModels

### Service Architecture
- `ChantingService`: Foreground service with `START_STICKY`
- `VolumeKeyAccessibilityService`: Intercepts volume keys
- Static companion methods: `start()`, `stop()`, `pause()`, `resume()`

### Data Layer
- `PreferencesRepository`: DataStore operations with Flow-based API
- JSON serialization via kotlinx.serialization

### Navigation
- String routes: `"home"`, `"settings"`, `"history"`, `"goals"`
- Animated transitions: `fadeIn/fadeOut` + `slideIntoContainer`

## Package Structure

```
com.superchant/
├── MainActivity.kt              # Entry point, navigation host
├── SuperChantApplication.kt     # Application class
├── data/
│   ├── ChantState.kt           # State models (ChantState, Goal, SessionRecord)
│   └── PreferencesRepository.kt # DataStore persistence
├── service/
│   ├── ChantingService.kt      # Foreground counting service
│   └── VolumeKeyAccessibilityService.kt
└── ui/
    ├── components/             # Reusable composables
    ├── screens/                # Screen-level composables
    └── theme/                  # Material 3 theming (Color, Theme, Type)
```

## Key Dependencies

- **Compose**: UI, Material3, Navigation, Animation
- **Lifecycle**: Runtime KTX, ViewModel Compose, Service
- **DataStore**: Preferences for persistence
- **Kotlinx**: Serialization JSON, Coroutines Android
- **Reorderable**: `org.burnoutcrew.composereorderable:reorderable:0.9.6`

## Testing

- Unit tests: `app/src/test/kotlin/com/superchant/`
- Instrumentation tests: `app/src/androidTest/kotlin/com/superchant/`
- Frameworks: JUnit 4, Espresso, Compose UI Test

## Important Notes

1. All user-facing strings must be in `res/values/strings.xml`
2. ProGuard rules in `app/proguard-rules.pro` preserve serialization
3. Release signing config loaded from `local.properties` (gitignored)
4. Accessibility service config in `res/xml/accessibility_service_config.xml`
5. App distributed via Google Play Store Internal Testing (bypasses Restricted Settings)
