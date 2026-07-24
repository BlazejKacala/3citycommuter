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

## Narzędzia do kontroli jakości kodu

### Spotless (Formatowanie kodu)

Projekt używa **Spotless 6.25.0** z **ktlint** do automatycznego formatowania kodu Kotlin.

**Dostępne komendy:**
```bash
# Sprawdź formatowanie (nie modyfikuje plików)
./gradlew spotlessCheck

# Sformatuj kod
./gradlew spotlessApply
```

**Konfiguracja:**
- Kotlin: 4 spacje, usuwanie białych znaków na końcu linii, nowa linia na końcu pliku
- Kotlin Gradle (`.kts`): takie same reguły jak Kotlin
- Markdown, YAML, `.gitignore`: 2 spacje, usuwanie białych znaków

**Wykluczenia:** `**/build/**`, `**/.gradle/**`, `**/generated/**`

### Detekt (Analiza statyczna kodu)

Projekt używa **Detekt 1.23.6** do analizy statycznej kodu Kotlin z obsługą Kotlin Multiplatform.

**Dostępne komendy:**
```bash
# Uruchom analizę Detekt
./gradlew detekt

# Generowanie raportów (HTML, XML, SARIF)
./gradlew detektAll  # w wszystkich modułach
```

**Konfiguracja:** `config/detekt/detekt.yml`

**Raporty generowane w:**
- HTML: `build/reports/detekt/detekt.html`
- XML: `build/reports/detekt/detekt.xml`
- SARIF: `build/reports/detekt/detekt.sarif`

**Ograniczenia z KMP:**
Analiza typów (type resolution) jest włączona, ale może mieć ograniczenia w zależności od wersji Kotlin i konfiguracji multiplatform. Jeśli wystąpią problemy, można ją wyłączyć w pliku konfiguracyjnym.

### Zadanie `lint`

Połączone zadanie uruchamiające wszystkie narzędzia kontroli jakości:
```bash
# Uruchom wszystkie sprawdzenia (Spotless + Detekt)
./gradlew lint

# Zadanie `check` obejmuje również `lint`
./gradlew check
```

## Baseline Detekt

Jeśli istniejący kod generuje dużo ostrzeżeń Detekt, użyj baseline zamiast masowego wyłączania reguł:

1. Uruchom: `./gradlew detektGenerateConfig`
2. Skopiuj wygenerowaną konfigurację do `config/detekt/detekt.yml`
3. Dodaj plik baseline: `./gradlew detektBaseline`
4. Skonfiguruj `baseline` w `detekt.yml`

**Lokalizacja pliku baseline:** `config/detekt/baseline.yml`

## Aktualizacja konfiguracji

- **Spotless**: Konfiguracja w `build.gradle.kts` (root project)
- **Detekt**: Konfiguracja w `config/detekt/detekt.yml`
- **Wersje pluginów**: `gradle/libs.versions.toml`

## Formatowanie kodu

Zalecany workflow przed commitem:
```bash
# 1. Sformatuj kod
./gradlew spotlessApply

# 2. Sprawdź analize statyczną
./gradlew detekt

# 3. Uruchom wszystkie sprawdzenia
./gradlew lint

# 4. Zbuduj projekt
./gradlew build
```
