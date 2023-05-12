[English version](README.md)

# Glucose Data Handler
## Features

* empfängt Glukose Werte von Juggluco
* empfängt Glukose Werte von xDrip+
* stellt mehrere ****[Complications](#complications)**** für Wear OS zur Verfügung
* unterstützt **[Android Auto](#android-auto)**
* unterstützt **[Tasker Ereignisse](#tasker)** für neue Glukose Werte
* sendet Glucodata Broadcasts an andere Apps (die dies unterstützen)

## Download
Die neuste Version kann [hier](https://github.com/pachi81/GlucoDataHandler/releases) heruntergeladen werden.

## Installation

-> [Installationsanleitung](./INSTALLATION_DE.md)

## Einstellungen

### Smartwatch

<img src='images/settings_wear_DE.png' width=200>

* Vibrieren: die Uhr vibriert, wenn der Zielbereich verlassen wird in regelmäßigen Intervallen
* Vordergrund: um zu verhindern, dass Wear OS die App beendet, empfehle ich diese Einstellung zu aktivieren
* alle anderen Einstellungen werden über die Smartphone App vorgenommen

### Smartphone

Die einzelnen Einstellungen sind in der App entsprechend beschrieben. Sobald die Smartwatch verbunden ist, werden die Einstellungen übermittelt.

<img src='images/settings_phone_1_DE.png' width=300>
<img src='images/settings_phone_2_DE.png' width=300>

#### Complications
Die Wear OS version stellt mehrere Complications zur Verfügung:
* Glukose Werte:

<img src='images/complications_glucose1.png' width=200>
<img src='images/complications_glucose2.png' width=200>

* Glukose Wert als Hintergrund (wenn vom Watchface unterstützt und anscheinend nur unter Wear OS 3 verfügbar):

<img src='images/complications_large_1.png' width=200>
<img src='images/complications_large_2.png' width=200>

* Delta Werte (pro Minute, bzw. pro 5 Minuten, wenn entsprechend aktiviert):

<img src='images/complications_delta.png' width=200>

* Trend als Wert und Pfeil (der Pfeil rotiert dynamisch zwischen +2.0 (↑) und -2.0 (↓) und zeigt Doppelpfeile ab +3.0 (⇈) und ab -3.0 (⇊))

<img src='images/complications_rate.png' width=200>

* Akku der Smartwatch und des Smartphones (wenn dieses verbunden ist)

<img src='images/complications_battery.png' width=200>

**WICHTIG:** Nicht alle Watchfaces zeigen die Complications gleich an, darauf habe ich keinen Einfluss, außer man verwendet die Bilder (farbigen Complications).

### Android Auto

Die App unterstützt Android Auto auf zweit Arten:

#### Option #1: Dummy Media Player
Wenn sie nicht über Android Auto Musik hören, können sie Media Player Unterstützung verwenden:

<img src='images/AA_media.png' width=300>

WICHTIG: da die App selber keine Musik abspielt, sollte man alle anderen Media Player aus dem Launcher entfernen, damit diese App entsprechend angezeigt wird.

#### Option #2: Benachrichtigungen verwenden

Alternativ zum Media Player kann die Android Auto Benachrichtigung verwendet werden:

<img src='images/AA_notification.png' width=300>
<img src='images/AA_notification_view.png' width=300>

INFO: das Benachrichtigungs Popup erscheint bei jedem neuen Wert.

### Tasker

-> [Tasker support](./TASKER.md)

