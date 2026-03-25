[<img src='images/en.png' height=10> English version](README.md)  
[<img src='images/de.png' height=10> Deutsche Version](README_DE.md)

# GlucoDataHandler (GDH)

<img src='images/playstore/Playstore_Present_2.png'>

Ta innowacyjna aplikacja odbiera dane z różnych źródeł i wizualizuje je w przejrzysty sposób na smartfonie z Androidem, smartwatchu (Wear OS, Miband i Amazfit) oraz w samochodzie (za pośrednictwem [GlucoDataAuto](https://github.com/pachi81/GlucoDataAuto/blob/main/README_PL.md)).

## Funkcje

* **Różne [źródła](https://github.com/pachi81/GlucoDataHandler/wiki/Sensors) danych:**
  
  * **Usługi chmurowe:**
    * Odbiera wartości glukozy jako **[LibreLinkUp follower](https://github.com/pachi81/GlucoDataHandler/wiki/LibreLinkUp)**
    * Odbiera wartości glukozy jako **[Dexcom Share follower](https://github.com/pachi81/GlucoDataHandler/wiki/Dexcom-Share)**
    * Odbiera wartości glukozy jako **[Medtrum follower](https://github.com/pachi81/GlucoDataHandler/wiki/EasyFollow)**
    * Odbiera wartości glukozy, IOB i COB z **Nightscout** (interfejs pebble).
  * **Aplikacje lokalne:**
    * Odbiera wartości glukozy, IOB i COB z **[AndroidAPS](https://github.com/pachi81/GlucoDataHandler/wiki/Android-APS-(AAPS))**.
    * Odbiera wartości glukozy z **[Juggluco](https://github.com/pachi81/GlucoDataHandler/wiki/Juggluco)**
    * Odbiera wartości glukozy z **[xDrip+](https://github.com/pachi81/GlucoDataHandler/wiki/xDrip)**
    * Odbiera wartości glukozy z **Eversense** (przy użyciu **[ESEL](https://github.com/pachi81/GlucoDataHandler/wiki/Esel)**).
    * Odbiera wartości glukozy z **[Dexcom BYODA](https://github.com/pachi81/GlucoDataHandler/wiki/Dexcom-BYODA)** (funkcja jeszcze nie została przetestowana!)
  * **[Powiadomienia](https://github.com/pachi81/GlucoDataHandler/wiki/Notification-Reader) (Beta!):** Odbiera wartości z aplikacji Cam APS FX, Dexcom G6/G7, Eversense i potencjalnie wielu innych.

* **Kompleksowa wizualizacja:**
  
  * Udostępnia kilka **widgetów** i pływający widget na telefonie.
  * Udostępnia opcjonalne **powiadomienia** z różnymi ikonami na telefonie.
  * Opcjonalnie może wyświetlać dane w formie tapety ekranu blokady.
  * Obsługuje funkcję wyświetlania danych na ekranie Always On Display (AOD).

* **Alarmy, które można samodzielnie ustawiać:**
  
  * Obsługa **alarmów**:
    * Alarm dla bardzo niskich, niskich, wysokich, bardzo wysokich i nieaktualnych wartości glukozy.
    * Indywidualne ustawienia dźwięku dla każdego typu alarmu.
    * Alarm pełnoekranowy na ekranie blokady.

* **Integracja z Wear OS:**
  
  * Zapewnia kilka **komplikacji** dla Wear OS.
  * Uruchamia alarmy bezpośrednio na zegarku.
  * **WAŻNA UWAGA:** GDH na Wear OS nie może działać samodzielnie. Do konfiguracji wymagana jest aplikacja na telefonie.

* **Obsługa WatchDrip+:** Używaj GDH z określonymi urządzeniami Mi Band, Xiaomi Smart Band i Amazfit
* **Zegarki Garmin i Fitbit**
* **Health Connect**

* **Ułatwienia dostępu:** Pełna obsługa funkcji TalkBack (podziękowania dla Alexa za testy!).

* **Android Auto:** Obsługa **Android Auto** przy użyciu aplikacji [GlucoDataAuto app](https://github.com/pachi81/GlucoDataAuto/blob/main/README_PL.md)

* **Integracja z aplikacją Tasker:** Integracja z aplikacją **[Tasker](./TASKER.md)**

* **Przesyłanie danych do innych aplikacji:** Wysyła transmisje danych o glukozie do innych aplikacji (które obsługują tę transmisję).

## Pobierz

[<img src='https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png' height=100>](https://play.google.com/store/apps/details?id=de.michelinside.glucodatahandler) [<img src='https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png' height=100>](https://apt.izzysoft.de/fdroid/index/apk/de.michelinside.glucodatahandler)

Aplikację można również pobrać i zainstalować ręcznie. [Tutaj](./INSTALLATION_PL.md) można przeczytać więcej informacji.

## Zrzuty ekranu

### Telefon

<img src='images/playstore/phone_main.png' width=200>  <img src='images/playstore/phone_alarm_fullscreen_notification.png' width=200>  <img src='images/playstore/phone_ps_2.png' width=200>  <img src='images/playstore/phone_widgets.png' width=200>

### Zegarek

<img src='images/playstore/gdh_wear.png' width=200>  <img src='images/playstore/gdh_wear_graph.png' width=200>  <img src='images/playstore/gdh_wear_notification.png' width=200>  <img src='images/playstore/Complications1x1.png' width=200>

### Tablet

<img src='images/playstore/tablet_10.png' width=800>

## Tarcze zegarków

GlucoDataHandler udostępnia tylko komplikacje do wykorzystania w innych tarczach zegarka z WearOS.

Dwóch użytkowników tworzy jednak tarcze zegarka specjalnie dla komplikacji GlucoDataHandler:

- [tarcze](https://sites.google.com/view/diabeticmaskedman), które tworzy @[sderaps](https://github.com/sderaps)
- [tarcze](https://play.google.com/store/apps/dev?id=7197840107055554214), które tworzy Graham

# Deweloperzy wnoszący swój wkład

@[RobertoW-UK](https://github.com/RobertoW-UK): AOD, widget baterii

@[rgodha24](https://github.com/rgodha24): Czytnik powiadomień

@[rileyg98](https://github.com/rileyg98): AiDEX broadcast support

# Szczególne podziękowania

@[lostboy86](https://github.com/lostboy86) za testy, motywację i informacje zwrotne

@[froter82](https://github.com/froster82) za tłumaczenie na język polski, testy i informacje zwrotne

@[nevergiveup](https://github.com/nevergiveup) za testy, motywację i informacje zwrotne

# Wesprzyj moją pracę

[<img src='https://www.paypalobjects.com/webstatic/de_DE/i/de-pp-logo-100px.png'>](https://paypal.me/pachi81)

[🍺 Postaw mi piwo](https://www.buymeacoffee.com/pachi81)
