# 3citycommuter

Aplikacja Android dla pasażerów komunikacji miejskiej w Gdańsku, Sopocie i Gdyni.
Wyświetla przystanki na mapie, rozkłady jazdy w czasie rzeczywistym oraz śledzi pojazdy na żywo.

## Funkcje

- **Mapa przystanków** – wszystkie przystanki autobusowe i tramwajowe na interaktywnej mapie Google Maps
- **Odjazdy w czasie rzeczywistym** – lista najbliższych odjazdów z wybranego przystanku z uwzględnieniem opóźnień
- **Śledzenie pojazdów** – pozycja pojazdu na mapie aktualizowana co kilka sekund, wskaźnik jakości sygnału GPS
- **Wizualizacja trasy** – rysowanie linii trasy wybranego kursu na mapie
- **Wyszukiwanie przystanków** – szybkie wyszukiwanie po nazwie przystanku
- **Lokalizacja użytkownika** – centrowanie mapy na aktualnej pozycji

## Dane

Aplikacja korzysta z otwartego API [Otwarte Dane Gdańska](https://ckan.multimediagdansk.pl):
- Dane o przystankach i liniach z `ckan.multimediagdansk.pl`
- Odjazdy i pozycje GPS z `ckan2.multimediagdansk.pl`
- Baza danych pojazdów z `files.cloudgdansk.pl`

## Stos technologiczny

| Warstwa | Technologia |
|---|---|
| Język | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Mapy | Google Maps Compose |
| DI | Hilt |
| Sieć | Ktor Client |
| Baza danych | Room |
| Nawigacja | Compose Navigation |
| Async | Kotlin Coroutines + Flow |
| Lokalizacja | Google Play Services |

## Architektura

Projekt wielomodułowy z czystym podziałem warstw:

```
app/       – UI (Compose, ViewModels, nawigacja)
data/      – repozytoria, modele domenowe, use case'y
network/   – klient HTTP, DTO, definicje API
database/  – Room, DAO, encje
```

## Wymagania

- Android 10+ (API 29)
- Klucz Google Maps API w pliku `secrets.properties`:
  ```
  MAPS_API_KEY=twój_klucz
  ```

## Budowanie

```bash
./gradlew assembleDebug     # debug APK
./gradlew assembleRelease   # release APK (wymaga konfiguracji podpisywania)
```

## Testy

```bash
./gradlew test                          # testy jednostkowe
./gradlew connectedDebugAndroidTest     # testy instrumentowane (urządzenie/emulator)
```

## Formatowanie kodu

```bash
./gradlew spotlessApply ktlintFormat    # zawsze uruchamiaj przed commitem
```
