# AGENTS.md

This file provides guidance to Mistral Vibe when working with code in this repository.

## Project Overview

**3citycommuter** is a **Kotlin Multiplatform** app for commuters in Gdansk, Sopot, Gdynia, and the Tri-City SKM rail network.
It displays public transport stops on a map, real-time departures, vehicle tracking, and route visualization.

**Targets:**
- **Android** (production ready, min SDK 29 / target SDK 37 / compile SDK 37)
- **Desktop (JVM)** (functional, Compose for Desktop)
- **iOS** (configured via KMP targets `iosX64`, `iosArm64`, `iosSimulatorArm64`; `iosApp/` exists but has no app entry point yet)

Data sources:
- **Gdansk**: open data from [Otwarte Dane Gdanska](https://ckan.multimediagdansk.pl) and companion feeds from `ckan2.multimediagdansk.pl` and `files.cloudgdansk.pl`
- **Gdynia**: public API from `api.zdiz.gdynia.pl`
- **SKM**: PLK API for departures and route order, plus local station geometry for map coordinates

**Note:** Active map support uses **MapLibre**. Mapbox dependencies are deprecated and commented out.

## Directory Structure

```text
3citycommuter/
|- build-logic/convention/    # Convention plugins: KmpLibrary, KmpCompose
|- shared/
|  |- core/                   # Domain models and utilities
|  |- network/                # Ktor client, DTOs, transport providers, platform engines
|  |- database/               # Room KMP, DAOs, entities, DB module
|  |- data/                   # Repositories, mappers, use cases, location, permissions
|  `- ui/                     # Compose Multiplatform UI, ViewModels, navigation, screens
|- composeApp/
|  |- android/                # MainActivity, CommuterApp, AndroidManifest
|  `- desktop/                # main.kt, desktop bootstrap
`- iosApp/                    # Empty shell, no app entry point yet
```

**Dependency flow:** `composeApp` + `iosApp` -> `shared:ui` -> `shared:data` -> `shared:network` + `shared:database` -> `shared:core`

## Essential Commands

### Building

```bash
./gradlew build
./gradlew :composeApp:android:assembleDebug
./gradlew :composeApp:android:assembleRelease
./gradlew :composeApp:desktop:run
```

**Note:** Project uses **Gradle 9.4.1** and **Android Gradle Plugin 9.2.1**.

### Testing

```bash
./gradlew :shared:ui:jvmTest
./gradlew :shared:network:jvmTest
./gradlew :composeApp:android:connectedDebugAndroidTest
```

**Run a single test class:**

```bash
./gradlew :shared:ui:jvmTest --tests "pl.bkacala.threecitycommuter.ui.screen.map.MapScreenViewModelTest"
./gradlew :shared:network:jvmTest --tests "pl.bkacala.threecitycommuter.client.TransitDataSourceTest"
```

### Code Quality

```bash
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew detekt
./gradlew lint
./gradlew check
```

`lint` runs Spotless and Detekt and is the main local quality gate.

## Architecture

### KMP Targets

Configured via `KmpLibraryConventionPlugin` in `build-logic/convention/`:
- **Android**: `com.android.kotlin.multiplatform.library` with `minSdk = 29` and `compileSdk = 37`
- **Desktop**: `jvm("jvm")`
- **iOS**: `iosX64()`, `iosArm64()`, `iosSimulatorArm64()`

The Android application module uses:
- `compileSdk = 37`
- `targetSdk = 37`
- `minSdk = 29`

### Convention Plugins

- `threecitycommuter.kmp.library` applies `org.jetbrains.kotlin.multiplatform` and `com.android.kotlin.multiplatform.library`
- `threecitycommuter.kmp.compose` applies Compose Multiplatform and the Compose compiler plugin

### UI Architecture

**Pattern:** MVVM with Compose Multiplatform and unidirectional data flow

**Key Components:**
- `App.kt` (`shared/ui/commonMain`) - root composable, scaffold, nav host
- `AppNavHost.kt` - navigation graph
- `MapScreen.kt` / `MapScreenViewModel.kt` - main map screen
- `PlatformMapView.kt` - `expect` declaration with platform `actual` implementations

Current map implementations:
- `androidMain` - Canvas placeholder
- `jvmMain` - Canvas placeholder with stop dots and route lines
- `iosMain` - stub placeholder

**State Pattern (`shared/core/.../UiState.kt`):**

```kotlin
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    class Error(val exception: Throwable) : UiState<Nothing>
    class Success<T>(val data: T) : UiState<T>
}
```

### Data Layer

Repository interfaces live in `shared/data/commonMain`.
All repositories return `Flow<T>`.

Main repositories:
- `BusStopsRepository`
- `LocationRepository`
- `VehiclesRepository`
- `RoutesRepository`
- `LastUpdateRepository`

### Dependency Injection

Koin modules:

| Module | Location | Contents |
|---|---|---|
| `databaseModule` | `shared/database` | Room DB, DAOs |
| `networkModule` | `shared/network` | Ktor client, JSON config, transport providers |
| `platformNetworkModule` | `shared/network` | Platform HTTP engine |
| `dataModule` | `shared:data` | Repositories, use cases, settings |
| `platformDataModule` | `shared:data` | Platform location and permissions |
| `uiModule` | `shared:ui` | ViewModels |

### Network Layer

`KtorNetworkClient` implements the legacy Gdansk-specific `NetworkClient`.

Transport data is normalized through a provider layer:
- `GdanskTransitDataSource`
- `GdyniaTransitDataSource`
- `SkmTransitDataSource`
- `CombinedTransitDataSource`

`CombinedTransitDataSource` is the application-facing source. It merges Gdansk, Gdynia, and SKM data and routes later lookups by provider based on app-level stop keys.

#### Data parsing and normalization

- App-level stop identity is represented by `TransitStopKey(provider, sourceStopId)`.
- `BusStopData.provider` and `BusStopData.sourceStopId` are first-class fields and are also used by persistence.
- `Departure.lineNumber` is the UI-facing line label:
  - Gdansk uses `routeId.toString()`
  - Gdynia uses `/pt/routes.routeShortName`
  - SKM uses the line label provided by the PLK-backed provider
- `Departure.routeId` remains the provider-internal route identifier and must still be used for route lookup.
- Gdynia route geometry is parsed from GTFS:
  - `/pt/trips` provides `tripId -> shapeId`
  - `shapes.txt` from `gtfs.zip` provides `shapeId -> ordered coordinates`
  - `GdyniaGtfsStore` caches the parsed route index in memory with a 1-day TTL
  - Android preloads the Gdynia GTFS cache during startup in the same background phase that loads `relations.json`
- Gdynia does not expose a public live GPS feed compatible with the Gdansk one:
  - route drawing works
  - live vehicle tracking is disabled in UI behavior
- SKM uses the PLK API authenticated with `PLK_KEY` for departures and planned route metadata:
  - local station geometry is still kept in-repo because PLK does not expose stop coordinates
  - SKM stops are intentionally not clustered on the map
  - SKM stops use a dedicated visual style distinct from bus and tram stops
- Android allows cleartext HTTP specifically for `api.zdiz.gdynia.pl` through `composeApp/android/src/main/res/xml/network_security_config.xml`

#### Error logging

- `MapScreenViewModel` logs failures for stops, departures, routes, and vehicle position loading.
- `GdyniaGtfsStore` logs GTFS download and parse failures.

Platform engines:
- Android: `HttpClient(Android)`
- Desktop: `HttpClient(Java)`
- iOS: `HttpClient(Darwin)`

### Database Layer

Room KMP 2.7.2 with `@ConstructedBy(CommuterDatabaseConstructor::class)`.

`getDatabaseBuilder()` is platform-specific:
- Android: Room + `Context`
- Desktop: Room + file
- iOS: Room + `NSFileManager`

### Platform Abstractions

| Interface | Android actual | Desktop actual | iOS actual |
|---|---|---|---|
| `PlatformMapView` | Canvas placeholder | Canvas placeholder with stop dots and route lines | Stub |
| `getDatabaseBuilder()` | Room + Context | Room + File | Room + NSFileManager |
| `platformNetworkModule` | Ktor Android engine | Ktor Java engine | Ktor Darwin engine |
| `platformDataModule` | FusedLocation + PermissionChecker | Default location + always granted | Stub |
| `PermissionChecker` | `ContextCompat.checkSelfPermission` | Always true | Stub |

## Common Development Workflows

### Adding a New Screen

1. Create package: `shared/ui/src/commonMain/.../ui/screen/[name]/`
2. Create `[Name]Screen.kt`
3. Create `[Name]ScreenViewModel.kt`
4. Register ViewModel in `shared/ui/.../di/UiModule.kt`
5. Add destination to `Destinations.kt`
6. Add route to `AppNavHost.kt`

### Adding a New Repository

1. Create interface in `shared:data/.../repository/[Name]Repository.kt`
2. Create `Real[Name]Repository.kt`
3. Register it in `shared:data/.../di/DataModule.kt`

### Adding a New API Endpoint

1. Add method to the appropriate transport provider or `NetworkClient`
2. Create DTO in `shared/network/.../model/`
3. Implement the call in the provider/client
4. Add or update mapper logic in shared domain/data code

## Testing

**Unit tests** live in `commonTest` and run with `:module:jvmTest`.

- ViewModels: `shared/ui/src/commonTest/`
- Serialization/provider tests: `shared/network/src/commonTest/`
- Test doubles: `shared/ui/src/commonTest/.../mocks/`

Important network/provider tests:
- `TransitDataSourceTest`
  - verifies Gdynia GTFS route parsing preserves point order
  - verifies Gdynia maps `routeId` to user-facing `lineNumber`
  - verifies Gdansk, Gdynia, and SKM providers populate a consistent shared domain model for stops and departures
- `GdyniaRouteNetworkDataSerializationTest`
  - verifies `/pt/routes` payload deserialization

Use `@BeforeTest` and `@AfterTest` with:

```kotlin
Dispatchers.setMain(UnconfinedTestDispatcher())
// test code
Dispatchers.resetMain()
```

## Configuration

### Map Configuration

MapLibre is the active map stack.
Basic usage does not require a token.
Current placeholder maps do not require external credentials.

### Signing (Android)

Local release builds use `secrets.properties` and `signing/key.jks`.

`secrets.properties`:

```properties
PASS=keystore_password
ALIAS=key_alias
ALIAS_PASS=key_password
PLK_KEY=your_plk_api_key
```

CI can use environment variables instead:

```properties
ANDROID_SIGNING_STORE_FILE=/path/to/key.jks
ANDROID_SIGNING_STORE_PASSWORD=...
ANDROID_SIGNING_KEY_ALIAS=...
ANDROID_SIGNING_KEY_PASSWORD=...
PLK_KEY=...
ANDROID_VERSION_CODE=123
ANDROID_VERSION_NAME=2.1+123
```

### GitHub Actions

Configured workflows:
- PR verification for branches targeting `main`
- signed release build on `main` push and manual dispatch

Required GitHub secrets for signed CI builds:
- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

## Tech Stack

| Layer | Technology | Version |
|-------|------------|---------|
| Language | Kotlin Multiplatform | 2.3.21 |
| Build | AGP | 9.2.1 |
| UI | Compose Multiplatform | 1.10.3 |
| UI | Material 3 | 1.10.0-alpha05 |
| Maps | MapLibre Compose | 0.13.0 |
| Maps | MapLibre Native Android SDK | 13.3.1 |
| Navigation | JetBrains Navigation Compose | 2.9.2 |
| DI | Koin | 4.0.2 |
| Networking | Ktor Client | 3.1.1 |
| Database | Room KMP | 2.7.2 |
| Serialization | kotlinx-serialization | 1.8.0 |
| Async | kotlinx-coroutines + Flow | 1.10.1 |
| Date/Time | kotlinx-datetime | 0.6.2 |
| Settings | multiplatform-settings | 1.3.0 |
| Quality | Spotless | 6.25.0 |
| Quality | Detekt | 1.23.6 |

## Safety Notes for Mistral Vibe

1. Do not commit secrets. `secrets.properties` is gitignored.
2. Android release signing requires `secrets.properties` and `signing/key.jks`, or equivalent CI environment variables.
3. `iosApp/` has no app entry point yet. Do not assume iOS is runnable.
4. Use `./gradlew` on Unix-like systems and `gradlew.bat` on Windows shells.
5. Respect existing Kotlin formatting and run `./gradlew lint` before committing.
6. Instrumented Android tests require a device or emulator.
7. Commands such as `./gradlew clean`, recursive deletes, and force-pushes require explicit confirmation.

## Quality Notes

1. Run `./gradlew lint` before committing.
2. Spotless covers Kotlin, Gradle Kotlin DSL, Markdown, YAML, and `.gitignore`.
3. Detekt reports are generated under module `build/reports/detekt/`.
4. `gradlew` must remain executable in Git for Linux CI runners.
