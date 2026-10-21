# Rozkłady — projekt i implementacja demonstracyjna

## Ekrany

- **Rozkład jazdy**: wyszukiwarka linii i przystanków, filtry Gdańsk/Gdynia/SKM oraz skróty do ulubionych, okolicy, zapisów offline, komunikatów i historii. Dzwonek otwiera alerty.
- **Rozkład linii lub przystanku**: kierunek, wybór przystanku/linii, dzisiaj/jutro/kalendarz, automatyczny typ dnia, odjazdy i wyróżnienie najbliższego kursu. Przystanek końcowy pokazuje stan pusty z podpowiedzią zmiany kierunku.
- **Szczegóły kursu**: przystanek wejścia, status, godziny planowe i przewidywane, pełna lista przystanków, komunikaty i konfiguracja przypomnienia.
- **Ulubione / historia / okolica / offline / alerty**: oddzielne widoki kolekcji. Zapis offline obejmuje konkretną linię, kierunek i dzień. Alert można zmienić lub usunąć.
- **Komunikaty**: przykładowe odwołania, objazdy i opóźnienia z filtrem przewoźnika.

Projekt korzysta z Material 3 i istniejącej palety niebieskiej oraz żółtej. Kontenery, dialogi i wybrane filtry otrzymują kolory wyprowadzone z motywu aplikacji. Treść ma ograniczoną szerokość na desktopie; długie listy przewijają się, a nagłówek i nawigacja pozostają dostępne.

## Podział kodu

- `ScheduleNavGraph`: właściwa nawigacja aplikacji i ViewModel współdzielony w obrębie zakładki.
- `ScheduleScreenViewModel`: stan i obsługa akcji; dane zwraca zamockowany `DemoScheduleDataSource`.
- `ScheduleUiState`, `ScheduleAction`, `model/`: modele widoku, akcji, kursów i kalendarza.
- `component/`: wspólne elementy Compose, selektor daty, konfiguracja alertu i motyw zakładki.
- Osobne pliki ekranów: wyszukiwanie, rozkład, kurs, kolekcje i komunikaty. Composable otrzymują stan i callbacki.
- `LocalSchedulePreferencesStore`: lokalne ustawienia i zapisane rozkłady, pod osobną przestrzenią kluczy `demo.schedule.*`. Dane są dzielone na fragmenty ze względu na limit wartości JVM Preferences. Publikacja nowego zestawu następuje po zapisaniu wszystkich fragmentów.

## Zakres demonstracji

Trasy, numery przystanków, godziny, odległości GPS, opóźnienia, odwołania i komunikaty są przykładowe. Zakładka nie odpytuje sieci ani rzeczywistego GPS. Licznik czasu odjazdu aktualizuje się co 30 sekund według czasu Warszawy. Gdynia pokazuje godziny planowe; przyszłe daty i zapisy offline nie pokazują danych live.

Ulubione, historia (10 pozycji), ustawienia alertów (20) i rozkłady offline (12) są zapisywane lokalnie i odtwarzane po restarcie. Snapshot zachowuje wybrany dzień i kierunek; starsze zapisy mają oznaczenie archiwalne. Najstarsze pozycje ponad limit są zastępowane. Powiadomienia systemowe nie są wysyłane — interfejs wyraźnie wskazuje tryb demonstracyjny. Nawigacja utrzymuje wybór w czasie życia ViewModelu; nie odtwarza otwartego kursu po zakończeniu procesu przez system.

Kalendarz rozróżnia dni robocze, soboty, niedziele i polskie święta, w tym ruchome. Liczba kursów zależy od typu dnia. To reguły demonstracji, nie oficjalny rozkład przewoźnika.

## Uruchomienie i weryfikacja

```powershell
.\gradlew.bat :composeApp:android:assembleDebug
.\gradlew.bat :shared:ui:jvmTest --tests '*ScheduleScreenViewModelTest'
.\gradlew.bat :composeApp:desktop:runSchedulePreview
```

Podgląd używa rzeczywistego grafu nawigacji zakładki, pamięciowego magazynu ustawień i przełącznika jasnego/ciemnego motywu. Nie wymaga uruchomienia bazy Room ani dostawców sieciowych; nie zmienia produkcyjnego `Main.kt`.

Material DatePicker wymaga nowego API czasu, a istniejący kod dostawców używa API 0.6. Aplikacje Android i desktop korzystają w runtime z [oficjalnego wariantu zgodności kotlinx-datetime](https://github.com/Kotlin/kotlinx-datetime#deprecation-of-instant). Kompilacja istniejących modułów zachowuje dotychczasowe API.
