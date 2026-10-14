# Plan optymalizacji warstwy danych 3citycommuter

Data analizy: 13.10.2026. Kod: `e338dcc1b7f92179c939698f0f7c470496aa94c8`.

## Stan wdrożenia quick winów — 13.10.2026

Wdrożono pakiet sześciu quick winów i dwóch dodatkowych zmian z rozmowy. Dalsze rozdziały opisują **stan bazowy audytu**; statusy poniżej i przy ustaleniach wskazują, które jego elementy zostały już zmienione.

| Quick win | Status | Wdrożenie i granice |
| --- | --- | --- |
| Wyłączenie body logging | Zrobione | Usunięto plugin Logging ze wspólnego klienta Ktor; payloady nie są logowane w żadnym wariancie |
| TTL Gdyni przy restarcie | Zrobione | Odczyt samej daty snapshotu; świeży snapshot pomija preload i download. Pierwsze odjazdy czytają lokalne stop_times bez geometrii i bez Route.Stop. Dysk ma pierwszeństwo przed seedem |
| Lokalny katalog przed siecią | Zrobione | Emisja Room/seeda przed odświeżeniem, potem zmienionego wyniku. Refresh należy do kolekcji: odbiorca musi kontynuować collect. Osobne aktualizacje providerów pozostają do wdrożenia |
| Mały seed Gdańska | Zrobione | 2719 rekordów z 2026-08-06; 1 176 879 B zamiast 15 304 307 B, redukcja pliku o 92,3%. Generator wymaga jawnej daty. To historyczny fallback offline, nie aktualny rozkład |
| Cache planu PLK | Zrobione | Pamięć procesu, TTL 1 h, klucz daty/stacji/przewoźnika/fullRoute, LRU do 8 odpowiedzi i 20 tys. rekordów stacji tras. Operacje live pozostają niezależne. Cache dyskowy/wersje oraz redukcja dwóch szczegółowych żądań są nadal otwarte |
| Indeks metadanych floty | Zrobione | Jeden odczyt Room, ponowne użycie mapy po numerycznym kodzie. Aktualizacja wymienia cache i timestamp; błąd zachowuje dane i wprowadza 5-minutową przerwę przed kolejną próbą |
| Anulowanie poprzedniej trasy | Zrobione | Osobny Job, anulowany po zmianie odjazdu/przystanku i wyczyszczeniu wyboru; anulowane zadanie nie czyści stanu nowszego żądania |
| Rzadsze pobieranie GPS | Zrobione | 20 s przerwy po żądaniu zamiast 10 s; wspólny snapshot GPS pozostaje do wdrożenia |

Dodano regresje dotyczące lokalnej emisji, świeżego/wygasłego GTFS, TTL/kluczy/LRU planu PLK, współdzielenia cache floty oraz anulowania tras. Weryfikację testów i kontroli jakości zakończono 14.10.2026; wyniki znajdują się w sekcji poniżej. Nie wykonano profilowania urządzenia; redukcja pliku seeda nie oznacza takiej samej redukcji heapu aplikacji.

## D10: koordynacja i atomowy snapshot GTFS — 14.10.2026

Wdrożono następny pakiet po quick winach:

- Jeden koordynator obejmuje preload, brakujące odjazdy, brakujące trasy i refresh. Oczekujący odbiorcy wykorzystują wynik zakończonego importu; nie uruchamiają równoległych importów. Dostępne odjazdy i kompletne trasy można czytać z niezmiennego stanu również podczas odświeżania.
- Android i JVM współdzielą implementację zapisu: pobranie do nowej generacji, parsowanie i walidacja, następnie atomowa wymiana manifestu. Nie ma zastępczego nieatomowego nadpisania. Zachowano odczyt starego, płaskiego formatu cache.
- Walidacja odrzuca puste dane, brak wymaganych wpisów/kolumn ZIP, błędne pola liczbowe i kursy bez odpowiadającej geometrii lub przystanków. Lokalny feed kontrolny: 9932 kursy, 3008 geometrii; wszystkie kursy mają geometrię i stop_times. Nie gwarantuje to zgodności każdej przyszłej pary osobno pobranych trips i ZIP; niezgodna para zachowuje poprzedni snapshot.
- Na dysku pozostają najwyżej bieżąca i poprzednia generacja nowego formatu, poza zachowanymi plikami legacy. Nieukończona generacja jest usuwana po błędzie/anulowaniu; pozostałości po zatrzymaniu procesu są sprzątane przed następnym importem.
- Cache brakujących tripId: LRU do 128 wpisów, TTL 5 minut, czyszczenie przy publikacji pełnej generacji. Świeży snapshot nie jest ponownie parsowany ani pobierany tylko dlatego, że nie zawiera wskazanego kursu.
- Błąd importu wprowadza 5-minutową przerwę przed następną próbą. Anulowanie nie jest błędem i nie uruchamia backoffu. Anulowanie oczekującego odbiorcy nie przerywa właściciela importu; anulowanie właściciela zatrzymuje import i pozwala kolejnemu odbiorcy spróbować ponownie. Parser CSV sprawdza anulowanie przy każdej linii, a mapowanie/sortowanie między grupami.
- Dostosowano kontrakt zapisu i implementację iOS; na Windows nie kompilowano ani nie uruchamiano iOS. ZIP reader tej platformy nadal jest stubem.

**Weryfikacja:** dodano 16 regresji koordynatora i zapisu na dysku. Testy JVM: data 8, network 39, UI 20 — łącznie 67, bez błędów. Przeszło polecenie gradlew.bat :shared:data:jvmTest :shared:network:jvmTest :shared:ui:jvmTest lint --continue --console=plain, obejmujące także kompilację Androida i Android Lint. Naprawiono niespójny fixture istniejącego testu przystanków: zmiana stopName wymagała także zmiany oczekiwanego name. Spotless poprawił również istniejące formatowanie warunku w CachingPlkApiClient.

**Granice:** pełny import nadal buduje cały graf GTFS w RAM. Magazyn indeksowany D3/D7, limit geometrii w pamięci i pomiary na urządzeniu pozostają kolejnymi pracami. Nie deklarujemy oszczędności MB ani przyspieszenia bez profilowania.

## 1. Wnioski i zakres

Największy potencjał ma ograniczenie pracy wykonywanej bez potrzeby: pełnego odświeżenia GTFS Gdyni przy starcie, utrzymywania wszystkich geometrii i rozkładów w RAM oraz ponownego pobierania planowych danych PLK. Najszybszą poprawę dostępności przystanków da emisja lokalnego katalogu **przed** odświeżeniem sieciowym.

Analiza obejmuje network, data, database, zasoby startowe i wywołującą je logikę ViewModelu. Nie obejmuje renderowania, rekompozycji ani pamięci MapLibre. ViewModel analizowany jest wyłącznie jako źródło zapytań, zadań i przetwarzania danych.

**Status dowodów:**

- **Kod** — potwierdzony przebieg wykonania lub struktura danych; nie oznacza zmierzonego czasu wykonania.
- **Pomiar zasobów** — rozmiary i liczności rzeczywistych plików z repozytorium.
- **Hipoteza wpływu** — przewidywany wpływ na czas, alokacje, GC lub RAM; do potwierdzenia profilerem.
- **API** — publiczna dokumentacja dostawcy sprawdzona podczas analizy; ograniczenia weryfikacji opisano w sekcji 6.

Na etapie audytu nie uruchamiano aplikacji na urządzeniu ani benchmarków. Implementację zmieniono później w opisanym wyżej pakiecie quick winów. Nie ma podstaw do deklarowania konkretnej liczby zaoszczędzonych MB lub procentu przyspieszenia.

### Priorytety

P0 oznacza pierwszy pakiet prac, P1 następny etap, P2 optymalizacje po pomiarach.

| ID | Priorytet | Problem | Oczekiwany efekt |
| --- | --- | --- | --- |
| D1 | P0 | Lokalny katalog przystanków czeka na sieć | Krótszy czas do użytecznych danych, także offline |
| D2 | P0 | Seed Gdyni przesłania datę snapshotu; start inicjuje pełny refresh | Mniej pobrań, parsowania i konkurencji o zasoby przy starcie |
| D3 | P0/P1 | Ponad milion punktów geometrii i pełny stop_times w cache procesu | Największy kandydat do redukcji szczytu i stałego RAM |
| D4 | P0 | Pełne logowanie odpowiedzi HTTP | Mniej kopiowania, formatowania i I/O |
| D5 | P0 | PLK: do czterech żądań na cykl, ponowne pobieranie planu | Mniejszy transfer, opóźnienia i ryzyko wyczerpania limitu |
| D6 | P1 | Seed Gdańska zawiera 16 dni; parser tworzy cały graf DTO | Mniejszy koszt pierwszej instalacji i zmiany wersji cache |
| D7 | P1 | Gdynia: skanowanie rozkładu dla każdego odjazdu | Mniej CPU i obiektów tymczasowych |
| D8 | P1 | Brak cache tras Gdańska i wspólnego snapshotu GPS | Szybsze wybieranie tras, mniej powtarzalnych zapytań |
| D9 | P1 | Pełna baza pojazdów ładowana do każdego odświeżenia odjazdów | Szybsze odjazdy i mniej alokacji |
| D10 | P1 | Niespójne odświeżanie, anulowanie i zapis snapshotu | Brak powielonej pracy i utraty dobrego cache |
| D11 | P2 | Nadmiarowe odczyty i nieusuwane rekordy Room | Mniejszy koszt katalogu i kontrolowany rozmiar bazy |
| D12 | P2 | Pełne wyszukiwanie/sortowanie danych także bez aktywnego wyszukiwania | Mniej CPU podczas działania |
| D13 | P1/P2 | Inicjalizacja kolei i kontrakt lokalizacji | Usunięcie ryzyk oczekiwania i powtórnych prób |

## 2. Co jest już zoptymalizowane

Stan kodu jest nowszy od opisu w AGENTS.md:

- Android i JVM pobierają ZIP GTFS do pliku i czytają wpisy po liniach. Nie trzeba wdrażać tej samej optymalizacji ponownie.
- Gdynia ma snapshot na dysku oraz dwa osobne indeksy w assets. Geometrie z assets są ładowane dopiero na żądanie; jednak pełny refresh startowy ponownie ładuje wszystkie geometrie.
- Przystanki są przechowywane w Room; jest dobowy TTL i fallback po błędzie odświeżenia.
- PLK już używa `operations/shortened`, filtrowania stacji/przewoźnika, `fullRoutes=false` dla zbiorczego wykonania i `dictionaries=false` dla planu.
- Polling odjazdów i pojazdu jest zatrzymywany przez `ScreenPaused`. Nie jest prawdą, że wszystkie zapytania stale działają w tle.
- Geometria jest współdzielona według `shapeId`, a nie kopiowana osobno dla każdego tripId.

Zachować te rozwiązania. Praca dotyczy ich polityki, granic pamięci i brakujących elementów.

## 3. Zmierzone rozmiary lokalnych danych

MiB = 1 048 576 bajtów. Poniżej dane repozytorium, **nie rozmiary aktualnych odpowiedzi produkcyjnych ani heapu Androida**.

| Zasób | Rozmiar pliku | Po dekompresji | Liczność |
| --- | --- | --- | --- |
| `departure_match_index.json.gz` | 1 238 910 B / 1,18 MiB | 15 784 822 B / 15,05 MiB JSON | 1 152 przystanki, 241 402 wpisy |
| `shape_index.json.gz` | 14 072 245 B / 13,42 MiB | 73 563 935 B / 70,16 MiB JSON | 9 932 mapowania kursów, 3 008 geometrii, 1 244 976 punktów |
| `gdansk_stops.json` | 15 304 307 B / 14,60 MiB | Bez kompresji w pliku źródłowym | 16 dni, 35 430 rekordów przystanków łącznie |
| `gdynia_stops.json` | 326 789 B | Bez kompresji w pliku źródłowym | 1 153 przystanki |
| `relations.json` | 117 129 B | Bez kompresji w pliku źródłowym | 696 wpisów |
| `rail_stations.json` | 7 867 B | Bez kompresji w pliku źródłowym | 47 stacji |
| `script/gdynia_source/gtfs.zip` | 17 819 469 B / 16,99 MiB | `shapes.txt`: 52 548 326 B; `stop_times.txt`: 14 426 430 B | Odpowiednio 1 244 976 i 241 402 wiersze |

Lokalne `calendar_dates.txt` ma dodatkowo 279 783 wiersze. Oba indeksy Gdyni mają `generatedAtUtc=2026-07-27T20:24:22.720314+00:00`; nie świadczy to o aktualności rozkładu w październiku.

Sam zapis dwóch współrzędnych Double dla 1 244 976 punktów wymaga około **19 MiB wartości liczbowych**. To wyłącznie dolne oszacowanie reprezentacji: obiekty, referencje, listy, mapy i obiekty pomocnicze zajmują dodatkową pamięć. Nie należy utożsamiać 70,16 MiB JSON z konkretnym rozmiarem String ani całego grafu Kotlin/ART.

Pomiar wykonano przez odczyt długości plików, rozpakowanie gzip, policzenie elementów JSON oraz odczyt metadanych i wierszy CSV lokalnego ZIP. Nie pobierano nowych pełnych feedów.

## 4. Ścieżka startu i działania

### Android

1. `CommuterApp.onCreate` inicjuje kontekst bazy i Koin.
2. `MainActivity` utrzymuje splash do zakończenia `RailStationsSeedSeeder.seedIfEmpty()`.
3. Oddzielna korutyna uruchamia `GdyniaGtfsPreloader.preload()`, potem `refresh()`.
4. `setContent` uruchamia aplikację niezależnie od zakończenia seeda. ViewModel zaczyna pobierać przystanki.
5. `RealTransitStopsRepository` czyta Room, ewentualnie zapisuje seed, następnie przy nieważnym cache czeka na `CombinedTransitDataSource.getStops()`.
6. Combined pobiera Gdańsk, potem Gdynię, potem lokalny katalog kolejowy; dopiero po zapisaniu wyniku repozytorium emituje listę.

Splash nie czeka bezpośrednio na GTFS. GTFS może jednak równolegle obciążać CPU, dysk, sieć i GC — ten wpływ na start wymaga pomiaru.

Dla nieważnego cache czas do przystanków obejmuje w przybliżeniu: odczyt/seed + Gdańsk + Gdynia + kolej + zapis + ponowny odczyt. Dla ważnego cache nie ma tego oczekiwania na sieć, ale pozostają dwa pełne odczyty katalogu.

### Działająca aplikacja

- Odjazdy miejskie: kolejne wykonanie po zakończeniu żądania i 30 s przerwy.
- Kolej: po zakończeniu cyklu i 60 s przerwy.
- GPS wybranego pojazdu Gdańska: pełny feed pozycji, następnie 10 s przerwy.
- Trasa: nowe pobranie po każdym wyborze odjazdu; poprzednie zadanie trasy nie ma własnego anulowanego Job.
- Wynik odjazdów ograniczany jest do 15 dopiero w ViewModelu, po pobraniu i przetworzeniu danych.

## 5. Wąskie gardła i rekomendacje

### D1. Lokalny katalog jest dostępny, ale użytkownik nadal czeka na sieć

**Status po quick winach:** Częściowo zrobione: lokalna emisja i serializacja refreshu; niezależni providerzy pozostają otwarci.

**Kod:** [RealTransitStopsRepository.kt](../shared/data/src/commonMain/kotlin/pl/bkacala/threecitycommuter/repository/stops/RealTransitStopsRepository.kt), linie 30–58; [CombinedTransitDataSource.kt](../shared/network/src/commonMain/kotlin/pl/bkacala/threecitycommuter/client/CombinedTransitDataSource.kt), 30–39.

Seed i stary katalog są fallbackiem dopiero po zakończeniu próby odświeżenia. Dostawcy wykonywani są sekwencyjnie. Awaria Gdańska zatrzymuje także próbę pobrania Gdyni i kolei w tym wywołaniu.

**Zmiana:** najpierw wyemitować Room lub przygotowany seed; odświeżać niezależnie dla dostawców, zapisując i publikując każdy poprawny wynik. Rozdzielić `observeStops()` od koordynowanego `refreshIfNeeded()`, aby anulowanie konsumenta po pierwszym wyniku nie anulowało przypadkowo całego refreshu. Utrzymywać osobne wersje i daty aktualizacji dla dostawców. Dopuszczać najwyżej ograniczoną liczbę równoległych pobrań — równoległość bez limitu zwiększy szczyt RAM.

**Kryterium:** przy zawieszonym endpointcie pierwsza lista lokalna zostaje opublikowana bez czekania na timeout; błąd jednego miasta nie blokuje drugiego.

### D2. Dobowy TTL Gdyni nie chroni typowego restartu procesu Androida

**Status po quick winach:** Zrobione w quick winach: świeży snapshot pomija startowe parsowanie i pobranie; odczyt danych na żądanie.

**Kod:** [GdyniaGtfsStore.kt](../shared/network/src/commonMain/kotlin/pl/bkacala/threecitycommuter/client/GdyniaGtfsStore.kt), 99–150; [MainActivity.kt](../composeApp/android/src/main/kotlin/pl/bkacala/threecitycommuter/MainActivity.kt), 49–55.

`ensureDepartureCacheLoaded()` wybiera bundled index przed persisted snapshotem. Ta ścieżka nie ustawia `downloadedAt`. Następujące po niej `refresh()` widzi null i pobiera `trips` oraz ZIP, nawet gdy na dysku istnieje świeży snapshot. To ustalenie dotyczy nowego procesu z poprawnie dostępnym seedem Androida, a nie każdego odtworzenia Activity.

**Zmiana:** czytać mały manifest cache przed ładowaniem danych. Wybrać najnowszą poprawną generację: aktualny cache dyskowy, następnie seed, następnie sieć. Sprawdzać TTL/wersję bez parsowania całego ZIP. Start nie powinien bezwarunkowo inicjować pełnego refreshu; przenieść go poza krytyczną ścieżkę dostępności danych i wykonywać tylko wtedy, gdy potrzebny.

**Kryterium:** dziesięć restartów procesu w okresie ważności snapshotu powoduje zero pobrań GTFS i zero pełnych importów. Pierwsza instalacja nadal działa na seedzie.

### D3. GTFS jest czytany strumieniowo, ale wynik w całości zostaje w pamięci

**Status po quick winach:** Częściowo: pierwszy odczyt odjazdów z dysku nie tworzy geometrii ani Route.Stop. Pełny import sieciowy i cache geometrii nadal wymagają przebudowy.

**Kod:** [GdyniaGtfsStore.kt](../shared/network/src/commonMain/kotlin/pl/bkacala/threecitycommuter/client/GdyniaGtfsStore.kt), 235–375 i 413–443; [AssetBackedGdyniaGtfsSeedSource.kt](../shared/network/src/androidMain/kotlin/pl/bkacala/threecitycommuter/client/AssetBackedGdyniaGtfsSeedSource.kt), 9–20.

Każde pełne odświeżenie buduje:

- 241 tys. wpisów dopasowania odjazdów i osobne obiekty `Route.Stop` dla tych samych wierszy rozkładu;
- mapę wszystkich kursów do geometrii i wszystkie punkty wszystkich geometrii;
- tymczasowe `ShapePoint`, listy sortowane i listy wynikowe.

Przy odświeżeniu poprzednie cache mogą nadal żyć podczas budowania nowych. Odczyt assets tworzy pełny String JSON, graf DTO i docelowe obiekty. Są to różne źródła szczytu pamięci, nawet mimo strumieniowego odczytu ZIP.

**Rekomendacja docelowa:** import raz na generację do lokalnego magazynu z indeksami, np. SQLite/Room. Przechowywać `trip -> shape/service/route`, odjazdy według przystanku/czasu/dnia oraz kolejność przystanków według kursu. Geometrię przechowywać jako zwarty blok na shapeId lub indeksowany plik; nie jest konieczna osobna encja Room dla każdego punktu. Ładować tylko geometrię i przystanki wybranej trasy, z LRU ograniczonym liczbą bajtów lub punktów.

**Etap przejściowy:** rozdzielić aktualizację indeksu odjazdów od materializacji geometrii; nie tworzyć `Route.Stop` dla całej sieci. Seed dostarczać w gotowym formacie indeksowanym lub dzielonym według klucza, bez jednego 70 MiB JSON. Samo przeniesienie parsera na inny dispatcher nie zmniejszy retencji.

**Kryterium:** samo otwarcie odjazdów nie ładuje geometrii; odwiedzenie kolejnych tras nie zwiększa cache bez granic; profiler potwierdza brak pełnego grafu sieci w sesji bez wybranej trasy.

### D4. LogLevel.BODY działa we wspólnym kliencie bez warunku debug

**Status po quick winach:** Zrobione: usunięto pełne logowanie HTTP.

**Kod:** [NetworkModule.kt](../shared/network/src/commonMain/kotlin/pl/bkacala/threecitycommuter/di/NetworkModule.kt), 38–52.

Pełne odpowiedzi tekstowe przechodzą przez logger i `println`. Dla dużych JSON to dodatkowe przetwarzanie i I/O. Dokładny wpływ pluginu na binarny ZIP należy zmierzyć; nie zakładamy, że loguje cały ZIP jako tekst. Androidowy odczyt stops Gdańska przez URLConnection omija tego klienta.

**Zmiana:** w release wyłączyć body logging; diagnostyka ma podawać endpoint, status, czas, rozmiar i liczbę rekordów. W debug body tylko na żądanie i dla ograniczonego zbioru małych odpowiedzi. Nagłówki uwierzytelniania muszą być ukryte.

**Kryterium:** release nie drukuje payloadów; porównać koszt tego samego feedu z logowaniem i bez niego.

### D5. PLK pobiera plan i dwa pełne wykonania w każdym cyklu odjazdów

**Status po quick winach:** Częściowo: ograniczony godzinny cache planu. Szczegółowe operacje, stronicowanie i budżet API pozostają otwarte.

**Kod:** [RailTransitDataSource.kt](../shared/network/src/commonMain/kotlin/pl/bkacala/threecitycommuter/client/RailTransitDataSource.kt), 44–122, 171–209 i 361–362; [KtorPlkApiClient.kt](../shared/network/src/commonMain/kotlin/pl/bkacala/threecitycommuter/client/KtorPlkApiClient.kt), 34–104.

Cykl to jedno `schedules`, następnie równolegle jedno `operations/shortened` i do dwóch `operations/train/...`. Oczekiwanie na wolne wykonanie szczegółowe opóźnia publikację całej tablicy. Plan pobierany jest z `fullRoute=true`, bez cache. Lista zawiera odjazdy do końca dnia, choć odbiorca pokazuje 15.

Przy dwóch odjazdach kwalifikujących się do zapytań szczegółowych i krótkim czasie odpowiedzi to około **240 żądań/h aktywnej tablicy**. Faktyczna częstotliwość jest niższa o czas cyklu; nie jest to pomiar ruchu. Przy wspólnym kluczu API ruch wielu instalacji się sumuje.

**Zmiany w kolejności:**

1. Cache planu według daty kursowania, stacji, przewoźnika i wersji planu. Przechowywać kompaktowy wynik potrzebny tablicy; szczegóły tras osobno.
2. Nie wykonywać dwóch zapytań szczegółowych automatycznie, jeżeli skrócone wykonanie zawiera wymagane dane. Najpierw test porównawczy kompletności; szczegóły pobierać dla wybranego pociągu albo uzasadnionego braku danych.
3. Publikować dostępny plan niezależnie od wolnego wykonania, oznaczając świeżość. Obecne `take(1)` w ViewModelu trzeba dostosować do późniejszego wzbogacenia — inaczej odbiorca odrzuci aktualizację.
4. Nie zmniejszać `pageSize` w ciemno. Teraz pobierana jest tylko pierwsza strona 500 wpisów; DTO shortened nie przechowuje metadanych stronicowania. Dodać ich obsługę i test kompletności.
5. Ograniczać wynik domenowy do uzgodnionego horyzontu/top-N dopiero po właściwym połączeniu opóźnień i planu. Nie usuwać opóźnionych kursów na podstawie samego czasu planowego.
6. Dodać budżet zapytań i obsługę 429. Sam cache planu pozostawia do 180 żądań/h, jeśli dwa szczegółowe wykonania nadal są pobierane co minutę.

Możliwości oraz ograniczenia API opisano w sekcji 6. Zmiana `fullRoute` wymaga sprawdzenia kierunku i stacji docelowej; aktualny mapper wyprowadza je z pełnej trasy.

### D6. Gdańsk materializuje katalog wielu dni, aby wybrać jeden

**Status po quick winach:** Częściowo: mały seed jednego dnia i generator. Selektywny parser sieciowy oraz timeouty URLConnection pozostają otwarte.

**Kod:** [GdanskTransitDataSource.kt](../shared/network/src/commonMain/kotlin/pl/bkacala/threecitycommuter/client/GdanskTransitDataSource.kt), 46–54; [GdanskStopsPayloadReader.android.kt](../shared/network/src/androidMain/kotlin/pl/bkacala/threecitycommuter/client/GdanskStopsPayloadReader.android.kt), 14–17.

Seed dekoduje 35 430 rekordów z 16 dni i wybiera zestaw o największym `lastUpdate`. To data publikacji, nie data obowiązywania. Dla obecnego pliku pierwszy zestaw z maksymalnym timestampem to 19 sierpnia, 1 580 rekordów. Odczyt sieciowy Android/JVM dekoduje cały obiekt przez stream, następnie bierze pierwszy zestaw.

**Zmiana:** generować mały seed jednego jawnie wybranego dnia/wariantów wymaganych przez produkt, z manifestem ważności. Dla feedu wielodniowego selektywnie odczytać właściwy klucz daty, pomijając inne części bez tworzenia DTO. Ujednolicić regułę wyboru daty i fallback poza zakresem feedu.

Odczyt `URL.openStream()` omija Ktor i nie ustawia własnych timeoutów. Zastąpić kontrolowanym transportem strumieniowym z timeoutami i anulowaniem; nie wracać przy tym do `bodyAsText()` całego zasobu.

**Kryterium:** seed nie parsuje 16 kopii katalogu; poprawna data jest testowana; sieć nie blokuje lokalnej emisji.

### D7. Dopasowanie Gdyni skanuje i normalizuje te same dane wielokrotnie

**Status po quick winach:** Do zrobienia.

**Kod:** [GdyniaTransitDataSource.kt](../shared/network/src/commonMain/kotlin/pl/bkacala/threecitycommuter/client/GdyniaTransitDataSource.kt), 92–147; [GdyniaGtfsStore.kt](../shared/network/src/commonMain/kotlin/pl/bkacala/threecitycommuter/client/GdyniaGtfsStore.kt), 56–85 i 478–480.

Dla każdego odjazdu wykonywane są `withContext`, pobranie cache, filtracja listy przystanku i `split/take/joinToString` dla czasu. Koszt to około O(D × S), gdzie D to liczba odjazdów w odpowiedzi, S liczba wpisów rozkładowych przystanku. Dochodzą listy i Stringi tymczasowe. Pierwszy odjazd czeka także na `/pt/routes`; brak odpowiedzi tego słownika blokuje całą tablicę.

**Zmiana:** przeliczać czas na sekundy/minuty dnia usługowego i normalizować kierunek podczas importu. Dopasowywać całą odpowiedź jednym wywołaniem repozytorium, zapytaniem indeksowanym lub ograniczonym cache przystanku. Klucz powinien uwzględniać linię, dzień/serviceId i kierunek, nie tylko godzinę. Użyć danych `trips.txt` i `calendar_dates.txt` z tej samej generacji.

Nazwy linii przechowywać trwale wraz z datą/wersją; obecna mapa żyje cały proces bez TTL. Brak metadanych powinien dopuścić odjazd z zastępczą etykietą. Rozwiązanie tripId do geometrii można opóźnić do wyboru odjazdu, pod warunkiem zachowania stabilnego klucza odjazdu i jawnej obsługi niejednoznaczności.

**Kryterium:** dopasowanie nie skanuje całego rozkładu przystanku dla każdego wiersza, zachowuje poprawne kursy przy tej samej godzinie, różnych liniach i dniach.

### D8. Gdańsk: ponowne pobieranie tras i całego feedu GPS

**Status po quick winach:** Częściowo: interwał GPS wynosi 20 s. Cache tras i wspólny snapshot GPS pozostają otwarte.

**Kod:** [GdanskTransitDataSource.kt](../shared/network/src/commonMain/kotlin/pl/bkacala/threecitycommuter/client/GdanskTransitDataSource.kt), 64–77; [KtorGdanskApiClient.kt](../shared/network/src/commonMain/kotlin/pl/bkacala/threecitycommuter/client/KtorGdanskApiClient.kt), 58–88; [MapScreenViewModel.kt](../shared/ui/src/commonMain/kotlin/pl/bkacala/threecitycommuter/ui/screen/map/MapScreenViewModel.kt), 357–405.

Wybór trasy pobiera kolejno shape i stopTimes całej linii/dnia; ponowny wybór powtarza oba żądania i obliczenia. Tracking pobiera wszystkie pozycje, żeby wybrać jedną, potencjalnie około 360 razy/h aktywnego śledzenia przy pomijalnej latencji.

**Zmiana:** cache geometrii według dostawcy/daty/routeId/tripId/wersji; stopTimes osobno według daty i routeId, z ograniczeniem rozmiaru. Nie cache'ować konkretnej trasy z godzinami wyłącznie pod tripId — ten identyfikator opisuje wariant, a nie pojedynczy odjazd. Przy braku cache niezależne żądania można zrównoleglić w ramach limitu.

Dla GPS: jeden współdzielony snapshot dostawcy, krótki TTL i deduplikacja trwającego żądania. Porównywać timestamp źródła; wstępnie przetestować interwał 20–30 s zamiast 10 s. Nie zakładać dostępności endpointu GPS pojedynczego pojazdu.

**Kryterium:** ponowny wybór tej samej trasy/dnia nie pobiera ponownie statycznych danych; dwóch odbiorców GPS współdzieli jedno żądanie.

### D9. Metadane pojazdów opóźniają i obciążają odjazdy

**Status po quick winach:** Częściowo: cache floty i indeks po kodzie, fallback i backoff. Odświeżenie metadanych nadal jest oczekiwane przez combine.

**Kod:** [GetDeparturesUseCase.kt](../shared/data/src/commonMain/kotlin/pl/bkacala/threecitycommuter/usecase/GetDeparturesUseCase.kt), 15–25; [RealVehiclesRepository.kt](../shared/data/src/commonMain/kotlin/pl/bkacala/threecitycommuter/repository/vehicles/RealVehiclesRepository.kt), 38–63.

Każdy cykl Gdańska odczytuje wszystkie pojazdy z Room i mapuje pełne encje. Dla każdego odjazdu `firstOrNull` liniowo szuka pojazdu i powtarza konwersję vehicleCode na Int. `combine` czeka na metadane; błąd ich odświeżenia może odebrać użytkownikowi poprawne odjazdy.

**Zmiana:** mały indeks metadanych według numerycznego kodu, przebudowywany tylko po zmianie bazy, albo zapytanie Room dla potrzebnego zbioru kodów. Emitować odjazdy z dostępnymi metadanymi, resztę uzupełniać później. Deduplikować odświeżenie i zachować poprzednią bazę przy awarii. `updateVehiclesBase()` powinno aktualizować tę samą metrykę świeżości co `ensureUpToDate()`.

**Kryterium:** brak pełnego odczytu floty co 30 s; brak zależności dostępności odjazdów od sieciowej aktualizacji metadanych.

### D10. Współbieżność i awarie mogą powielać drogie operacje

**Status po quick winach:** anulowanie poprzedniej trasy i propagacja anulowania w zmienionych ścieżkach. **Aktualizacja 14.10.2026:** wdrożono wspólny koordynator GTFS, atomową publikację generacji, ograniczony negative cache i backoff; szczegóły oraz granice opisano na początku dokumentu. Poniższe ustalenia opisują stan bazowy audytu.

**Kod:** [GdyniaGtfsStore.kt](../shared/network/src/commonMain/kotlin/pl/bkacala/threecitycommuter/client/GdyniaGtfsStore.kt), 99–178, 262–265, 460–475; [FileBackedGdyniaGtfsSnapshotStorage.kt](../shared/network/src/androidMain/kotlin/pl/bkacala/threecitycommuter/client/FileBackedGdyniaGtfsSnapshotStorage.kt), 26–55; [MapScreenViewModel.kt](../shared/ui/src/commonMain/kotlin/pl/bkacala/threecitycommuter/ui/screen/map/MapScreenViewModel.kt), 181–245.

- Osobne mutexy ładowania i refreshu nie obejmują wszystkich ścieżek pobrania; możliwe są równoległe pełne importy. `applyFullCache` aktualizuje oba cache poza jednym wspólnym mechanizmem publikacji.
- Brak przystanków konkretnego tripId uruchamia odczyt całego snapshotu, potem potencjalnie pobranie sieciowe — bez sprawdzenia TTL w tej ścieżce. Powtórzenie nieistniejącego kursu powtarza pracę.
- ZIP i trips zapisywane są do docelowych plików przed sprawdzeniem, czy komplet da się poprawnie sparsować. Przerwany download może zastąpić dobry snapshot.
- Część `catch(Throwable)`/runCatching traktuje anulowanie jako awarię; długie synchroniczne pętle parsera nie sprawdzają anulowania.
- Zadania ładowania tras po szybkich kolejnych kliknięciach nadal wykonują pracę; sprawdzenie klucza chroni przed częścią nieaktualnych publikacji, ale nie przed kosztem żądań.

**Zmiana:** jeden koordynator odświeżenia na generację (współdzielony Deferred/single-flight), atomowy niezmienny snapshot stanu, krótki negative cache brakujących tripId związany z wersją danych. Pobierać do katalogu tymczasowej generacji, walidować komplet, następnie atomowo przełączyć manifest. Uwzględnić zgodność trips i ZIP; rozważyć trips.txt z tego samego ZIP zamiast osobnego /pt/trips po teście zgodności identyfikatorów.

Propagować CancellationException, okresowo sprawdzać anulowanie w parserze i ograniczyć liczbę ciężkich importów do jednego. Dodać anulowany Job/flatMapLatest dla tras. Ustalić jawne timeouty, ograniczone retry dla przejściowych błędów i backoff; nie ponawiać agresywnie 401/403/429.

**Kryterium:** jednoczesny preload, otwarcie odjazdów i wybór trasy wywołują najwyżej jeden import; przerwanie zapisu zachowuje stary snapshot; kolejne zapytanie o brakujący kurs nie pobiera od razu ponownie ZIP.

### D11. Room: zbędne pełne odczyty i brak usuwania nieaktualnych rekordów

**Status po quick winach:** Częściowo: ważny katalog jest odczytywany raz. Projekcje, wymiana snapshotu i migracje pozostają otwarte.

**Kod:** [TransitStopsDao.kt](../shared/database/src/commonMain/kotlin/pl/bkacala/threecitycommuter/dao/TransitStopsDao.kt), 11–15; [TransitStopEntity.kt](../shared/database/src/commonMain/kotlin/pl/bkacala/threecitycommuter/model/stops/TransitStopEntity.kt); [DatabaseModule.kt](../shared/database/src/commonMain/kotlin/pl/bkacala/threecitycommuter/di/DatabaseModule.kt), 9–12.

Sprawdzenie, czy katalog istnieje, materializuje całą listę. Potem lista odczytywana jest ponownie. Upsert nie usuwa przystanków wycofanych z feedu; z czasem katalog może zawierać zbędne rekordy. Pobierane są wszystkie kolumny, choć większość odczytów potrzebuje tylko identyfikacji, nazwy, pozycji i typu.

**Zmiana:** `EXISTS/COUNT` do kontroli obecności lub wykorzystanie raz pobranej listy. Projekcja małego modelu katalogu; szczegóły na żądanie. Transakcyjna wymiana kompletnego snapshotu **jednego dostawcy**, z usuwaniem wyłącznie rekordów nieobecnych w poprawnie zweryfikowanej nowej generacji. Nie usuwać danych przy błędzie lub niepełnym feedzie.

Sprawdzić `EXPLAIN QUERY PLAN` przed dodawaniem indeksów; klucz złożony provider/sourceStopId już istnieje. Nie zakładać, że kolejny indeks będzie istotniejszy od usunięcia pełnego odczytu. Przy rozbudowie trwałego cache dodać migracje zamiast polegać na destructive migration, które ponownie wymusza seed/pobieranie.

### D12. Nadmiarowe przetwarzanie katalogu poza Compose

**Status po quick winach:** Do zrobienia.

**Kod:** [MapScreenViewModel.kt](../shared/ui/src/commonMain/kotlin/pl/bkacala/threecitycommuter/ui/screen/map/MapScreenViewModel.kt), 407–420 i 479–501; [RailStaticCatalog.kt](../shared/network/src/commonMain/kotlin/pl/bkacala/threecitycommuter/client/RailStaticCatalog.kt), 49–63.

Wyszukiwanie filtruje, oblicza odległości, sortuje i buduje modele wszystkich pasujących przystanków, także przy nieaktywnej wyszukiwarce i pustym zapytaniu. Część obliczeń wykonuje się wewnątrz `StateFlow.update`, którego lambda może być wywołana ponownie przy konkurencji. Dopasowanie najbliższego przystanku trasy wykonuje zagnieżdżone wyszukiwanie liniowe. Katalog kolejowy tworzy mapy w getterach.

**Zmiana:** wydzielić indeks nazw i lookup po TransitStopKey, przeliczać wyniki tylko przy rzeczywistej potrzebie; ograniczyć top-N, dodać debounce zapytania i próg zmiany lokalizacji. Obliczać wynik poza lambdą aktualizacji, z kontrolą wersji wejścia. Geoprzestrzenny indeks lub FTS dopiero po wykazaniu potrzeby. Dla 47 stacji kolejowych proste mapy są wystarczające — niski priorytet.

### D13. Inicjalizacja kolei i niekończący się strumień lokalizacji

**Status po quick winach:** Do zrobienia.

**Kod:** [MainActivity.kt](../composeApp/android/src/main/kotlin/pl/bkacala/threecitycommuter/MainActivity.kt), 40–61; [RailTransitDataSource.kt](../shared/network/src/commonMain/kotlin/pl/bkacala/threecitycommuter/client/RailTransitDataSource.kt), 163–169; [ProviderTransitDataSource.kt](../shared/network/src/commonMain/kotlin/pl/bkacala/threecitycommuter/client/ProviderTransitDataSource.kt), 20; [AndroidLocationRepository.kt](../shared/data/src/androidMain/kotlin/pl/bkacala/threecitycommuter/repository/location/AndroidLocationRepository.kt), 20–48.

Seed kolejowy liczy tylko 47 stacji — sam plik nie jest głównym konsumentem pamięci. Jednak setContent może wystartować przed końcem seeda, a `catalogLoaded` może utrwalić pusty odczyt. Rail nie nadpisuje domyślnego pustego `getBundledStops()`, więc pierwszy start offline może dostać tylko miejskie przystanki. Uporządkować inicjalizację repozytorium kolejowego lub zapewnić mu własny lokalny fallback, bez blokowania startu na sieci.

Lokalizacja wywołuje jednorazowe `getCurrentLocation`, emituje wynik i pozostaje w `awaitClose`. Kolektor w pętli ViewModelu nie kończy się, więc jego delay 10 s nie ustanawia regularnego odpytywania Androida. Nie interpretować tej pętli jako faktycznego GPS co 10 s.

**Zmiana:** jawny kontrakt: suspend dla pojedynczej lokalizacji z prawidłowym tokenem anulowania i timeoutem albo jeden strumień aktualizacji z usuwaniem listenera. To problem poprawności cyklu życia; nie ma dowodu na duży wyciek pamięci.

## 6. Możliwości i ograniczenia API dostawców

### Gdańsk

Dokumentacja z 21.05.2026 opisuje statyczne dane aktualizowane dobowo, zasób dat aktualizacji, około 20-sekundowy cache GPS i 20-sekundowy cache odjazdów. Udostępnia także GTFS-RT. To uzasadnia osobne polityki statyczne/live i eksperyment z rzadszym pollingiem GPS. Nie znaleziono potwierdzenia filtrowania GPS po jednym vehicleId. Dla stopTimes udokumentowano zakres linia + dzień; cache powinien odpowiadać tej jednostce pobrania. [Dokumentacja ZTM Gdańsk](https://ckan.multimediagdansk.pl/dataset/c24aa637-3619-4dc2-a171-a23eec8f2172/resource/2daba886-922f-497e-a39a-8e9b0a010e28/download/opis-zbioru-otwarte-dane-ztm-w-gdasku.pdf)

Zasób dat aktualizacji nie obejmuje automatycznie każdego pliku — sprawdzić pokrycie konkretnego feedu. GTFS-RT jest opcją eksperymentalną dla transferu GPS, nie gwarancją mniejszego heapu ani szybszego startu; wymaga sprawdzenia dostępności pól i mapowania identyfikatorów. Dla pojedynczej tablicy zachować endpoint pojedynczego przystanku, zamiast pobierać wszystkie odjazdy miasta.

### Gdynia

Oficjalna dokumentacja udostępnia stops, routes, trips, stop_times, calendar_dates i delays z obowiązkowym stopId. Nie dokumentuje filtrowania stop_times po stopId/tripId ani publicznego feedu GPS pojazdów. Nie opierać planu na takich możliwościach bez testu. Historyczny opis shape_id jako nieużywanego różni się od danych obsługiwanych przez kod i lokalny ZIP — rzeczywisty kontrakt wymaga sprawdzenia na aktualnym feedzie. [Dokumentacja TRISTAR Gdynia](https://otwartedane.gdynia.pl/dataset/fc4f3a7c-b877-4fe1-ab22-3fb54e6513f7/resource/a426e7b7-7261-4f43-8acb-59a5eca167aa/download/tristar_api.pdf)

Wniosek projektowy: pobierać zbiorczy rozkład rzadko, indeksować lokalnie i wykonywać małe lokalne zapytania. Połączenie kalendarza i kursów jest potrzebne również dla poprawności dopasowania. Nie zamieniać ZIP na pełny JSON stop_times bez porównania transferu i kosztu parsowania.

### PLK: SKM i PKM w aktualnym kodzie

API dokumentuje filtrowanie stacji/przewoźników, shortened i stronicowanie operacji. Limity godzinowe/dzienne wynoszą: Basic 100/1 000, Standard 500/5 000, Premium 2 000/20 000. Publikuje nagłówki pozostałego budżetu. Poziomu przyznanego projektowi nie sprawdzano. [Dokumentacja API PLK](https://pdp-api.plk-sa.pl/api-documentation)

OpenAPI rozróżnia `fullRoute` dla schedules i `fullRoutes` dla operations. `data-version` ma osobne `schedulesVersion` i `operationsVersion`; planu nie należy unieważniać na każdą zmianę wersji wykonania. Opis `fullRoute=false` odnosi się do przycinania from→to, więc nie daje podstaw, by obiecać taki sam zysk przy filtrowaniu samą stacją. Nie potwierdzono filtra godzinowego dla schedules. [OpenAPI PLK](https://pdp-api.plk-sa.pl/swagger/v1/swagger.json)

Wniosek projektowy: kontrolę wersji wykonywać oszczędnie i współdzielić. Dodatkowe odpytywanie data-version co minutę może zużyć tyle żądań, ile oszczędzi cache. Po usunięciu zbędnych szczegółowych operacji bazowy polling raz/min to około 60 żądań/h plus odświeżenia planu. Dla wielu instalacji ze wspólnym kluczem rozważyć centralny cache/proxy i wspólny budżet; wymaga to osobnego zakresu infrastruktury.

Shortened zmniejsza nazwy pól na drucie, a nie liczbę rekordów. Aktualny mapper dodatkowo tworzy drugą reprezentację DTO; ewentualne mapowanie bezpośrednie do domeny to dalsza optymalizacja po ograniczeniu zakresu danych.

### Niezweryfikowane możliwości HTTP

Publiczne strony dokumentacji były dostępne. Próby dostępu do /pt/routes przez narzędzie web oraz próbne HEAD do trips, GTFS i GPS z lokalnego środowiska nie powiodły się. Nie potwierdzono ETag, Last-Modified, 304, kompresji ani aktualnych rozmiarów feedów. Nie używano PLK_KEY.

Wdrożenie warunkowych GET jest **warunkowe**: najpierw kontrolowany test konkretnego endpointu i poprawności 304. Bez wsparcia serwera stosować lokalny TTL/manifest lub udokumentowaną wersję danych. Brak dostępu w tym środowisku nie dowodzi niedostępności API dla aplikacji.

## 7. Docelowa polityka danych

Poniższe wartości to propozycje początkowe do strojenia, nie potwierdzone SLA.

| Dane | Trwałość i zakres | Odświeżanie | Zachowanie przy błędzie |
| --- | --- | --- | --- |
| Katalog przystanków | Room, generacja osobno per provider | Emituj lokalnie; sprawdź wersję/TTL dobowy w tle | Zachowaj ostatni poprawny katalog |
| GTFS Gdynia | Indeksowany magazyn na dysku | Co generację/TTL; jeden import; poza startem tablicy | Poprzednia poprawna generacja, seed jako fallback |
| Geometrie | Dysk + LRU, początkowo np. 50 tys. punktów RAM | Na wybór trasy | Brak geometrii nie blokuje odjazdów |
| Dopasowania Gdynia | Indeks stopId + czas + service/route | Zapytanie dla aktywnego przystanku | Jawne nierozwiązane tripId, bez pętli pobrań |
| Odjazdy miejskie | Krótki cache stopKey, wspólne żądanie | Startowo obecne 30 s; uwzględnij timestamp źródła | Pokaż ostatni wynik ze znacznikiem świeżości |
| GPS Gdańsk | Jeden snapshot providera w RAM | Eksperyment 20–30 s, wyłącznie aktywne śledzenie | Nie przedstawiaj starej pozycji jako bieżącej |
| Plan PLK | Cache stacja/przewoźnik/dzień/wersja | Wersja planu + awaryjny TTL, np. 1 h | Dostępny plan bez obietnicy danych live |
| Wykonanie PLK | Krótki cache właściwego dnia i pociągu/stacji | 60 s lub rzadziej zależnie od budżetu | Backoff i aktualna informacja o braku live |
| Metadane pojazdów | Room + kompaktowy lookup | Dobowo lub po zmianie źródła | Odjazdy działają bez wzbogacenia |

Każdy cache ma limit, klucz wersji i właściciela. Model czasu przechowuje dzień usługowy i offset, a nie Instant wyliczony raz podczas importu. Jest to konieczne przy zmianie doby, czasie ponad 24:00 i strefie Europe/Warsaw.

Obecne `toGdyniaInstantOrNull()` dodaje `(hour / 24).hours`: dla 25:00 dodaje jedną godzinę, a nie dobę. Poprawić przed utrwaleniem nowego indeksu. Nie przenosić błędu czasu do bardziej trwałego cache.

## 8. Plan wdrożenia

Szacunki oznaczają dni pracy jednej osoby po zapoznaniu się z kodem, bez czasu oczekiwania na dostawców. Nie są zobowiązaniem harmonogramowym.

| Etap | Zakres i zależności | Nakład orientacyjny | Warunek zakończenia |
| --- | --- | --- | --- |
| 0. Pomiar bazowy | Instrumentacja danych, scenariusze z sekcji 9 | 1–2 dni | Powtarzalne czasy, RAM, transfer, liczba requestów |
| 1. Szybkie zmiany P0 | D1, D2, D4; timeouty i propagacja anulowania z D10 | 2–4 dni | Cache lokalny bez oczekiwania na sieć; restart bez pełnego GTFS |
| 2. Kontrola kosztu PLK | D5, cache planu, budżet i test równoważności operacji | 2–4 dni | Plan nie jest pobierany co minutę; brak automatycznego 1+1+2 bez potrzeby |
| 3. Magazyn Gdyni | D3, D7, atomowa generacja/single-flight z D10; migracja i generator seedów | 4–7 dni | Odjazdy i pojedyncza trasa bez pełnego grafu GTFS w RAM |
| 4. Pozostała sieć i baza | D6, D8, D9, D11, poprawność czasu i inicjalizacji | 3–5 dni | Brak powtarzalnych statycznych pobrań i pełnych odczytów floty |
| 5. Dostrojenie | D12/D13, limity cache, testy długiej sesji | 1–3 dni | Cele pomiarowe spełnione bez regresji danych |

Etapy rozbić na osobne, mierzalne PR-y. Najpierw usunąć zbędną pracę, następnie zmienić reprezentację danych. Optymalizacje D12 wdrażać w pierwszej kolejności tylko wtedy, gdy profile pokażą ich znaczący udział.

### Konieczne testy regresji

- Cache przystanków emituje wynik przed ukończeniem zawieszonej sieci; niezależna awaria dostawcy; poprawna emisja kolejnych aktualizacji.
- Nowy proces ze świeżym snapshotem Gdyni; brak seeda; uszkodzony seed; stary snapshot; offline.
- Równoczesne trzy żądania do cache powodują jeden import; nieistniejący tripId ma negative cache.
- Przerwanie downloadu/importu zachowuje poprzednią generację; walidacja pustego i częściowego feedu.
- Dopasowanie Gdyni: różne linie o tej samej godzinie, kalendarz, trasy okrężne, północ, 25:00, zmiana czasu.
- Cache Gdańska rozróżnia dzień, wariant i konkretny kurs; daty seeda wybierane według jawnej reguły.
- PLK: zgodność shortened i szczegółowego wykonania dla opóźnień, odwołań i kierunku; więcej niż 500 rekordów; zmiana dnia; 429.
- Brak metadanych pojazdu nie blokuje odjazdów.
- Szybkie przełączanie tras anuluje poprzednią pracę; pauza ekranu kończy polling.
- Pierwszy start offline zawiera stacje kolejowe; lokalizacja ma poprawne zakończenie/anulowanie.

Rozszerzyć istniejące `RealTransitStopsRepositoryTest`, `TransitDataSourceTest` i `MapScreenViewModelTest`; nie zastępować pomiarów czasu testami jednostkowymi.

Po zmianach implementacyjnych na Windows: `gradlew.bat :shared:data:jvmTest :shared:network:jvmTest :shared:ui:jvmTest`, następnie `gradlew.bat lint`. Testy instrumentowane i profilowanie wymagają urządzenia/emulatora. Aktualizacja quick winów obejmuje kod i wymaga powyższych kontroli.

## 9. Jak mierzyć rezultat

Mierzyć build release/profileable bez body logging na tym samym urządzeniu średniej klasy, z osobnym przebiegiem debug do diagnostyki. Użyć stałych lokalnych fixture'ów do porównań A/B i oddzielnych prób z prawdziwą siecią. Nie mieszać zmienności dostawców z kosztem parsera.

### Scenariusze

1. Pierwsza instalacja, pusta baza: online, offline i wolna sieć.
2. Zimny start procesu ze świeżym cache oraz z cache po TTL.
3. Otwarcie odjazdów oddzielnie dla Gdańska, Gdyni, SKM i PKM.
4. Pierwsza trasa Gdyni, powtórny wybór i seria 30 różnych tras.
5. 15–30 minut odjazdów oraz śledzenia Gdańska.
6. Przerwanie refreshu, szybkie przełączanie przystanków, pauza/wznowienie.
7. Sesja przez północ, przekroczenie limitu PLK, niedostępność jednego providera.

### Metryki

| Metryka | Sposób interpretacji |
| --- | --- |
| Proces → lokalny katalog dostępny | Oddzielić od pierwszej klatki i zdjęcia splasha |
| Żądanie → pierwsze odjazdy / komplet wzbogacenia | Osobno dla dostawców i stanu cache |
| Czas pobrania, dekompresji, parsowania, indeksowania, zapisu | Osobne odcinki, bez sumowania równoległych etapów jako czasu użytkownika |
| Heap przed/po, szczyt heapu, retained size dominatorów | Szukać String/ByteArray, GeoPoint, ShapePoint, StopTimeLookup, Route.Stop |
| Alokacje i pauzy GC | Osobno import i stabilny polling |
| PSS i native heap | Nie przypisywać całego procesu warstwie danych; wyłączyć wpływ mapy w harnessie danych |
| Bajty i liczba requestów per endpoint | Cache hit/miss, 304, powtórne importy, błędy, 429 |
| Liczba aktywnych zadań i rozmiar cache | Sprawdzać po anulowaniu i po serii przełączeń |

Wykonać minimum 10 porównywalnych startów i raportować medianę oraz rozrzut; do wiarygodniejszego p95 zwiększyć próbę, np. do 30. Trace'y aplikacyjne i profiler pamięci powinny wskazać dominujące klasy i czas importu. Heap dump wykonywać w kontrolowanych punktach, ponieważ sam zaburza czas wykonania.

### Proponowane bramki akceptacji

- Dostępność lokalnego katalogu nie zależy od sieci; cel początkowy p95 poniżej 500 ms od wejścia do repozytorium na urządzeniu referencyjnym, do kalibracji po etapie 0.
- Zero pobrań/importów GTFS podczas startów ze świeżą generacją.
- Zero załadowanych geometrii Gdyni przy samej tablicy odjazdów.
- Jeden import naraz i ograniczony cache tras; po serii tras pamięć stabilizuje się w ramach budżetu.
- Cel eksperymentalny: co najmniej 50% mniej szczytowych alokacji importu Gdyni względem pomiaru bazowego. Ostateczny budżet MB ustalić po profilu; nie jest to prognoza wyniku.
- Bez potrzeby szczegółowych operacji: około jednego żądania live PLK/min aktywnej tablicy plus kontrolowany koszt planu/wersji, w granicach przyznanego limitu.
- Poprawne godziny, kierunki, kompletność i tryb offline muszą przejść testy przed uznaniem optymalizacji za zakończoną.

## 10. Decyzje, których nie podejmować bez dowodów

- Nie zaczynać od zmiany silnika HTTP, DI, zwiększania heapu lub ręcznego wywoływania GC.
- Nie zakładać, że gzip rozwiązuje retencję obiektów po dekodowaniu.
- Nie wdrażać nieograniczonego cache całych odpowiedzi jako rozwiązania liczby requestów.
- Nie pobierać wszystkich dostawców i wszystkich tras równolegle bez limitu.
- Nie zmniejszać pageSize, nie usuwać pełnych tras PLK ani nie ograniczać listy odjazdów przed sprawdzeniem kompletności.
- Nie traktować iOS jako działającego punktu odniesienia: ZIP reader jest stubem, a downloader materializuje ByteArray; optymalizację iOS połączyć z uruchomieniem tej platformy.
- Nie mylić ciężkich obliczeń w tle z blokowaniem głównego wątku. W tym projekcie wiele operacji jest już na IO, lecz nadal konkuruje o pamięć i CPU.
