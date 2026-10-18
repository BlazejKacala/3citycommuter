# Trzy przystanki — wdrożona identyfikacja

Wektorowe opracowanie wybranego wariantu 01. Znak przedstawia cyfrę 3 jako trasę z trzema żółtymi przystankami. Źródłem geometrii jest `script/GenerateBranding.java`; wygenerowane pliki są częścią repozytorium i zwykły build nie wymaga uruchamiania generatora.

- `logo.svg`: niebieski znak na przezroczystym tle.
- `logo-dark.svg`: jaśniejszy znak na ciemne tło.
- `launcher.svg`: biała trasa na niebieskim kafelku.

## Zasoby aplikacji

Android korzysta z wektorowych warstw ikony adaptacyjnej i monochromatycznej. Znak mieści się w bezpiecznym kole o średnicy 66 dp na warstwie 108 dp. Warianty bitmapowe mdpi–xxxhdpi również zostały zaktualizowane. Osobna skala ikon klasycznych i desktopowych kompensuje brak przycinania zewnętrznej części warstwy adaptacyjnej.

Ekran startowy używa `Theme.SplashScreen`, jasnych/ciemnych kolorów systemowych oraz znaku na środku. Nazwa aplikacji jest zamieniona na krzywe, więc zachowuje polskie znaki i wygląd niezależnie od fontów urządzenia. Android 12+ wyświetla ją w systemowym polu brandingowym przy dolnej krawędzi; Android 10–11 korzysta z odpowiadającego mu tła warstwowego. Nie dodano dodatkowego ekranu ani opóźnienia uruchomienia.

Wymiar ikony splash screenu to 288 dp; znak mieści się wewnątrz koła 192 dp. Nazwa zajmuje obszar 200 × 80 dp. Podstawa: [dokumentacja ekranu startowego Androida](https://developer.android.com/develop/ui/views/launch/splash-screen).

Desktop korzysta z `composeApp/desktop/src/main/resources/icon.png` jako ikony okna.

## Regeneracja

Uruchom z katalogu głównego repozytorium, używając JDK 21+ i pogrubionego fontu TrueType. Obecne krzywe nazwy wygenerowano z Arial Bold, dostępnego w Windows:

```powershell
& 'C:/Program Files/Android/Android Studio/jbr/bin/java.exe' script/GenerateBranding.java 'C:/Windows/Fonts/arialbd.ttf'
```

Generator zapisuje SVG, Android VectorDrawable, bitmapy wszystkich gęstości i ikonę desktopową. Nie wymaga zewnętrznych bibliotek. Kolory motywu startowego są w `values/branding.xml` i `values-night/branding.xml`; przy zmianie palety należy utrzymać ich zgodność ze stałymi generatora.
