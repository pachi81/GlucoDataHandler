[<img src='images/en.png' height=10> English version](README.md)  
[<img src='images/pl.png' height=10> Wersja polska](README_PL.md)

# GlucoDataHandler (GDH)

<img src='images/playstore/Playstore_Present_2.png'>

Diese innovative App empfängt Daten von zahlreichen Quellen und visualisiert sie übersichtlich auf deinem Android-Smartphone, deiner Smartwatch (Wear OS, Miband und Amazfit) sowie in deinem Auto (via [GlucoDataAuto](https://github.com/pachi81/GlucoDataAuto/blob/main/README_DE.md)).

## Features

* **Vielfältige [Datenquellen](https://github.com/pachi81/GlucoDataHandler/wiki/Sensors):**
    * **Cloud-Dienste:**
        * Empfängt Glukose Werte als **[LibreLinkUp Follower](https://github.com/pachi81/GlucoDataHandler/wiki/LibreLinkUp)**
        * Empfängt Glukose Werte als **[Dexcom Share Follower](https://github.com/pachi81/GlucoDataHandler/wiki/Dexcom-Share)**
        * Empfängt Glukose Werte als **[Medtrum Follower](https://github.com/pachi81/GlucoDataHandler/wiki/EasyFollow)**
        * Empfängt Glukose, IOB und COB Werte von **Nightscout** (Pebble Schnittstelle)
    * **Lokale Apps:**
        * Empfängt Glukose, IOB und COB Werte von **[AndroidAPS](https://github.com/pachi81/GlucoDataHandler/wiki/Android-APS-(AAPS))**
        * Empfängt Glukose Werte von **[Juggluco](https://github.com/pachi81/GlucoDataHandler/wiki/Juggluco)**
        * Empfängt Glukose Werte von **[xDrip+](https://github.com/pachi81/GlucoDataHandler/wiki/xDrip)**
        * Empfängt Glukose Werte von **Eversense** (mittels **[ESEL](https://github.com/pachi81/GlucoDataHandler/wiki/Esel)**)
        * Empfängt Glukose Werte von **[Dexcom BYODA](https://github.com/pachi81/GlucoDataHandler/wiki/Dexcom-BYODA)** (bisher nicht getestet!)
    * **[Benachrichtigungen](https://github.com/pachi81/GlucoDataHandler/wiki/Notification-Reader) (Beta!):** Empfängt Werte von Cam APS FX, Dexcom G6/G7, Eversense und potenziell vielen weiteren Apps (kontaktiere mich einfach!).

* **Umfassende Visualisierung:**
    * Stellt mehrere **Widgets** für Android zur Verfügung.
    * Optionale **Benachrichtigungen**, um weitere Statusbar-Icons zur Verfügung zu haben.
    * Optionale Anzeige als Hintergrundbild auf dem Sperrbildschirm.
    * Unterstützung für Always On Display (AOD).

* **Individuelle Alarme:**
    * Unterstützung von **Alarmen**:
        * Alarmtypen: sehr tiefe, tiefe, hohe, sehr hohe und veraltete Werte.
        * Individuelle Ton und Vibrationseinstellungen für jeden Alarmtyp.
        * Vollbildbenachrichtigung auf dem Sperrbildschirm.

* **Wear OS Integration:**
    * Stellt mehrere **Complications** für Wear OS zur Verfügung.
    * Erhalte Alarme direkt auf deiner Uhr.
    * **WICHTIGER HINWEIS:** GDH ist keine Standalone-Wear OS App. Für die Einrichtung ist die Telefon-App erforderlich.

* **WatchDrip+ Unterstützung:** Nutze GDH mit bestimmten Mi Band-, Xiaomi Smart Band und Amazfit-Uhren.
* **Garmin und Fitbit** Unterstützung (interner Webserver im xDrip+ Format)
* **Health Connect** Unterstützung

* **Barrierefreiheit:** Volle TalkBack-Unterstützung (Dank an Alex für die Tests!).

* **Android Auto:** Unterstützt **Android Auto** über die [GlucoDataAuto App](https://github.com/pachi81/GlucoDataAuto/blob/main/README_DE.md).

* **[Tasker Integration](./TASKER.md)**

* **Datenweiterleitung:** Sendet Glucodata Broadcasts an andere Apps (die dies unterstützen).

## Download
[<img src='https://play.google.com/intl/en_us/badges/static/images/badges/de_badge_web_generic.png' height=100>](https://play.google.com/store/apps/details?id=de.michelinside.glucodatahandler) 
[<img src='https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png' height=100>](https://apt.izzysoft.de/fdroid/index/apk/de.michelinside.glucodatahandler) 

Die neuste Version kann auch manuell heruntergeladen und installiert werden. [Hier](./INSTALLATION_DE.md) gibt es mehr Informationen.

## Screenshots

### Phone
<img src='images/playstore/de/phone_main.png' width=200>  <img src='images/playstore/de/phone_alarm_fullscreen_notification.png' width=200>  <img src='images/playstore/de/phone_ps_2.png' width=200>  <img src='images/playstore/phone_widgets.png' width=200>

### Watch
<img src='images/playstore/gdh_wear.png' width=200>  <img src='images/playstore/gdh_wear_graph.png' width=200>  <img src='images/playstore/de/gdh_wear_notification.png' width=200>  <img src='images/playstore/Complications1x1.png' width=200> 

### Tablet
<img src='images/playstore/de/tablet_10.png' width=800>

## Ziffernblätter

GlucoDataHandler bietet lediglich Wear OS Complications zur Verwendung in anderen Zifferblättern an.

Es gibt jedoch zwei Benutzer, die speziell Zifferblätter für die Complications von GlucoDataHandler erstellen:

- [Ziffernblätter](https://sites.google.com/view/diabeticmaskedman) bereitgestellt von @[sderaps](https://github.com/sderaps)
- [Ziffernblätter](https://play.google.com/store/apps/dev?id=7197840107055554214) bereitgestellt von Graham

# Mitwirkende Entwickler
@[RobertoW-UK](https://github.com/RobertoW-UK): AOD, Batterie-Widget, Webserver

@[rgodha24](https://github.com/rgodha24): Benachrichtigungsleser

@[rileyg98](https://github.com/rileyg98): AiDEX broadcast support

# Danksagung
@[lostboy86](https://github.com/lostboy86) fürs Testen, Motivieren und dein Feeback

@[froter82](https://github.com/froster82) für die polnische Übersetzung, fürs Testen und dein Feeback

@[nevergiveup](https://github.com/nevergiveup) fürs Testen, Motivieren und dein Feeback

# Unterstützt meine Arbeit
[<img src='https://www.paypalobjects.com/webstatic/de_DE/i/de-pp-logo-100px.png'>](https://paypal.me/pachi81) (bevorzugt als Freund - ohne Gebühren)

[🍺 Buy me a beer](https://www.buymeacoffee.com/pachi81) (hohe Gebühren)
