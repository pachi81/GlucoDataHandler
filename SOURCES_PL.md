[<img src='images/en.png' height=10> English version](SOURCES.md)  
[<img src='images/de.png' height=10> Deutsche Version](SOURCES_DE.md)

# Źródła <!-- omit in toc -->
- [LibreLinkUp](#librelinkUp)
- [Dexcom Share](#dexcom-share)
- [Juggluco](#juggluco)
- [xDrip+](#xdrip)
- [AndroidAPS](#androidaps)
- [Eversense](#eversense)
- [Dexcom BYODA](#dexcom-byoda)

# LibreLinkUp

[Przewodnik wideo](https://youtu.be/YRs9nY4DgnA?si=v108UhnBNfQSeK2I)

Aby skonfigurować LibreLink jako „follower”, potrzebne są dane konta z LibreLinkUp.
Jeśli nie skonfigurowałeś jeszcze swojego konta LibreLinkUp, postępuj zgodnie z tą [instrukcją](https://librelinkup.com/articles/getting-started).

Oto krótkie podsumowanie kroków, które należy wykonać:
* otwórz aplikację FreeStyle Libre i wybierz w menu `Udostępnianie` lub `Podłączone aplikacje`.
* aktywuj połączenie LibreLinkUp
* zainstaluj LibreLinkUp ze [Sklepu Play](https://play.google.com/store/apps/details?id=org.nativescript.LibreLinkUp)
* skonfiguruj swoje konto i czekaj na zaproszenie
* po zaakceptowaniu zaproszenia nie potrzebujesz już aplikacji LibreLinkUp.
* teraz możesz dodać swoje konto LibreLinkUp do GlucoDataHandler i aktywować to źródło.
    
# Dexcom Share
Aby odbierać dane z serwerów Dexcom Share, musisz mieć:

W **aplikacji Dexcom**:
- Utwórz obserwatora
- W sekcji "Połączenia" upewnij się, że wyświetlana jest opcja "Udostępniaj".
    
W **GlucoDataHandler**:
- Wprowadź nazwę użytkownika i hasło Dexcom Clarity (jeśli używasz numeru telefonu jako nazwy użytkownika, dołącz kod kraju) 
- Sprawdź ustawienie konta US

**Ważna uwaga**: Opcja ta nie działa z kontem obserwatora!

# Juggluco

[Przewodnik wideo](https://youtu.be/evS5rXDiciY?si=DTxlGYhYm-nNboHl)

Jeśli używasz Juggluco do odbierania wartości glukozy, otwórz Juggluco i włącz w Ustawieniach opcję `Glucodata broadcast` i wybierz `de.michelinside.glucodatahandler` (lub `de.michelinside.glucodataauto` w przypadku [GlucoDataAuto](https://github.com/pachi81/GlucoDataAuto/blob/main/README_PL.md)). Zapisz i OK.

<img src='images/broadcast.png' width=700>

# xDrip+
Jeśli używasz xDrip+ do odbierania wartości glukozy, otwórz xDrip+, przejdź do ustawień i wybierz Ustawienia innych aplikacji

* włącz "Broadcast Service API"

<img src='images/xdrip_broadcast_api.jpg' height=340>

## Alternatywa: nadawanie lokalne xDrip+ <!-- omit in toc -->
Przejdź do ustawień i wybierz Ustawienia innych aplikacji
* włącz "Nadawaj lokalnie"
* ustaw "Blokowanie szumów" na "Send even Extremely noisy signals"
* włącz "Kompatybilny Broadcast"
* sprawdź, czy pole "Identyfikuj odbiornik" jest puste, a jeśli jest już tam jakiś wpis, dodaj za nim spację a następnie wpisz `de.michelinside.glucodatahandler` (lub `de.michelinside.glucodataauto` w przypadku [GlucoDataAuto](https://github.com/pachi81/GlucoDataAuto/blob/main/README_PL.md))

<img src='images/xDrip_InterAppSettings.png' height=340> <img src='images/xDrip+_noise_blocking.jpg' height=340>

# AndroidAPS

Aby odbierać wartości z AAPS:
* otwórz AAPS
* przejdź do "Konfiguracja"
* włącz "Samsung Tizen" lub "Data Broadcaster"

<img src='images/pl/AAPS_config.jpg' width=340>

# Eversense
Aby odbierać wartości z Eversense, musisz używać [ESEL](https://github.com/BernhardRo/Esel) w trybie towarzyszącym (czytanie powiadomień) lub połączyć się z poprawioną (patched) aplikacją Eversense.

# Dexcom BYODA
Aby otrzymywać wartości z Dexcom BYODA, należy włączyć transmisję do xDrip+, AAPS lub obu podczas tworzenia aplikacji.
