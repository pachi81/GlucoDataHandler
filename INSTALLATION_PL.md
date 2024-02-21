[<img src='images/en.png' height=10> English version](INSTALLATION.md)  
[<img src='images/de.png' height=10> Deutsche Version](INSTALLATION_DE.md)

# Instalacja

## Warunek wstępny

Aplikacja Juggluco lub xDrip+ jest zainstalowana na telefonie lub zegarku i łączy z sensorem.

## 1 – Zainstaluj GlucoDataHandler na swoim telefonie

W telefonie przejdź do strony [Releases](https://github.com/pachi81/GlucoDataHandler/releases) i wybierz ostatnią (górną) wersję przedpremierową lub wersję stabilną.
Pobierz i zainstaluj `GlucoDataHandler.apk` (w razie potrzeby zezwól na instalację z nieznanego źródła).

<img src='images/download.png' width=350>

## 2 – Zainstaluj GlucoDataHandler na swoim zegarku

Pobierz teraz aplikację `GlucoDataHandler-Wear.apk` na swój telefon, ale nie instaluj jej.

<img src='images/download.png' width=350>

### Metoda nr 1 – Instalacja za pomocą Wear Installer 2

Postępuj zgodnie z instrukcjami [tym filmie](https://www.youtube.com/watch?v=ejrmH-JEeE0), aby zainstalować `GlucoDataHandler-Wear.apk` na swoim zegarku.

### Metoda nr 2 – Instalacja za pomocą Wear Installer

Postępuj zgodnie z instrukcjami [tym filmie](https://www.youtube.com/watch?v=8HsfWPTFGQI), aby zainstalować `GlucoDataHandler-Wear.apk` na swoim zegarku.

### Metoda nr 3 – Instalacja za pomocą Android Debug

Pobierz `GlucoDataHandler-Wear.apk` na swój komputer, zainstaluj ADB, włącz tryb programisty na zegarku (zobacz filmy powyżej), po podłączeniu wpisz poniższe polecenie w ADB, będąc w folderze, do którego pobrałeś plik aplikacji na zegarek.

```
adb install -r GlucoDataHandler-Wear.apk
```

### Więcej informacji

[Tutaj](https://forum.xda-developers.com/t/how-to-install-apps-on-wear-os-all-methods.4510255/) można przeczytać więcej informacji.

### Sprawdź instalację

Teraz na zegarku powinna znajdować się aplikacja GlucoDataHandler.

<img src='images/watch_apps.png' width=300>

Otwórz aplikację i upewnij się, że opcja „Pierwszy plan" jest włączona (zalecane).

<img src='images/watch_gdh_app.png' width=300>

## 3 – Konfiguracja komplikacji

Wybierz tarczę zegarka, która obsługuje komplikacje; rozpoznasz ją po zębatce widocznej poniżej podglądu tarczy.

<img src='images/complication1.png' width=300>

Wybierz pole.

<img src='images/complication2.png' width=300>

Wybierz aplikację GlucoDataHandler, a następnie typ informacji.

<img src='images/complication3.png' width=300>

Inne pola można dostosować za pomocą różnych informacji.

I powinieneś być gotowy.

<img src='images/complication4.png' width=300>
