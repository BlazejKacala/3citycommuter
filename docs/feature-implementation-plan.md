# Integracja ekranu rozkładów z danymi rzeczywistymi

## Stan początkowy (2026-10-20)

Repozytorium zawiera niezacommitowany, wieloekranowy feature. Zachowujemy jego nawigację, wygląd, preferencje i lokalne zmiany. Nie cofamy go do wcześniejszego prototypu z rozmowy.

- `shared/ui/src/commonMain/kotlin/pl/bkacala/threecitycommuter/ui/screen/schedule/`: ekran wyszukiwania, rozkład, szczegóły kursu, kolekcje, komunikaty, komponenty Material.
- `ScheduleScreenViewModel.kt`: StateFlow, viewModelScope, akcje i aktualizacja zegara co 30 sekund; wszystkie odjazdy pochodzą z `data/DemoScheduleDataSource.kt`.
- `model/ScheduleModels.kt`, `ScheduleUiState.kt`: modele obecnie zakładają symetryczną trasę i stały czas między przystankami. To założenie nie pasuje do danych dostawców.
- `data/SchedulePreferencesStore.kt`: Settings, JSON i atomowa publikacja porcji danych. Obecny namespace demo nie może być traktowany jako dane produkcyjne.
- `ui/nav/ScheduleNavGraph.kt`: jeden ViewModel dla grafu rozkładów; integracja ma zachować ten podział.

## Zweryfikowana architektura

W ścieżkach poniżej pakiet bazowy to `pl/bkacala/threecitycommuter`.

| Element | Istniejący wzorzec i plik |
| --- | --- |
| Moduły | `settings.gradle.kts`: composeApp Android/Desktop, shared core/network/database/data/ui |
| KMP | `build-logic/convention/`: commonMain + Android/JVM/iOS; wspólne pliki dyskowe GTFS w androidJvmMain |
| DI | `shared/{network,data,ui}/src/commonMain/kotlin/.../di/{Network,Data,Ui}Module.kt`: Koin |
| HTTP | `shared/network/.../client/KtorGdanskApiClient.kt`, `KtorPlkApiClient.kt`: wspólny Ktor, kotlinx.serialization; Android/Java/Darwin przez platformNetworkModule |
| Agregacja dostawców | `shared/network/.../client/CombinedTransitDataSource.kt`: delegacja Gdańsk/Gdynia/PLK |
| Tożsamość | `shared/core/.../model/transit/TransitStopKey.kt`: provider + sourceStopId |
| Repozytoria | `shared/data/.../repository/stops/RealTransitStopsRepository.kt`: Flow, IO, lokalne dane przed odświeżeniem, propagacja anulowania |
| Odjazdy i trasy | `TransitStopsRepository`, `RoutesRepository`, domenowe Departure i Route; nie duplikujemy klienta live |
| Stan i lifecycle | `shared/ui/.../ui/screen/map/MapScreenViewModel.kt`: UiState, Job cancellation, lifecycle start/stop polling, logError |
| Lokalizacja | `shared/data/.../repository/location/LocationRepository.kt`, `PermissionChecker`: istniejące platformowe implementacje |
| Testy | commonTest + JVM, kotlin.test, coroutines-test, Ktor MockEngine; ViewModelStore.clear oraz resetMain |

Nie tworzymy nowego stosu ani nowych bibliotek. Uzasadnione rozszerzenie to repozytorium rozkładów datowanych: obecne repozytoria nie udostępniają katalogu linii ani kursów na wybrany dzień. Szczegóły API i źródła: `feature-api-analysis.md`.

## Kontrakty zatwierdzone do równoległej pracy

- `shared/core/.../model/schedule/Timetable.kt`: TimetableLine, TimetableJourney, TimetableStopTime, LineTimetable. Identyfikatory z prefiksem dostawcy, godziny Instant, kolejność jawna.
- `shared/network/.../client/TimetableDataSource.kt`: `getLines(provider)` i `getTimetable(line, date, stopKey?)`. Stacja jest potrzebna PLK. Kierunek pochodzi z rzeczywistych kursów.
- `shared/data/.../repository/schedule/ScheduleRepository.kt`: te same operacje jako Flow na wzór istniejących repozytoriów.
- `TimetableAvailability.UNSUPPORTED_DATE` odróżnia nieobsługiwaną datę od poprawnego pustego rozkładu. Sieć/HTTP/parsing propagują wyjątki; anulowanie nie staje się błędem UI.
- Dzisiejsza tablica przystankowa korzysta z istniejącego TransitStopsRepository. Pełne datowane rozkłady pobiera się dla wybranej linii; nie skanujemy wszystkich przystanków w poszukiwaniu linii.
- Mapowanie UI zachowuje dotychczasowe nazwy modeli, dodając prawdziwe czasy przystanków, źródłową tożsamość kursu i rzeczywiste warianty kierunków. Nie wyliczamy czasów przez stały interwał.

## Zadania i ownership

1. Supervisor: analiza, ten plan, kontrakty, integracja UI, AGENTS.md, końcowe testy i akceptacja.
2. API Analyst (Luna): wyłącznie `docs/feature-api-analysis.md`; gotowe przed implementacją adapterów.
3. Workflow Config (Luna): `.codex/`, `docs/codex-multi-agent.md`; niezależne od kodu feature'a.
4. Data Worker (Luna): adaptery katalogów i rozkładów Gdańska/PLK, agregator TimetableDataSource, RealScheduleRepository, DI network/data. Nie edytuje modeli kontraktów bez uzgodnienia.
5. Gdynia Data Worker (Luna): GdyniaTimetableSource i konieczne, ograniczone rozszerzenia GdyniaGtfsStore, bez zmiany publikacji snapshotów i istniejących cache.
6. ViewModel Worker (Luna): ScheduleScreenViewModel, ScheduleUiState, ScheduleAction, modele i mapper UI, preferences store, UiModule. Nie zmienia ekranów Compose.
7. Test Worker (Luna): testy nowych adapterów, repozytorium, mapperów i ViewModelu; uzgadnia z supervisorem aktualizację istniejących testów demo.
8. Reviewer (Astra): niezależny przegląd całego diffu po integracji, bez edycji podczas review.

Maksymalnie trzech subagentów jednocześnie. Testy Gradle uruchamia supervisor, aby uniknąć równoczesnego zapisu do katalogów build. Każdy worker raportuje pliki, testy, problemy i ryzyka. Zmiany kontraktów wymagają uzgodnienia. Kolejność: analiza → kontrakty → równolegle adaptery/VM → integracja UI/testy → review → poprawki → weryfikacja.

## Ryzyka i zasady implementacji

- Gdańskie tripId opisuje wzorzec, nie pojedynczy kurs. Wariant, data i instancja odjazdu muszą rozróżniać kursy. Czasy po północy odnosimy do daty usługi w Europe/Warsaw.
- Gdynia wymaga kalendarza usług GTFS; nie wolno uznać stop_times za codzienny rozkład. Brak danych o ważności oznacza jawne ograniczenie, bez fikcyjnych kursów.
- PLK wymaga skonfigurowanego PLK_KEY. Nie odczytujemy ani nie logujemy klucza; brak konfiguracji daje błąd z możliwością ponowienia.
- Nie łączymy aktualnych opóźnień z inną datą ani z innym kursem. Gdynia nie dostaje fikcyjnego GPS.
- Brak potwierdzonego feedu komunikatów i dostarczania powiadomień systemowych ma być jasno opisany w UI. Nie pozostawiamy komunikatów demo jako informacji rzeczywistych.
- Zapis offline przechowuje czasy i metadane potrzebne do odczytu po restarcie, bez aktualnych opóźnień; nie zależy od bieżącego katalogu sieciowego.
- Lokalizacja używa istniejącego repozytorium; stała lokalizacja desktop/iOS nie może udawać GPS.
- Zmiana linii, przystanku, daty i wyjście z ekranu anuluje nieaktualne zapytania/polling. Lokalne filtrowanie i ulubione nie wywołują sieci.
- Brak globalnego refaktoringu ani nowych bibliotek. Dotychczasowe niezacommitowane zmiany pozostają zachowane.

## Kryteria akceptacji

- Produkcyjne UI/VM/DI nie zależy od DemoScheduleDataSource ani stałego interwału czasów.
- Realne katalogi, identyfikatory przewoźników, odjazdy i trasy; loading/success/empty/error/retry i jawne ograniczenia dostępności.
- Kierunki, daty, ulubione, historia i zapis offline współpracują z rzeczywistymi danymi i nie powodują wyjątków przy brakujących wpisach.
- Deterministyczne testy obejmują mapowanie, różne daty, brakujące dane, błędy HTTP/sieci, anulowanie i brak nadpisania nowego wyboru starym wynikiem.
- Kompilacja Android/JVM, właściwe testy JVM i root `:lint`; brak twierdzenia o przetestowanym iOS na Windows.
- `.codex/` zweryfikowane dostępnym CLI, rzeczywiste identyfikatory modeli i udokumentowane ograniczenia konfiguracji.

## Wyniki weryfikacji

Końcowa weryfikacja 20–21.10.2026:

| Kontrola | Wynik |
|---|---|
| `:shared:network:jvmTest` | 47 testów, 0 błędów |
| `:shared:data:jvmTest` | 10 testów, 0 błędów |
| `:shared:ui:jvmTest` — pełne uruchomienie | przekroczony limit 60 s |
| UI po wyłączeniu jednego istniejącego testu mapy wyłącznie w skrypcie uruchomieniowym | 38 testów, 0 błędów; w tym 11 testów rozkładu |
| `:composeApp:android:assembleDebug` | sukces, APK debug zbudowany |
| `:composeApp:desktop:testClasses` | sukces; podgląd ma fake repozytoria wyłącznie w test source set |
| Spotless dla plików objętych integracją | sukces |
| `:lint` | Detekt bez błędów; Spotless blokowany przez cztery istniejące pliki mapy |
| `git diff --check` | sukces |
| Codex CLI 0.163.0 | konfiguracja projektu odczytana, `config.load: ok`, model Astra; szczegóły w `codex-multi-agent.md` |

Wyłączony test: `MapScreenViewModelTest.refreshedDepartureUpdatesRouteTimesWithoutLosingSelection`. Pełna próba bez wyłączenia przekroczyła 60 s; pozostały zestaw przechodzi. Wyłączenie znajduje się tylko w lokalnym, ignorowanym `build/schedule-test-timeout.gradle`. Nie zmieniono testu ani produkcyjnego kodu mapy.

Istniejące naruszenia formatowania blokujące globalny lint:

- `shared/ui/src/commonMain/kotlin/pl/bkacala/threecitycommuter/ui/screen/map/MapScreenUiState.kt`
- `shared/ui/src/commonMain/kotlin/pl/bkacala/threecitycommuter/ui/screen/map/MapScreenViewModel.kt`
- `shared/ui/src/commonTest/kotlin/pl/bkacala/threecitycommuter/ui/screen/map/MapRouteStopTimeTest.kt`
- `shared/ui/src/commonTest/kotlin/pl/bkacala/threecitycommuter/ui/screen/map/RouteStopTimeCalculatorTest.kt`

Logi lokalne: `build/schedule-final-tests.log`, `build/schedule-validation.log`, `build/schedule-ui-full.log`, `build/schedule-format.log`. Raporty testów znajdują się w `shared/{ui,network,data}/build/reports/tests/jvmTest/`.

### Zakres testów feature'a

Gdańsk: dokładna data, oficjalny URL/wrapper, powtarzalne instancje kursów, filtrowanie przystanków technicznych, czasy ISO/następnego dnia, HTTP 503, niepoprawny JSON i nieopublikowana data. Gdynia: etykiety i identyfikatory linii, kalendarze i wyjątki, czasy powyżej 24:00, granica DST, brak kalendarza i data poza feedem. PLK: autoryzacja/HTTP 429 nie są cache'owane jako pusty plan, kolejność stacji, daty kursowania i offsety dni. Repozytorium: delegowanie i propagacja błędów. VM: loading/success/empty/error/retry, niezależność dostawców, anulowanie starszego wyboru, kierunki i data, odczyt snapshotu, ścisłe dopasowanie live, zatrzymanie pollingu, brak dodatkowego requestu przy zmianie kierunku i brak odjazdów na terminalu.

### Review i pozostałe ograniczenia

Niezależny reviewer (Astra) wykrył błędny UUID i wrapper zasobu Gdańska, brak walidacji HTTP przed cache PLK oraz problemy DST/anulowania GTFS. Poprawiono te miejsca i dodano regresje. Po limicie użycia subagentów supervisor dokończył review, poprawki i testy. Weryfikacja nie obejmowała testu end-to-end z rzeczywistymi dostawcami, autoryzowanego połączenia PLK, emulatora/urządzenia ani iOS.

Produkcyjny przepływ nie korzysta z generatora demo. Utrzymano istniejące ekrany i wzorce Koin/Ktor/Flow. Dodatkowa zależność desktopowego testowego podglądu to już używane w projekcie `kotlinx-datetime`, bez wprowadzania nowej biblioteki.

Dostępność dat zależy od feedu. Komunikaty drogowe i dostarczanie powiadomień systemowych pozostają jawnie niedostępne; lokalne ustawienia alertów są zachowane. Dopasowanie live, którego nie da się potwierdzić po tożsamości i czasie kursu, pozostawia kurs planowy. GPS zależy od istniejących uprawnień i implementacji platformowej. Gdynia nie ma nowego iOS fallbacku GTFS.

Zapis offline: najwyżej 12 snapshotów i 2000 kursów na snapshot, limit przybliżony 750 000 bajtów w VM oraz twardy limit serializacji 256 × 6000 znaków w store. Snapshoty są jawnie datowane, bez automatycznego przesuwania na inny dzień. Krótkie zachowanie planu w VM: jeden wynik, do 20 000 rekordów stop-time, 5 minut. Nie zmieniono istniejących limitów cache providerów.

### Poprawka detalu linii — 2026-10-21

Wybór kierunku dla linii miejskich grupuje obecnie warianty według `directionId` przewoźnika (Gdańsk oraz GTFS Gdyni). Gdańskie kursy `NON_PASSENGER` są pomijane. Etykieta grupy preferuje wariant główny wskazany przez dostawcę, a następnie najczęstszy opis pasażerski, a selektor przystanków uwzględnia również przystanki wariantów. Każdy kurs zachowuje własny opis, kolejność przystanków i czasy; lista przystanków w selektorze nie jest trasą konkretnego kursu. Przy braku identyfikatora kierunku warianty pozostają rozdzielone, zamiast arbitralnego ograniczania do dwóch. Dla kolei sposób rozdzielania kierunków pozostaje bez zmian.

Na emulatorze Android odtworzono `OutOfMemoryError` (limit sterty 192 MB) przy otwieraniu autobusu Gdynia 86. Aktualny snapshot zawierał 271 932 rekordy `calendar_dates.txt`; parser przechowywał mapy wszystkich wierszy, a następnie drugi zestaw modeli wyjątków. Odczyt jest teraz strumieniowy: zachowuje tylko kalendarz wybranej daty i kursy wybranej linii. Android/JVM odczytują wpis ZIP bezpośrednio przez `ZipFile`, bez wielokrotnej dekompresji poprzedzającego go `shapes.txt` (około 53 MB).

Import snapshotu uruchamiany przez detal waliduje strumieniowo spójność identyfikatorów, bez budowania geometrii i obiektów przystanków całej sieci. Zachowano koordynator, atomową publikację, anulowanie, backoff i poprzedni snapshot po błędzie. Pozostałe ścieżki pełnego importu dla mapy nadal wymagają osobnej optymalizacji.

Weryfikacja: 51 testów `:shared:network:jvmTest`, w tym kalendarz 280 000 obcych wyjątków odczytywany z pliku ZIP oraz publikacja/fallback snapshotu; `:composeApp:android:assembleDebug`; Spotless dla plików poprawki. Ponowne otwarcie Gdynia 86 na emulatorze wczytało rzeczywiste kierunki i przystanki bez awarii. SKM również wczytało dane podczas diagnozy. Logi: `build/schedule-detail-final.log`, `build/schedule-detail-verified.log`.
