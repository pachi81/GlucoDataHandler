[<img src='images/en.png' height=10> English version](README.md)  
[<img src='images/pl.png' height=10> Wersja polska](README_PL.md)

# Glucose Data Handler
## Features

* empfängt Glukose Werte als **[LibreLink Follower](./SOURCES_DE.md#librelink)**
* empfängt Glukose, IOB und COB Werte von Nightscout (Pebble Schnittstelle)
* empfängt Glukose, IOB und COB Werte von **[AndroidAPS](./SOURCES_DE.md#androidaps)**
* empfängt Glukose Werte und IOB von **[Juggluco](./SOURCES_DE.md#juggluco)**
* empfängt Glukose Werte von **[xDrip+](./SOURCES_DE.md#xdrip)**
* stellt mehrere **[Widgets](#widgets)** für Android zur Verfügung
* optionale **[Benachrichtigungen](#benachrichtigungen)** um weitere Statusbar-Icons zur Verfügung zu haben
* stellt mehrere **[Complications](#complications)** für Wear OS zur Verfügung
* unterstützt **[Android Auto](https://github.com/pachi81/GlucoDataAuto/blob/main/README_DE.md)** über die [GlucoDataAuto App](https://github.com/pachi81/GlucoDataAuto/releases)
* **[Tasker Ereignisse](#tasker)** Integration
* sendet Glucodata Broadcasts an andere Apps (die dies unterstützen)

## Download
[<img src='https://play.google.com/intl/en_us/badges/static/images/badges/de_badge_web_generic.png' height=100>](https://play.google.com/store/apps/details?id=de.michelinside.glucodatahandler) 

Die neuste Version kann [hier](https://github.com/pachi81/GlucoDataHandler/releases) heruntergeladen werden.

## Installation

-> [Installationsanleitung](./INSTALLATION_DE.md)

## Quellen

-> [Konfiguration der Quellen](./SOURCES_DE.md)

## Einstellungen

### Smartwatch

* Vibrieren: die Uhr vibriert, wenn der Zielbereich verlassen wird in regelmäßigen Intervallen
* Farbiges AOD: Manche Watchfaces zeigen im Ambient-Mode (AOD) auch farbige Complications (Bilder) an, aber nur, wenn kein monochromes Bild vorhanden ist 
-> dies kann man mit dieser Einstellung erzwingen, dann wird kein monochromes Bild für den Ambient Mode zur Verfügung gestellt. Wenn dann im Ambient-Mode nichts zu sehen ist, muss man die Einstellung rückgängig machen
* Großer Trendpfeil: auf manchen Uhren, wie die Samsung Galaxy Watch 5 Pro mit Wear OS 3, wird der farbige Trendpfeil zu groß erzeugt. Damit man einen normalen Trendpfeil sieht, muss man auf diesen Uhren diese Einstellung deaktivieren
* Vordergrund: um zu verhindern, dass Wear OS die App beendet, empfehle ich diese Einstellung zu aktivieren
* Relative Zeit: zeigt die Zeit in Minuten seit dem der letzte Wert empfangen wurde, anstatt eines festen Zeitstempels. Je nach Uhr kann es sein, dass das nicht korrekt funktioniert aufgrund der jeweiligen Android Batterie Optimierungen auf die ich keinen Einfluss habe
* alle anderen Einstellungen werden über die Smartphone App vorgenommen

### Smartphone

Die einzelnen Einstellungen sind in der App entsprechend beschrieben. Sobald die Smartwatch verbunden ist, werden die Einstellungen übermittelt.

## Widgets
Es gibt verschiedene Arten von Widgets und ein schwebendes Widget:

<img src='images/widgets.jpg' width=200>  <img src='images/de/FloatingWidget.jpg' width=200>

## Benachrichtigungen
Es gibt 2 Benachrichtigungen um zusätzliche Icons in der Statusbar zur Verfügung zu haben.
Die erste Benachrichtig is außerdem eine Vordergrundbenachrichtigung, die verhindert, dass Android die App im Hintergrund beenden kann.
Die zweite Benachrichtigung ist immer leer und wird nur für ein weiteres Statusbar Icon verwendet.

<figure>
  <img src='images/notifications.jpg' width=200> 
  <figcaption>Statusbar mit Trendpfeil und Deltawert neben dem Glucose Wert von Juggluco.</figcaption>
</figure>

## Complications
Die Wear OS version stellt mehrere Complications zur Verfügung:
* Glukose Werte:

<img src='images/complications_glucose1.png' width=200> <img src='images/complications_glucose2.png' width=200>

* Glukose Wert als Hintergrund (wenn vom Watchface unterstützt und anscheinend nur unter Wear OS 3 verfügbar):

<img src='images/complications_large_1.png' width=200> <img src='images/complications_large_2.png' width=200>

* Delta Werte (pro Minute, bzw. pro 5 Minuten, wenn entsprechend aktiviert):

<img src='images/complications_delta.png' width=200>

* Trend als Wert und Pfeil (der Pfeil rotiert dynamisch zwischen +2.0 (↑) und -2.0 (↓) und zeigt Doppelpfeile ab +3.0 (⇈) und ab -3.0 (⇊))

<img src='images/complications_rate.png' width=200>

* Akku der Smartwatch und des Smartphones (wenn dieses verbunden ist)

<img src='images/complications_battery.png' width=200>

**WICHTIG:** Nicht alle Watchfaces zeigen die Complications gleich an, darauf habe ich keinen Einfluss, außer man verwendet die Bilder (farbigen Complications).

## Tasker

-> [Tasker Integration](./TASKER.md)

# Danksagung
@[lostboy86](https://github.com/lostboy86) fürs Testen, Motivieren und dein Feeback

@[froter82](https://github.com/froster82) für die polnische Übersetzung, fürs Testen und dein Feeback

@[nevergiveup](https://github.com/nevergiveup) fürs Testen, Motivieren und dein Feeback

# Unterstützt meine Arbeit
[🍺 Buy me a beer](https://www.buymeacoffee.com/pachi81)

[Paypal me](https://paypal.me/pachi81)
