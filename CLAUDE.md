# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**3citycommuter** is a **Kotlin Multiplatform** app for commuters in Gdańsk, Sopot, and Gdynia (Tri-City area). It displays public transport stops on a map, real-time departures, vehicle tracking, and route visualization. Targets: **Android**, **Desktop (JVM)**, **iOS** (stub).

## Essential Commands

### Building
```bash
./gradlew build                                      # Build all modules
./gradlew :composeApp:android:assembleDebug          # Build debug APK
./gradlew :composeApp:android:assembleRelease        # Build release APK (requires signing config)
./gradlew :composeApp:desktop:run                    # Run desktop app
```

### Testing
```bash
./gradlew :shared:ui:jvmTest                         # ViewModel tests (fast, no device needed)
./gradlew :shared:network:jvmTest                    # Serialization tests
./gradlew :composeApp:android:connectedDebugAndroidTest  # Instrumented tests on device/emulator
```

**Run a single test class:**
```bash
./gradlew :shared:ui:jvmTest --tests "pl.bkacala.threecitycommuter.ui.screen.map.MapScreenViewModelTest"
```

### Code Quality
```bash
./gradlew spotlessApply ktlintFormat    # Auto-fix formatting (always run before committing)
./gradlew spotlessCheck ktlintCheck     # Check only
./gradlew lint                          # Android lint
```

## Architecture

### Module Structure

```
3citycommuter/
├── build-logic/convention/    # Convention plugins (KmpLibrary, KmpCompose)
├── shared/
│   ├── core/                  # Domain models, LatLng, UiState, utilities (commonMain only)
│   ├── network/               # Ktor client, DTOs, platform engines (commonMain + per-platform)
│   ├── database/              # Room KMP, DAOs, entities, DatabaseModule (commonMain + per-platform builders)
│   ├── data/                  # Repositories, mappers, use cases, location/permissions (commonMain + per-platform)
│   └── ui/                    # Compose Multiplatform UI, ViewModels, navigation (commonMain + per-platform MapView)
└── composeApp/
    ├── android/               # MainActivity, CommuterApp (Koin init), AndroidManifest
    └── desktop/               # main.kt (Window + Koin init)
```

**Dependency flow:** `composeApp` → `shared:ui` → `shared:data` → `shared:network` + `shared:database` → `shared:core`

### KMP Targets

- **Android**: `androidTarget()` via `KmpLibraryConventionPlugin`
- **Desktop**: `jvm("jvm")` via `KmpLibraryConventionPlugin`
- **iOS**: `iosX64()`, `iosArm64()`, `iosSimulatorArm64()` (disabled on Windows builds)

### Convention Plugins (build-logic/convention/)

- `threecitycommuter.kmp.library` — applies `kotlin-multiplatform` + `com.android.library`, configures all targets
- `threecitycommuter.kmp.compose` — applies Compose Multiplatform plugin + compiler plugin

### UI Architecture

**Pattern:** MVVM with Compose Multiplatform + Unidirectional Data Flow

**Key Components:**
- `App.kt` (`shared/ui/commonMain`) — root composable, Scaffold + NavHost
- `AppNavHost.kt` — navigation graph (JetBrains KMP Navigation)
- `MapScreen.kt` / `MapScreenViewModel.kt` — main screen with map, departures, vehicle tracking
- `PlatformMapView.kt` — `expect fun` with `actual` per platform:
  - `androidMain` — Mapbox Compose SDK (requires `MAPBOX_DOWNLOADS_TOKEN`) / Canvas placeholder
  - `jvmMain` — Canvas placeholder with stop dots and route lines
  - `iosMain` — TODO stub (UIKitView interop)

**State Pattern:**
```kotlin
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Error(val error: Throwable) : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
}
```

### Data Layer

**Repository Pattern** with interfaces in `shared/data/commonMain`:
- `BusStopsRepository` — bus stops + departures (cached in Room if > 1 day old)
- `LocationRepository` — user location (`expect`/`actual` per platform)
- `VehiclesRepository` — vehicle data + real-time GPS positions
- `RoutesRepository` — route shapes
- `LastUpdateRepository` — cache invalidation via `multiplatform-settings`

**All repositories return `Flow<T>`.**

### Dependency Injection (Koin 4.0)

Modules declared per layer:

| Module | Location | Contents |
|---|---|---|
| `databaseModule` | `shared:database` | Room DB, DAOs |
| `networkModule` | `shared:network` | Ktor client (common) |
| `platformNetworkModule` | `shared:network` (expect/actual) | HTTP engine per platform |
| `dataModule` | `shared:data` | Repositories, use cases, Settings |
| `platformDataModule` | `shared:data` (expect/actual) | LocationRepository, PermissionChecker per platform |
| `uiModule` | `shared:ui` | ViewModels |

All modules started in `CommuterApp.kt` (Android) or `main.kt` (Desktop).

### Network Layer

`KtorNetworkClient` in `shared:network/commonMain` — platform engine injected via `platformNetworkModule`:
- Android: `HttpClient(Android)`
- iOS: `HttpClient(Darwin)`
- Desktop: `HttpClient(Java)`

### Database Layer

Room KMP 2.7 with `@ConstructedBy(CommuterDatabaseConstructor::class)` pattern.
`getDatabaseBuilder()` is `expect fun` with `actual` in `androidMain`, `jvmMain`, `iosMain`.

### Platform Abstractions (expect/actual)

| Interface | Android actual | Desktop actual | iOS actual |
|---|---|---|---|
| `PlatformMapView` | Mapbox / Canvas placeholder | Canvas placeholder | Stub |
| `getDatabaseBuilder()` | Room + Context | Room + File | Room + NSFileManager |
| `platformNetworkModule` | Ktor Android engine | Ktor Java engine | Ktor Darwin engine |
| `platformDataModule` | FusedLocation | Default location | Stub |
| `PermissionChecker` | ContextCompat | Always true | Stub |

## Common Development Workflows

### Adding a New Screen
1. Create package: `shared/ui/src/commonMain/.../ui/screen/[name]/`
2. Create `[Name]Screen.kt` (Composable)
3. Create `[Name]ScreenViewModel.kt` (plain `ViewModel`, no Hilt)
4. Register ViewModel in `shared/ui/.../di/UiModule.kt`: `viewModel { NameScreenViewModel(get(), ...) }`
5. Add destination to `Destinations.kt`
6. Add route to `AppNavHost.kt`

### Adding a New Repository
1. Create interface in `shared/data/.../repository/[Name]Repository.kt`
2. Create `Real[Name]Repository.kt` in same folder
3. Add to `dataModule` in `shared/data/.../di/DataModule.kt`:
   ```kotlin
   single<NameRepository> { RealNameRepository(get()) }
   ```

### Adding a New API Endpoint
1. Add method to `NetworkClient` interface (`shared/network/commonMain`)
2. Create DTO in `shared/network/.../model/`
3. Implement in `KtorNetworkClient.kt`
4. Add mapper in `shared/data/.../model/`

## Testing

**Unit Tests** live in `commonTest` — run on JVM with `./gradlew :module:jvmTest`:
- ViewModels: `shared/ui/src/commonTest/` — Turbine for Flow testing, `kotlin.test`
- Serialization: `shared/network/src/commonTest/`
- Mock repositories: `shared/ui/src/commonTest/.../mocks/`

**No JUnit `@Rule`** — use `@BeforeTest`/`@AfterTest` with `Dispatchers.setMain(UnconfinedTestDispatcher())`.

**Testing utilities:**
- `makeRandomInstance<T>()` — reflection-based random instance creation (`tools/RandomInstance.kt`)
- `MockPermissionChecker` — `PermissionChecker` test double (granted/denied variants)
- Turbine: `flow.test { awaitItem() shouldBe expected }`

## Configuration

**Mapbox token** — required for Android map (add to `gradle.properties`):
```properties
MAPBOX_DOWNLOADS_TOKEN=sk.eyJ1...
```
Without token: Android builds use Canvas placeholder map (Mapbox deps are commented out in `shared/ui/build.gradle.kts`).

**Signing** — `secrets.properties` (gitignored):
```properties
PASS=keystore_password
ALIAS=key_alias
ALIAS_PASS=key_password
```

## Tech Stack

- **Language:** Kotlin 2.1.10 + Kotlin Multiplatform
- **UI:** Compose Multiplatform 1.7.3, Material 3
- **Maps:** Mapbox Maps SDK 11.9.2 (Android, requires token)
- **Navigation:** JetBrains Navigation Compose 2.9.0 (KMP)
- **DI:** Koin 4.0.2
- **Networking:** Ktor 3.1.1
- **Database:** Room KMP 2.7.1
- **Serialization:** Kotlinx Serialization 1.8.0
- **Async:** Kotlin Coroutines 1.10.1 + Flow
- **Date/Time:** Kotlinx DateTime 0.6.2
- **Settings:** multiplatform-settings 1.3.0
- **Testing:** kotlin.test, Kotest assertions, Turbine, Coroutines Test

## Target SDKs (Android)
- **Min SDK:** 29 (Android 10)
- **Target SDK:** 35 (Android 15)
- **Compile SDK:** 35
