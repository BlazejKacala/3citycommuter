# 3CityCommuter Skill

This skill provides specialized knowledge and workflows for developing the 3citycommuter Kotlin Multiplatform app.

## Overview

**3citycommuter** is a Kotlin Multiplatform app for commuters in Gdańsk, Sopot, and Gdynia (Tri-City area, Poland). It displays public transport stops on a map, real-time departures, vehicle tracking, and route visualization.

**Targets:**
- Android (production ready, min SDK 29 / Android 10, target SDK 35 / Android 15)
- Desktop (JVM) - functional, Compose for Desktop
- iOS (configured via KMP targets, platform-specific code in `shared/*/iosMain` via `expect/actual`)

**Data Sources:** Open data from [Otwarte Dane Gdanska](https://ckan.multimediagdansk.pl)

## Quick Access

### Essential Commands

**Building:**
```bash
./gradlew build                                      # Build all modules
./gradlew :composeApp:android:assembleDebug          # Build debug APK
./gradlew :composeApp:android:assembleRelease        # Build release APK (requires signing config)
./gradlew :composeApp:desktop:run                    # Run desktop app
```

**Testing:**
```bash
./gradlew :shared:ui:jvmTest                         # ViewModel tests (commonTest, runs on JVM)
./gradlew :shared:network:jvmTest                    # Serialization tests
./gradlew :composeApp:android:connectedDebugAndroidTest  # Instrumented tests
```

**Run a single test class:**
```bash
./gradlew :shared:ui:jvmTest --tests "pl.bkacala.threecitycommuter.ui.screen.map.MapScreenViewModelTest"
```

**Code Quality:**
```bash
./gradlew spotlessCheck    # Check formatting
./gradlew spotlessApply    # Auto-fix formatting
./gradlew detekt           # Static analysis
./gradlew lint             # All quality checks (spotless + detekt)
```

## Architecture

### Module Structure

```
3citycommuter/
├── build-logic/convention/    # Convention plugins (KmpLibrary, KmpCompose)
├── shared/
│   ├── core/                  # Domain models (LatLng, UiState, UserLocation, BusStopData, etc.), utilities (commonMain only)
│   ├── network/               # Ktor client, DTOs, NetworkClient interface, platform engines (commonMain + androidMain/jvmMain/iosMain)
│   ├── database/              # Room KMP, DAOs, entities, CommuterDatabase, DatabaseModule (commonMain + per-platform builders)
│   ├── data/                  # Repositories (interfaces + Real* implementations), use cases, mappers, location/permissions (commonMain + per-platform)
│   └── ui/                    # Compose Multiplatform UI, ViewModels, navigation, screens (commonMain + per-platform MapView)
├── composeApp/
│   ├── android/               # MainActivity, CommuterApp (Koin init), AndroidManifest
│   └── desktop/               # main.kt (Window + Koin init)
└── iosApp/                    # Empty directory (iOS app entry point not yet implemented)
```

**Dependency flow:** `composeApp` + `iosApp` → `shared:ui` → `shared:data` → `shared:network` + `shared:database` → `shared:core`

### Convention Plugins

- `threecitycommuter.kmp.library` — applies `kotlin-multiplatform` + `com.android.library`, configures Android, JVM, and iOS targets
- `threecitycommuter.kmp.compose` — applies Compose Multiplatform plugin + compiler plugin

### UI Architecture

**Pattern:** MVVM with Compose Multiplatform + Unidirectional Data Flow

**Key Components:**
- `App.kt` (`shared/ui/commonMain`) — root composable, Scaffold + NavHost
- `AppNavHost.kt` — navigation graph (JetBrains KMP Navigation Compose)
- `MapScreen.kt` / `MapScreenViewModel.kt` — main screen with map, departures, vehicle tracking
- `PlatformMapView.kt` — `expect fun` with `actual` per platform:
  - `androidMain` — Canvas placeholder (Mapbox deps commented out; requires `MAPBOX_DOWNLOADS_TOKEN`)
  - `jvmMain` — Canvas placeholder with stop dots and route lines
  - `iosMain` — Stub with placeholder text

**State Pattern (`shared/core/src/commonMain/kotlin/.../ui/common/UiState.kt`):**
```kotlin
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    class Error(val exception: Throwable) : UiState<Nothing>
    class Success<T>(val data: T) : UiState<T>
}
```

All ViewModels use `Flow<UiState<T>>` pattern with `asUiState()` extension for automatic loading/error handling.

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
| `databaseModule` | `shared/database` | Room DB, DAOs |
| `networkModule` | `shared/network` | Ktor client (common), Json config |
| `platformNetworkModule` | `shared/network` (expect/actual) | HTTP engine per platform (Android/Darwin/Java) |
| `dataModule` | `shared/data` | Repositories, use cases, Settings |
| `platformDataModule` | `shared/data` (expect/actual) | LocationRepository, PermissionChecker per platform |
| `uiModule` | `shared/ui` | ViewModels |

All modules started in `CommuterApp.kt` (Android) or `main.kt` (Desktop).

### Network Layer

`KtorNetworkClient` in `shared/network/commonMain` implements `NetworkClient` interface. Platform engine injected via `platformNetworkModule`:
- Android: `HttpClient(Android)`
- iOS: `HttpClient(Darwin)`
- Desktop: `HttpClient(Java)`

### Database Layer

Room KMP 2.7 with `@ConstructedBy(CommuterDatabaseConstructor::class)` pattern. `getDatabaseBuilder()` is `expect fun` with `actual` in `androidMain` (Room + Context), `jvmMain` (Room + File), `iosMain` (Room + NSFileManager).

### Platform Abstractions (expect/actual)

| Interface | Android actual | Desktop actual | iOS actual |
|---|---|---|---|
| `PlatformMapView` | Canvas placeholder | Canvas placeholder | Stub |
| `getDatabaseBuilder()` | Room + Context | Room + File | Room + NSFileManager |
| `platformNetworkModule` | Ktor Android engine | Ktor Java engine | Ktor Darwin engine |
| `platformDataModule` | FusedLocation + PermissionChecker | Default location + always granted | Stub |
| `PermissionChecker` | ContextCompat.checkSelfPermission | Always true | Stub |

## Common Development Workflows

### Adding a New Screen

1. Create package: `shared/ui/src/commonMain/.../ui/screen/[name]/`
2. Create `[Name]Screen.kt` (Composable)
3. Create `[Name]ScreenViewModel.kt` (plain `ViewModel`, no framework-specific DI)
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

**No JUnit `@Rule`** — use `@BeforeTest`/`@AfterTest` with:
```kotlin
Dispatchers.setMain(UnconfinedTestDispatcher())
// ... test code ...
Dispatchers.resetMain()
```

**Testing utilities:**
- `makeRandomInstance<T>()` — reflection-based random instance creation (`shared/ui/src/commonTest/.../tools/RandomInstance.kt`)
- `MockPermissionChecker` — test double (granted/denied variants)
- Turbine: `flow.test { awaitItem() shouldBe expected }`

## Configuration

### Mapbox Token

Required for Android map (add to `gradle.properties`):
```properties
MAPBOX_DOWNLOADS_TOKEN=sk.eyJ1...
```
Without token: Android and Desktop builds use Canvas placeholder map. Mapbox deps are commented out in `shared/ui/build.gradle.kts`.

### Signing (Android)

`secrets.properties` (gitignored):
```properties
PASS=keystore_password
ALIAS=key_alias
ALIAS_PASS=key_password
```
Keystore file expected at `signing/key.jks`.

## Tech Stack

| Layer | Technology | Version |
|-------|------------|---------|
| Language | Kotlin + Kotlin Multiplatform | 2.1.10 |
| UI | Compose Multiplatform + Material 3 | 1.7.3 |
| Maps | Mapbox Maps SDK (Android, optional) / Canvas placeholder | 11.9.2 |
| Navigation | JetBrains Navigation Compose (KMP) | 2.9.0 |
| DI | Koin | 4.0.2 |
| Networking | Ktor Client | 3.1.1 |
| Database | Room KMP | 2.7.1 |
| Serialization | Kotlinx Serialization | 1.8.0 |
| Async | Kotlin Coroutines + Flow | 1.10.1 |
| Date/Time | Kotlinx DateTime | 0.6.2 |
| Settings | multiplatform-settings | 1.3.0 |
| Testing | kotlin.test, Kotest assertions, Turbine, Coroutines Test | - |

## Safety Notes

1. **Do NOT commit secrets** — `secrets.properties` and `MAPBOX_DOWNLOADS_TOKEN` are gitignored. Never add them to commits.
2. **Android signing** — release builds require `secrets.properties` and `signing/key.jks`. Do not attempt release builds without these.
3. **iOS limitations** — `iosApp/` is empty. iOS target is configured in KMP but has no entry point. Do not attempt iOS builds.
4. **Mapbox** — Without `MAPBOX_DOWNLOADS_TOKEN`, Android uses Canvas placeholder. Do not uncomment Mapbox dependencies without a valid token.
5. **Gradle commands** — Always use `./gradlew` (not `gradlew.bat`) on Linux/macOS. Project uses Gradle version catalog (`gradle/libs.versions.toml`).
6. **File modifications** — Respect existing code style: 4-space indentation, ktlint-compatible formatting.
7. **Test execution** — Instrumented tests require Android device/emulator. Use `jvmTest` for fast feedback.
8. **Blast radius** — Commands like `./gradlew clean`, `rm -rf`, or git force-push require explicit confirmation.

## API Reference

See the bundled resource file: `api_reference.md`

This contains comprehensive documentation for all ZTM Gdańsk API endpoints used by the app.
