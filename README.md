# 3citycommuter

Kotlin Multiplatform app for public transport commuters in Gdansk, Sopot, and Gdynia. The app shows stops on a map, real-time departures, vehicle positions, and route shapes.

## Platforms

| Platform | Status |
| --- | --- |
| Android | Production-ready |
| Desktop (JVM) | Supported |
| iOS | KMP target configured, app entry point not implemented |

Android target details:
- Min SDK: 29
- Target SDK: 37
- Compile SDK: 37

## Data sources

The app aggregates multiple provider APIs into one shared transport model.

Gdańsk sources:
- stops: `ckan.multimediagdansk.pl`
- real-time departures and route shapes: `ckan2.multimediagdansk.pl`
- vehicles and metadata: `files.cloudgdansk.pl`

Gdynia sources:
- stops, delays, routes metadata, trips, GTFS: `api.zdiz.gdynia.pl`

Provider differences are normalized before the UI sees them:
- both cities map into the same `BusStopData`, `Departure`, and `Route` domain models
- app-level stop IDs are globally unique even though provider-native IDs differ
- Gdynia line labels come from `/pt/routes.routeShortName`
- Gdynia route geometry comes from `gtfs.zip`, not from a direct route-shape endpoint
- Gdynia does not currently expose a public live GPS feed compatible with the Gdańsk feed

## Tech stack

| Layer | Technology | Version |
| --- | --- | --- |
| Language | Kotlin Multiplatform | 2.3.21 |
| Android Gradle Plugin | AGP | 9.2.1 |
| UI | Compose Multiplatform | 1.10.3 |
| UI | Material 3 | 1.10.0-alpha05 |
| Maps | MapLibre Compose | 0.13.0 |
| Maps | MapLibre Native Android SDK | 13.3.1 |
| Navigation | JetBrains Navigation Compose | 2.9.2 |
| DI | Koin | 4.0.2 |
| Networking | Ktor Client | 3.1.1 |
| Database | Room KMP | 2.7.2 |
| Serialization | kotlinx-serialization | 1.8.0 |
| Coroutines | kotlinx-coroutines | 1.10.1 |
| Date/Time | kotlinx-datetime | 0.6.2 |
| Settings | multiplatform-settings | 1.3.0 |
| Quality | Spotless | 6.25.0 |
| Quality | Detekt | 1.23.6 |

## Architecture

```text
composeApp / iosApp
        |
        v
   shared:ui
        |
        v
  shared:data
    /       \
   v         v
shared:network  shared:database
    \       /
        v
   shared:core
```

`shared:core` contains domain models and utilities. The remaining shared modules cover networking, database, repositories, and Compose UI. Android and Desktop bootstrap Koin and consume `shared:ui`.

Transport provider architecture:
- `GdanskTransitDataSource` adapts the Gdańsk feeds
- `GdyniaTransitDataSource` adapts the Gdynia API
- `CombinedTransitDataSource` merges both into one application-facing source

Important normalization rules:
- `TransitStopId` converts provider-native stop IDs into globally unique app IDs
- `BusStopData.provider` and `BusStopData.sourceStopId` are derived from the app-level stop ID
- `Departure.lineNumber` is the display label shown in UI
- `Departure.routeId` remains the internal provider route identifier used for route lookup

Gdynia route parsing:
- `GdyniaGtfsStore` loads `tripId -> shapeId` from `/pt/trips`
- it loads route coordinates from `shapes.txt` inside `gtfs.zip`
- the parsed route cache is kept in memory with a 1-day TTL
- Android preloads that cache during startup so the first route selection does not need to parse GTFS on click

## Requirements

- JDK 21
- Android SDK 37 for Android builds
- Gradle Wrapper from this repository

## Build and run

```bash
./gradlew build
./gradlew :composeApp:android:assembleDebug
./gradlew :composeApp:android:assembleRelease
./gradlew :composeApp:desktop:run
```

## Testing

```bash
./gradlew :shared:ui:jvmTest
./gradlew :shared:network:jvmTest
./gradlew :composeApp:android:connectedDebugAndroidTest
```

Useful provider-focused tests:
- `./gradlew :shared:network:jvmTest --tests "pl.bkacala.threecitycommuter.client.TransitDataSourceTest"`
- `./gradlew :shared:network:jvmTest --tests "pl.bkacala.threecitycommuter.model.gdynia.GdyniaRouteNetworkDataSerializationTest"`

## Code quality

```bash
./gradlew spotlessCheck
./gradlew spotlessApply
./gradlew detekt
./gradlew lint
./gradlew check
```

`lint` is the main local quality gate and runs Spotless plus Detekt. `check` also finalizes with `lint`.

## Maps

The project uses MapLibre. Basic usage does not require a token. Current platform implementations:

- Android: Canvas placeholder map
- Desktop: Canvas placeholder map with stop markers and route lines
- iOS: placeholder stub

Mapbox dependencies are deprecated and commented out.

## Signing and release builds

Local Android release builds use:

- `secrets.properties`
- `signing/key.jks`

Expected keys in `secrets.properties`:

```properties
PASS=keystore_password
ALIAS=key_alias
ALIAS_PASS=key_password
```

CI can use environment variables instead:

- `ANDROID_SIGNING_STORE_FILE`
- `ANDROID_SIGNING_STORE_PASSWORD`
- `ANDROID_SIGNING_KEY_ALIAS`
- `ANDROID_SIGNING_KEY_PASSWORD`
- optional: `ANDROID_VERSION_CODE`
- optional: `ANDROID_VERSION_NAME`

## GitHub Actions

Two workflows are configured:

- `Pull Request`: runs on PRs targeting `main`, executes `lint`, JVM tests, builds the debug APK, and optionally builds a signed release APK when signing secrets are configured
- `Main Release Build`: runs on pushes to `main` and on manual dispatch, and optionally builds a signed release APK when signing secrets are configured

Required GitHub secrets for signed CI builds:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

On Linux runners, `gradlew` is tracked as executable and workflows also call `chmod +x ./gradlew` defensively.
