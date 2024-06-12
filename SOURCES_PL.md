[<img src='images/en.png' height=10> English version](SOURCES.md)  
[<img src='images/de.png' height=10> Deutsche Version](SOURCES_DE.md)

# LibreLink

Aby skonfigurować LibreLink jako „follower”, potrzebne są dane konta z LibreLinkUp.
Jeśli nie skonfigurowałeś jeszcze swojego konta LibreLinkUp, postępuj zgodnie z tą [instrukcją](https://librelinkup.com/articles/getting-started).

Oto krótkie podsumowanie kroków, które należy wykonać:
* otwórz aplikację FreeStyle Libre i wybierz w menu `Udostępnianie` lub `Podłączone aplikacje`.
* aktywuj połączenie LibreLinkUp
* zainstaluj LibreLinkUp ze [Sklepu Play](https://play.google.com/store/apps/details?id=org.nativescript.LibreLinkUp)
* skonfiguruj swoje konto i czekaj na zaproszenie
* po zaakceptowaniu zaproszenia nie potrzebujesz już aplikacji LibreLinkUp.
* teraz możesz dodać swoje konto LibreLinkUp do GlucoDataHandler i aktywować to źródło.

# Juggluco

Jeśli używasz Juggluco do odbierania wartości glukozy, otwórz Juggluco i włącz w Ustawieniach opcję `Glucodata broadcast` i wybierz `de.michelinside.glucodatahandler`. Zapisz i OK.

<img src='images/broadcast.png' width=700>

# xDrip+

Jeśli używasz xDrip+ do odbierania wartości glukozy, otwórz xDrip+, przejdź do ustawień i wybierz Ustawienia innych aplikacji

* włącz "Nadawaj lokalnie"
* ustaw "Blokowanie szumów" na "Send even Extremely noisy signals"
* włącz "Kompatybilny Broadcast"
* sprawdź, czy pole "Identyfikuj odbiornik" jest puste, a jeśli jest już tam jakiś wpis, dodaj za nim spację a następnie wpisz `de.michelinside.glucodatahandler`

<img src='images/xDrip_InterAppSettings.png' height=340> <img src='images/xDrip+_noise_blocking.jpg' height=340>

# AndroidAPS

Aby odbierać wartości z AAPS:
* otwórz AAPS
* przejdź do "Konfiguracja"
* włącz "Samsung Tizen" lub "Data Broadcaster"

<img src='images/pl/AAPS_config.jpg' width=340>
