# CLAUDE.md

This file provides guidance to Claude Code when working with code in this repository.

## Project overview

`3citycommuter` is a Kotlin Multiplatform app for commuters in Gdansk, Sopot, Gdynia, and the Tri-City SKM rail network. It displays stops on a map, real-time departures, vehicle tracking, and route visualization.

Targets:
- Android
- Desktop (JVM)
- iOS stub

## Essential commands

### Build

```bash
./gradlew build
./gradlew :composeApp:android:assembleDebug
./gradlew :composeApp:android:assembleRelease
./gradlew :composeApp:desktop:run
```

### Test

```bash
./gradlew :shared:ui:jvmTest
./gradlew :shared:network:jvmTest
./gradlew :composeApp:android:connectedDebugAndroidTest
```

Run a single test class:

```bash
./gradlew :shared:ui:jvmTest --tests "pl.bkacala.threecitycommuter.ui.screen.map.MapScreenViewModelTest"
./gradlew :shared:network:jvmTest --tests "pl.bkacala.threecitycommuter.client.TransitDataSourceTest"
```

### Code quality

```bash
./gradlew spotlessApply
./gradlew spotlessCheck
./gradlew detekt
./gradlew lint
./gradlew check
```

## Architecture

### Module structure

```text
3citycommuter/
|- build-logic/convention/
|- shared/
|  |- core/
|  |- network/
|  |- database/
|  |- data/
|  `- ui/
|- composeApp/
|  |- android/
|  `- desktop/
`- iosApp/
```

Dependency flow:
`composeApp` + `iosApp` -> `shared:ui` -> `shared:data` -> (`shared:network` + `shared:database`) -> `shared:core`

### KMP targets

- Android libraries use `com.android.kotlin.multiplatform.library` in the convention plugin
- Android app module uses `com.android.application`
- Desktop target is `jvm("jvm")`
- iOS targets are `iosX64`, `iosArm64`, and `iosSimulatorArm64`

Shared Android target settings from the convention plugin:
- `minSdk = 29`
- `compileSdk = 37`

Android app settings:
- `minSdk = 29`
- `targetSdk = 37`
- `compileSdk = 37`

### Convention plugins

- `threecitycommuter.kmp.library` applies `org.jetbrains.kotlin.multiplatform` and `com.android.kotlin.multiplatform.library`
- `threecitycommuter.kmp.compose` applies Compose Multiplatform and the Compose compiler plugin

### UI architecture

Pattern:
- MVVM
- Compose Multiplatform
- unidirectional data flow

Key files:
- `shared/ui/.../App.kt`
- `shared/ui/.../AppNavHost.kt`
- `shared/ui/.../MapScreen.kt`
- `shared/ui/.../MapScreenViewModel.kt`
- `shared/ui/.../PlatformMapView.kt`

Current map implementation:
- Android: Canvas placeholder
- Desktop: Canvas placeholder with stop dots and route lines
- iOS: stub placeholder

### State pattern

```kotlin
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    class Error(val exception: Throwable) : UiState<Nothing>
    class Success<T>(val data: T) : UiState<T>
}
```

### Data layer

Repositories expose `Flow<T>`.

Main repositories:
- `TransitStopsRepository`
- `LocationRepository`
- `VehiclesRepository`
- `RoutesRepository`
- `LastUpdateRepository`

### Dependency injection

Koin modules:
- `databaseModule`
- `networkModule`
- `platformNetworkModule`
- `dataModule`
- `platformDataModule`
- `uiModule`

Android starts Koin in `composeApp/android`. Desktop starts Koin in `composeApp/desktop`.

### Network layer

`KtorNetworkClient` implements the legacy Gdansk-specific `NetworkClient`.

Transport provider architecture:
- `GdanskTransitDataSource` adapts the legacy Gdansk feeds
- `GdyniaTransitDataSource` adapts the Gdynia API
- `SkmTransitDataSource` adapts the current SKM mock feed and is designed to switch to PLK API data later
- `CombinedTransitDataSource` merges all providers into one application-facing source

Data normalization rules:
- app-level stop IDs are represented by `TransitStopKey(provider, sourceStopId)`
- `TransitStopData.provider` and `TransitStopData.sourceStopId` are persisted and used directly
- `Departure.lineNumber` is the display label used by UI
- `Departure.routeId` remains the provider-internal route identifier for route lookup
- Gdynia line labels must come from `/pt/routes.routeShortName`, not from raw `routeId`
- Gdynia route shapes come from `gtfs.zip`
  - `/pt/trips` provides `tripId -> shapeId`
  - `shapes.txt` provides `shapeId -> ordered coordinates`
  - `GdyniaGtfsStore` caches the parsed result in memory with a 1-day TTL
  - Android preloads the cache during startup in `MainActivity`
- Gdynia does not support live GPS tracking in the same way as Gdansk
- SKM uses PLK-backed realtime departures and planned route metadata authenticated with `PLK_KEY`
- SKM still keeps local station coordinates because the PLK station dictionary does not expose map geometry
- SKM markers remain visually distinct from bus and tram stops and are not clustered

Debugging notes:
- stop/departure/route/vehicle-loading failures are logged from `MapScreenViewModel`
- GTFS download and parse failures are logged from `GdyniaGtfsStore`
- keep the SKM provider swap behind the same repository and datasource boundary so UI code does not depend on mock vs live data
- Android cleartext HTTP is enabled specifically for `api.zdiz.gdynia.pl`

Platform engines:
- Android: `HttpClient(Android)`
- iOS: `HttpClient(Darwin)`
- Desktop: `HttpClient(Java)`

### Database layer

Room KMP 2.7.2 with `@ConstructedBy(CommuterDatabaseConstructor::class)`.

`getDatabaseBuilder()` is implemented per platform:
- Android: Room + `Context`
- Desktop: Room + file path
- iOS: Room + `NSFileManager`

## Configuration

### Maps

The project uses MapLibre, not Mapbox, for the active implementation path.
No token is required for the current placeholder map setup.

### Signing

Local Android release builds read from:
- `secrets.properties`
- `signing/key.jks`

Expected properties:

```properties
PASS=keystore_password
ALIAS=key_alias
ALIAS_PASS=key_password
PLK_KEY=your_plk_api_key
```

CI can provide signing through:
- `ANDROID_SIGNING_STORE_FILE`
- `ANDROID_SIGNING_STORE_PASSWORD`
- `ANDROID_SIGNING_KEY_ALIAS`
- `ANDROID_SIGNING_KEY_PASSWORD`
- `PLK_KEY`
- optional `ANDROID_VERSION_CODE`
- optional `ANDROID_VERSION_NAME`

### GitHub Actions

Configured workflows:
- PR verification against `main`
- signed main release build

Signed CI builds use these secrets:
- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

## Tech stack

- Kotlin Multiplatform: 2.3.21
- AGP: 9.2.1
- Compose Multiplatform: 1.10.3
- Material 3: 1.10.0-alpha05
- MapLibre Compose: 0.13.0
- MapLibre Native Android SDK: 13.3.1
- JetBrains Navigation Compose: 2.9.2
- Koin: 4.0.2
- Ktor: 3.1.1
- Room KMP: 2.7.2
- kotlinx-serialization: 1.8.0
- kotlinx-coroutines: 1.10.1
- kotlinx-datetime: 0.6.2
- multiplatform-settings: 1.3.0
- Spotless: 6.25.0
- Detekt: 1.23.6

## Android SDK levels

- Min SDK: 29
- Target SDK: 37
- Compile SDK: 37
