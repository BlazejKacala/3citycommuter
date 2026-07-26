# 3citycommuter

Aplikacja Kotlin Multiplatform dla pasażerów komunikacji miejskiej w Gdańsku, Sopocie i Gdyni. Wyświetla przystanki na mapie, odjazdy w czasie rzeczywistym, pozycje pojazdów oraz przebieg tras.

## Funkcje

- Interaktywna mapa przystanków autobusowych i tramwajowych.
- Odjazdy w czasie rzeczywistym wraz z opóźnieniami.
- Śledzenie pojazdów i prezentacja jakości sygnału GPS.
- Wizualizacja trasy wybranego kursu.
- Wyszukiwanie przystanków i centrowanie mapy na lokalizacji użytkownika.

## Platformy

| Platforma | Status |
| --- | --- |
| Android | Gotowa do użycia (min. SDK 29, target SDK 36) |
| Desktop (JVM) | Obsługiwana |
| iOS | Skonfigurowany target KMP; brak aplikacji startowej, widok mapy jest stubem |

## Dane

Aplikacja korzysta z otwartych danych [Otwarte Dane Gdańska](https://ckan.multimediagdansk.pl):

- przystanki i linie: `ckan.multimediagdansk.pl`,
- odjazdy i pozycje GPS: `ckan2.multimediagdansk.pl`,
- dane pojazdów: `files.cloudgdansk.pl`.

## Stos technologiczny

| Warstwa | Technologia |
| --- | --- |
| Język | Kotlin Multiplatform 2.3.21 |
| UI | Compose Multiplatform 1.10.3 + Material 3 |
| Mapy | MapLibre Compose 0.13.0 + MapLibre Native 13.3.1 |
| DI | Koin 4.0.2 |
| Sieć | Ktor Client 3.1.1 |
| Baza danych | Room KMP 2.7.2 |
| Nawigacja | JetBrains Navigation Compose 2.9.2 |
| Asynchroniczność | Kotlin Coroutines + Flow |
| Lokalizacja | Google Play Services (Android) |
| Ustawienia | multiplatform-settings 1.3.0 |

## Architektura

```
composeApp / iosApp
        │
        ▼
   shared:ui
        │
        ▼
  shared:data
    ┌───┴────┐
    ▼        ▼
shared:network  shared:database
    └───┬────┘
        ▼
   shared:core
```

`shared:core` zawiera modele domenowe i narzędzia. Pozostałe moduły odpowiadają odpowiednio za komunikację sieciową, bazę Room, repozytoria oraz interfejs Compose. Aplikacje Android i Desktop inicjalizują Koin i korzystają z `shared:ui`.

## Wymagania

- JDK 21,
- Android SDK 36 do budowania aplikacji Android,
- Gradle Wrapper dostarczony w repozytorium (Gradle 9.4.1).

## Budowanie i uruchamianie

```bash
# Android: debug APK
./gradlew :composeApp:android:assembleDebug

# Desktop JVM
./gradlew :composeApp:desktop:run

# Wszystkie moduły
./gradlew build
```

Wydanie Android wymaga pliku `secrets.properties` oraz klucza `signing/key.jks`; te pliki są lokalne i nie powinny trafiać do repozytorium.

W CI można zamiast tego użyć zmiennych środowiskowych:
`ANDROID_SIGNING_STORE_FILE`, `ANDROID_SIGNING_STORE_PASSWORD`, `ANDROID_SIGNING_KEY_ALIAS`, `ANDROID_SIGNING_KEY_PASSWORD`.
Opcjonalnie można też nadpisać wersję przez `ANDROID_VERSION_CODE` i `ANDROID_VERSION_NAME`.

## Testy

```bash
# Testy ViewModeli (commonTest uruchamiany na JVM)
./gradlew :shared:ui:jvmTest

# Testy serializacji i warstwy sieciowej
./gradlew :shared:network:jvmTest

# Testy instrumentowane Androida — wymagają urządzenia lub emulatora
./gradlew :composeApp:android:connectedDebugAndroidTest
```

## Mapy

Projekt używa MapLibre — nie wymaga tokenu do podstawowego działania. Aktualna implementacja mapy działa na Androidzie i Desktopie. Dostępne są również style Stadia Maps, które opcjonalnie wymagają tokenu przekazywanego do aplikacji; nie zapisuj go w repozytorium.

## Jakość kodu

```bash
# Sprawdzenie formatowania
./gradlew spotlessCheck

# Formatowanie kodu
./gradlew spotlessApply

# Analiza statyczna
./gradlew detekt

# Wszystkie kontrole jakości (Spotless + Detekt)
./gradlew lint

# Pełne sprawdzenie projektu, w tym lint
./gradlew check
```

Konfiguracja Spotless i Detekt znajduje się w głównym `build.gradle.kts`, a reguły Detekt w `config/detekt/detekt.yml`.

## GitHub Actions

Repo zawiera dwa workflowy:

- `Pull Request` uruchamia się dla PR do `main`, odpala `lint`, testy JVM i buduje debug APK. Jeśli ustawisz sekrety podpisu, zbuduje też podpisany `release` APK jako artifact.
- `Main Release Build` uruchamia się po pushu do `main` oraz ręcznie i buduje podpisany `release` APK.

Wymagane sekrety GitHub:

- `ANDROID_KEYSTORE_BASE64` — zawartość `signing/key.jks` zakodowana base64
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

