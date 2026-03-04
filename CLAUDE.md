# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**3citycommuter** is an Android app for commuters in Gdańsk, Sopot, and Gdynia (Tri-City area). It displays public transport stops on a map, real-time departures, vehicle tracking, and route visualization.

## Essential Commands

### Building
```bash
./gradlew build                    # Build all modules
./gradlew assembleDebug            # Build debug APK
./gradlew assembleRelease          # Build release APK (requires signing config)
```

### Testing
```bash
./gradlew test                     # Run all unit tests
./gradlew testDebugUnitTest        # Run app module unit tests (debug)
./gradlew connectedDebugAndroidTest # Run instrumented tests on device/emulator
```

**Run a single test class:**
```bash
./gradlew test --tests "pl.bkacala.threecitycommuter.ui.screen.map.MapScreenViewModelTest"
```

### Code Quality
```bash
./gradlew ktlintCheck              # Check Kotlin code style
./gradlew ktlintFormat             # Auto-fix Kotlin style issues
./gradlew spotlessCheck            # Check formatting (Kotlin + misc files)
./gradlew spotlessApply            # Auto-fix all formatting
./gradlew lint                     # Run Android lint
./gradlew lintFix                  # Apply safe lint fixes
```

**Always run before committing:**
```bash
./gradlew spotlessApply ktlintFormat test
```

## Architecture

### Module Structure
The project uses a **multi-module architecture** with clear separation:

```
3citycommuter/
├── app/              UI layer (Compose, ViewModels, Navigation)
├── data/             Repository implementations, domain models, use cases
├── network/          Network client (Ktor), DTOs, API definitions
└── database/         Room database, DAOs, entities
```

**Dependency flow:** `app` → `data` → `network` + `database`

### UI Architecture (app module)

**Pattern:** MVVM with Jetpack Compose + Unidirectional Data Flow

**Key Components:**
- `MainActivity.kt` - Single activity, handles location permission, splash screen
- `AppNavHost.kt` - Navigation graph with nested navigation
- `Destinations.kt` - Enum-based navigation destinations (Map, Schedule, Lines)

**Main Screen:**
- `MapScreen.kt` / `MapScreenViewModel.kt` - Google Maps with bus stops, departures, vehicle tracking
  - Location: `app/src/main/kotlin/pl/bkacala/threecitycommuter/ui/screen/map/`
  - State management: `MutableStateFlow` for screen state
  - Lifecycle awareness: Pauses/resumes polling jobs on Activity lifecycle
  - Real-time updates: Polls every 10-30 seconds for departures and vehicle positions

**State Pattern:**
```kotlin
sealed class UiState<out T> {
    data object Loading : UiState<Nothing>()
    data class Error(val error: Throwable) : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
}
```

### Data Layer Architecture (data module)

**Repository Pattern** with interfaces in `data/repository/`:
- `BusStopsRepository` - Bus stops, departures (cached in DB if >1 day old)
- `LocationRepository` - User location via Google Play Services
- `VehiclesRepository` - Vehicle data, real-time GPS positions
- `RoutesRepository` - Route shapes (GeoPoints for polyline visualization)
- `LastUpdateRepository` - Cache invalidation timestamps

**Key Pattern:** All repositories return `Flow<T>` for reactive updates.

**Dependency Injection:**
- All repositories bound in `RepositoryModule.kt` as singletons
- ViewModels use `@HiltViewModel` with constructor injection
- Activities/Fragments use `@AndroidEntryPoint`

### Network Layer (network module)

**NetworkClient interface** implemented by `KtorNetworkClient`:
- HTTP client: Ktor with JSON serialization, logging
- APIs:
  - `ckan.multimediagdansk.pl` - Bus stops master data
  - `ckan2.multimediagdansk.pl` - Departures, GPS positions, routes
  - `files.cloudgdansk.pl` - Vehicle details database

**Adding a new endpoint:**
1. Add method to `NetworkClient` interface (in `network/`)
2. Create DTO in `network/model/`
3. Implement in `KtorNetworkClient.kt`
4. Add mapper to domain model in `data/`

### Database Layer (database module)

**Room Database:** `CommuterDatabase`
```
Tables:
├── bus_stops       - Bus stop master data (id, name, location, type)
├── bus_stop_types  - Stop type relations (bus/tram)
└── vehicles        - Vehicle details (type, capacity, amenities)
```

**DAOs:** `BusStopsDao`, `BusStopsTypesDao`, `VehiclesDao`

**Caching strategy:** Data refreshes from network if >1 day old (timestamp tracked in SharedPreferences).

## Common Development Workflows

### Adding a New Screen
1. Create package: `app/src/main/kotlin/pl/bkacala/threecitycommuter/ui/screen/[name]/`
2. Create Composable: `[Name]Screen.kt`
3. Create ViewModel: `[Name]ScreenViewModel.kt` with `@HiltViewModel`
4. Add destination to `Destinations.kt` enum
5. Add route to `AppNavHost.kt`
6. If bottom nav item needed, update `AppNavigationBar.kt`

### Adding a New Repository
1. Create interface in `data/repository/[Name]Repository.kt`
2. Create implementation `Real[Name]Repository.kt` in same folder
3. Add `@Binds` method in `data/di/RepositoryModule.kt`:
```kotlin
@Binds
@Singleton
abstract fun bind[Name]Repository(impl: Real[Name]Repository): [Name]Repository
```
4. Inject into ViewModels via constructor

### Testing

**Unit Tests:**
- ViewModels: Use `Turbine` for Flow testing, `kotlinx-coroutines-test` for `runTest`
- Repositories: Mock dependencies with test doubles
- Example: `app/src/test/java/pl/bkacala/threecitycommuter/ui/screen/map/MapScreenViewModelTest.kt`

**Testing utilities:**
- `MainDispatcherRule` - Sets Main dispatcher to TestDispatcher
- `Turbine` - `Flow.test {}` for asserting emissions
- `Kotest` - `shouldBe`, `shouldContain` assertions

**Mock repositories:** Located in `app/src/test/java/pl/bkacala/threecitycommuter/mocks/`

## Code Style

**Formatting:**
- Spotless enforces ktlint rules + trailing whitespace/indentation
- Ratchet mode: Only formats files changed from `origin/main`
- **Always run `./gradlew spotlessApply` before committing**

**Conventions:**
- Kotlin: 4-space indentation
- Composables: PascalCase (e.g., `MapScreen()`)
- ViewModels: Suffix with `ViewModel` (e.g., `MapScreenViewModel`)
- Repositories: Prefix implementation with `Real` (e.g., `RealBusStopsRepository`)
- Use `sealed class` for state modeling
- Prefer `Flow` over LiveData
- Use `@Composable` functions for UI, extract reusable components

## Key Files to Understand

**Entry points:**
- `app/src/main/kotlin/pl/bkacala/threecitycommuter/CommuterApp.kt` - Hilt application
- `app/src/main/kotlin/pl/bkacala/threecitycommuter/MainActivity.kt` - Single activity
- `app/src/main/kotlin/pl/bkacala/threecitycommuter/AppNavHost.kt` - Navigation graph

**Main feature:**
- `app/src/main/kotlin/pl/bkacala/threecitycommuter/ui/screen/map/MapScreenViewModel.kt` (450+ lines)
- `app/src/main/kotlin/pl/bkacala/threecitycommuter/ui/screen/map/MapScreen.kt`

**Dependency injection:**
- `data/src/main/kotlin/pl/bkacala/threecitycommuter/di/RepositoryModule.kt`
- `network/src/main/kotlin/pl/bkacala/threecitycommuter/di/NetworkModule.kt`
- `database/src/main/kotlin/pl/bkacala/threecitycommuter/di/DatabaseModule.kt`

**Network client:**
- `network/src/main/kotlin/pl/bkacala/threecitycommuter/network/KtorNetworkClient.kt`

## Configuration

**Secrets:** `secrets.properties` (gitignored) contains:
- Maps API key (automatically injected via Secrets Gradle Plugin)
- Signing credentials (PASS, ALIAS, ALIAS_PASS)

**API Keys:**
- Google Maps API key required for map functionality
- Configured via `secrets.properties` and accessed in `AndroidManifest.xml`

## Tech Stack

- **Language:** Kotlin 1.9.22
- **UI:** Jetpack Compose, Material 3
- **Maps:** Google Maps Compose (`com.google.maps.android:maps-compose`)
- **Navigation:** Androidx Compose Navigation
- **DI:** Hilt
- **Networking:** Ktor Client
- **Database:** Room
- **Serialization:** Kotlinx Serialization
- **Async:** Kotlin Coroutines + Flow
- **Location:** Google Play Services Location
- **Permissions:** PermissionFlow library
- **Date/Time:** Kotlinx DateTime
- **Testing:** JUnit, Kotest, Turbine, Coroutines Test

## Target SDK
- **Min SDK:** 29 (Android 10)
- **Target SDK:** 34 (Android 14)
- **Compile SDK:** 34