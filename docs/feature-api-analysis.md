# Analiza API dla rozkładu jazdy

Analiza rozpoczęta przed implementacją, zaktualizowana po review 20.10.2026. Stan początkowy: istniejące ekrany Compose korzystały z `DemoScheduleDataSource`. Obecny przepływ produkcyjny korzysta z adapterów opisanych poniżej. Generator demonstracyjny pozostał wyłącznie w `commonTest`.

## Kontrakty istniejącego projektu

- `shared/network/.../client/TransitDataSource.kt` i providerowe implementacje dostarczają przystanki, bieżące odjazdy i pojedyncze trasy. `CombinedTransitDataSource` wybiera źródło według `TransitStopKey(provider, sourceStopId)`.
- `TransitStopsRepository.getTransitStops()` emituje dane lokalne przed odświeżeniem. ViewModel zbiera cały Flow. `getDepartures(stopKey)` dostarcza bieżącą tablicę przystankową.
- `RoutesRepository` służy do uzupełnienia szczegółów bieżącego kursu. Pełne czasy pobrane z rozkładu datowanego mają pierwszeństwo; nie zastępujemy ich trasą dzisiejszą.
- Nowy `TimetableDataSource` udostępnia tylko brakujące operacje `getLines(provider)` i `getTimetable(line, date, stopKey?)`. `RealScheduleRepository` opakowuje je w Flow na dispatcherze IO. Ktor, JSON, Koin, silniki platformowe oraz biblioteki pozostają istniejące.

## Gdańsk

Źródła oficjalne:

- [routes.json](https://ckan.multimediagdansk.pl/dataset/c24aa637-3619-4dc2-a171-a23eec8f2172/resource/22313c56-5acf-41c7-a5fd-dc5dc72b3851/download/routes.json): obiekt indeksowany datą, następnie `routes[]`; pola `routeId`, `agencyId`, `routeShortName`, `routeLongName`.
- [trips.json](https://ckan.multimediagdansk.pl/dataset/c24aa637-3619-4dc2-a171-a23eec8f2172/resource/b15bb11c-7e06-4685-964e-3db7775f912f/download/trips.json): data → `trips[]`; `routeId`, `tripId`, `tripHeadsign`, `directionId`.
- [stoptimesspan.json](https://ckan.multimediagdansk.pl/dataset/c24aa637-3619-4dc2-a171-a23eec8f2172/resource/9c3d6fed-5394-4ef1-b2c6-c8716999149c/download/stoptimesspan.json): `stopTimesSpans[]` z `agencyId`, `startDate`, `endDate`. Nazwę pola i UUID katalogu zweryfikowano ponownie podczas review.
- [Opis zbioru ZTM (PDF)](https://ckan.multimediagdansk.pl/dataset/c24aa637-3619-4dc2-a171-a23eec8f2172/resource/2daba886-922f-497e-a39a-8e9b0a010e28/download/opis-zbioru-otwarte-dane-ztm-w-gdasku.pdf): zasoby planowe oraz estymowane odjazdy.

`GdanskTimetableSource` używa katalogu linii bez dodatkowego pobierania wszystkich przystanków i powiązań tras. Dla wybranej daty wybiera dokładnie ten wpis katalogu, sprawdza zakres danej agencji i wywołuje istniejące `GdanskApiClient.getRouteStopTimes(date, routeId)` z datą ISO `YYYY-MM-DD`. Endpoint klienta to `https://ckan2.multimediagdansk.pl/stopTimes?date=...&routeId=...`. Brak wymaganej daty w katalogu/zakresie daje `UNSUPPORTED_DATE`; nie podstawiamy najnowszego dnia.

Wiersze stop-time zawierają m.in. `tripId`, `variantId`, `stopId`, `stopSequence`, `arrivalTime`, `departureTime`. `tripId` oznacza wzorzec. Instancje kursów rozróżniamy po wariancie i uporządkowanej kolejności odjazdów, zgodnie z podejściem istniejącego `GdanskRouteTimesResolver`. Nierówne liczby wystąpień lub niechronologiczny przebieg są odrzucane zamiast łączenia niepasujących kursów. To konserwatywne zachowanie może pomijać uszkodzone/niekompletne warianty.

Daty bazowe 1899-12-30 i 1899-12-31 są odnoszone do dnia usługi w Europe/Warsaw; pełne znaczniki ISO zachowują datę i offset. Brak obu czasów nie staje się północą. Wiersze `passenger=false`, `nonpassenger=1` i `virtual=1` są pomijane.

Brak autoryzacji i paginacji w wykorzystanych zasobach. Nie potwierdzono liczbowego limitu zapytań publicznych. Błędy HTTP katalogów nie są interpretowane jako puste rozkłady. Katalogi są pobierane przy otwarciu feature'a/ponowieniu; wybrany rozkład jest krótkotrwale zachowany w VM, bez odpytywania całego planu przy każdym odświeżeniu live.

## Gdynia

Istniejący kod (`GdyniaTransitDataSource`, `GdyniaGtfsStore`) używa `http://api.zdiz.gdynia.pl/pt/routes`, `/pt/delays`, `/pt/trips` i `/pt/gtfs.zip`. Nie znaleziono oficjalnego publicznego opisu tego API. Adresy i DTO pochodzą z działającej integracji repozytorium; gwarancje dostawcy, numeryczne limity i pełny horyzont danych pozostają niezweryfikowane.

- Katalog `/pt/routes` zawiera wewnętrzny `routeId` oraz prezentacyjny `routeShortName`. Nie zamieniamy jednego na drugi w zapytaniach.
- `GdyniaTimetableSource` korzysta z opublikowanej generacji istniejącego `GdyniaGtfsStore`. Przy braku/wygaśnięciu snapshotu używa tego samego koordynatora importu i atomowej publikacji. Nie ma drugiego importera ani niezależnego pobierania ZIP.
- Parser czyta `trips.txt` (`route_id`, `service_id`, `trip_id`, `trip_headsign`, `direction_id`), `calendar.txt`, wyjątki `calendar_dates.txt` oraz strumieniowo `stop_times.txt`. Wybiera usługi dla konkretnego dnia tygodnia i stosuje dodania/usunięcia usług. Odczyt sprawdza anulowanie coroutine.
- Brak kalendarzy albo dzień poza potwierdzonym zakresem oznacza `UNSUPPORTED_DATE`. Błąd pobrania przy braku snapshotu pozostaje błędem. Poprawnie potwierdzony dzień bez kursów daje pusty sukces.
- Czasy powyżej 24:00 są zachowane. Przeliczenie korzysta z początku usługi określonego przez południe lokalne minus 12 godzin, zgodnie z [definicją czasu GTFS](https://gtfs.org/documentation/schedule/reference/#field-types). Jest test granicy zmiany czasu.
- Kierunek uwzględnia rzeczywistą sekwencję przystanków, więc krótsze warianty nie są sztucznie odwracane.

Bieżące odjazdy nadal dostarcza istniejący provider. Dopasowanie live wymaga identycznych routeId, tripId i teoretycznego czasu na przystanku. Identyfikatory live Gdyni nie zawsze odpowiadają GTFS; gdy nie da się ich jednoznacznie dopasować, kurs pozostaje planowy. Nie dodajemy fikcyjnej estymacji ani GPS pojazdu.

Android ma już wyjątek cleartext dla tego hosta. Desktop ma istniejący importer dyskowy. iOS nie ma kompletnego fallbacku GTFS ani działającego entry pointu aplikacji; brak danych jest jawny, nie zastępowany mockiem.

## SKM/PKM przez PLK

Korzystamy z istniejących `PlkApiClient`, `KtorPlkApiClient`, `CachingPlkApiClient`, `RailStaticCatalog` i DTO w `model/plk`.

- Plan: `GET /api/v1/schedules`, parametry `dateFrom`, `dateTo`, `stations`, `carriersInclude`, `fullRoute=true`. Zapytanie dotyczy jednego dnia i wybranej stacji.
- Autoryzacja: nagłówek `X-API-Key` z istniejącej konfiguracji `PLK_KEY`. Implementacja nie zapisuje kluczy ani payloadów do logów.
- Odpowiedź: `routes[]`, `scheduleId`, `orderId`, `carrierCode`, `operatingDates`, uporządkowane `stations[]`; czasy przyjazdu/odjazdu są nullable, mają osobne offsety dni. Do planu wybieramy tylko kursy obejmujące żądaną datę, stację i przewoźnika.
- Nie ma potwierdzonego globalnego katalogu linii. Wyszukiwarka prezentuje logiczne pozycje przewoźników SKM/PKM z istniejących kodów konfiguracji. Kierunki powstają z faktycznych tras zwróconych dla stacji; nie odpytujemy wszystkich stacji w celu budowy katalogu.
- Dane bieżące nadal pochodzą z istniejącego `RailTransitDataSource` i endpointów operacji. Nie stosujemy cache planowego do live.
- HTTP 401/429 i inne błędy planu są walidowane przed deserializacją. Dzięki temu odpowiedź błędu z domyślnym pustym DTO nie trafia na godzinę do cache jako pusty rozkład.

[Dokumentacja PLK](https://pdp-api.plk-sa.pl/api-documentation) opisuje zakresy dat, autoryzację, paginację odpowiednich endpointów i limity zależne od dostępu. Nie zweryfikowano uprawnień ani limitu przypisanego do klucza użytkownika i nie wykonywano autoryzowanych zapytań produkcyjnych w testach. Istniejący cache planu ma TTL 1 godzinę, najwyżej 8 odpowiedzi i 20 000 rekordów stacyjnych; nie zmieniono tych limitów.

## Różnice między mockiem a produkcją i zachowanie UI

- Czas każdego przystanku pochodzi z danych; usunięto produkcyjne zależności od stałego `STOP_INTERVAL`.
- Kierunek to rzeczywisty wariant, a nie `stopIds.reversed()`. Data/typ dnia opisują wybór użytkownika; kalendarze dostawcy rozstrzygają faktyczne kursowanie.
- Brak estymacji oznacza plan, nie „na czas”. Kurs bez znanej godziny pokazuje brak czasu. Przyjazd na końcu trasy nie jest odjazdem do wsiadania.
- Dzisiejsza tablica przystanku używa feedu live. Na inny dzień należy wybrać linię. Częściowa awaria katalogu jednego dostawcy nie ukrywa pozostałych.
- VM zachowuje najwyżej jeden plan do 20 000 czasów przystankowych przez 5 minut. Zmiana kierunku/przystanku w tym planie nie wymaga powtórnego pełnego requestu. Polling live czeka 30 s dla miast i 60 s dla kolei po zakończeniu poprzedniego zapytania; zatrzymuje się poza właściwym ekranem.
- Zapis offline przechowuje rzeczywiste kursy, linię i nazwy przystanków. Jest ograniczony do 12 zapisów, 2000 kursów na zapis oraz limitu rozmiaru preferencji. Dane live nie są zapisywane jako plan. Starsze dane demo mają inną przestrzeń kluczy.
- Lokalizacja korzysta z istniejącego `LocationRepository` i `PermissionChecker`; stała lokalizacja platformy nie jest opisywana jako GPS.
- Nie zidentyfikowano gotowego, wspólnego kontraktu komunikatów drogowych. UI pokazuje niedostępność, a nie „brak utrudnień”. Ustawienia alertów pozostają lokalne; dostarczanie powiadomień systemowych wymaga osobnej integracji platformowej i jest jawnie opisane w ekranie.

## Testy i ograniczenia weryfikacji

Testy są deterministyczne: Ktor MockEngine, fikstury ZIP/JSON, fake repozytoria oraz kontrolowany dispatcher. Obejmują daty, wyjątki kalendarza, czasy nocne/DST, błędy HTTP, mapowanie i anulowanie. Nie wymagają internetu ani klucza PLK. Wyniki wykonania, buildów i globalnych kontroli są w [planie implementacji](feature-implementation-plan.md).
