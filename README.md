# 3citycommuter

Aplikacja **Kotlin Multiplatform** dla pasażerów komunikacji miejskiej w Gdańsku, Sopocie i Gdyni.
Wyświetla przystanki na mapie, rozkłady jazdy w czasie rzeczywistym oraz śledzi pojazdy na żywo.

## Funkcje

- **Mapa przystanków** – wszystkie przystanki autobusowe i tramwajowe na interaktywnej mapie
- **Odjazdy w czasie rzeczywistym** – lista najbliższych odjazdów z wybranego przystanku z uwzględnieniem opóźnień
- **Śledzenie pojazdów** – pozycja pojazdu na mapie aktualizowana co kilka sekund, wskaźnik jakości sygnału GPS
- **Wizualizacja trasy** – rysowanie linii trasy wybranego kursu na mapie
- **Wyszukiwanie przystanków** – szybkie wyszukiwanie po nazwie przystanku
- **Lokalizacja użytkownika** – centrowanie mapy na aktualnej pozycji

## Platformy

| Platforma | Status |
|---|---|
| Android | ✅ Produkcyjny |
| Desktop (JVM) | ✅ Działa |
| iOS | 🚧 Stub (wymaga Mapbox iOS SDK) |

## Dane

Aplikacja korzysta z otwartego API [Otwarte Dane Gdańska](https://ckan.multimediagdansk.pl):
- Dane o przystankach i liniach z `ckan.multimediagdansk.pl`
- Odjazdy i pozycje GPS z `ckan2.multimediagdansk.pl`
- Baza danych pojazdów z `files.cloudgdansk.pl`

## Stos technologiczny

| Warstwa | Technologia |
|---|---|
| Język | Kotlin Multiplatform 2.1 |
| UI | Compose Multiplatform 1.7 + Material 3 |
| Mapy | Mapbox (Android) / Canvas placeholder (Desktop) |
| DI | Koin 4.0 |
| Sieć | Ktor Client (Android/Darwin/Java engines) |
| Baza danych | Room KMP 2.7 |
| Nawigacja | JetBrains Navigation Compose (KMP) |
| Async | Kotlin Coroutines + Flow |
| Lokalizacja | Google Play Services (Android) / stub (Desktop) |
| Ustawienia | multiplatform-settings |

## Architektura

Projekt wielomodułowy KMP z czystym podziałem warstw:

```
shared/
├── core/       – modele domenowe, LatLng, UiState, utilities
├── network/    – Ktor client, DTOs, Koin modules per platform
├── database/   – Room KMP, DAOs, encje, DatabaseModule
├── data/       – repozytoria, mappery, use case'y, lokalizacja
└── ui/         – Compose Multiplatform UI, ViewModels, nawigacja

composeApp/
├── android/    – MainActivity, CommuterApp (Koin init)
└── desktop/    – main.kt (Window + Koin init)
```

**Przepływ zależności:** `composeApp` → `shared:ui` → `shared:data` → `shared:network` + `shared:database` → `shared:core`

## Budowanie

```bash
# Android APK
./gradlew :composeApp:android:assembleDebug
./gradlew :composeApp:android:assembleRelease   # wymaga konfiguracji podpisywania

# Desktop JVM
./gradlew :composeApp:desktop:run

# Wszystkie moduły
./gradlew build
```

## Testy

```bash
./gradlew :shared:ui:jvmTest          # testy ViewModel (commonTest, JVM)
./gradlew :shared:network:jvmTest     # testy serializacji
./gradlew :composeApp:android:connectedDebugAndroidTest   # testy instrumentowane
```

## Mapbox

Mapbox wymaga tokenu pobierania. Dodaj do `gradle.properties`:

```properties
MAPBOX_DOWNLOADS_TOKEN=sk.eyJ1...
```

Bez tokenu Android używa uproszczonego placeholdera Canvas.

## Formatowanie kodu

```bash
./gradlew spotlessApply ktlintFormat    # zawsze uruchamiaj przed commitem
```
