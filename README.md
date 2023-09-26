[<img src='images/de.png' height=10> Deutsche Version](README_DE.md)  
[<img src='images/pl.png' height=10> Wersja polska](README_PL.md)

# Glucose Data Handler
## Features

* receives glucose values from Juggluco
* receives glucose values from xDrip+
* provides several **[widgets](#widgets)** and a floating widget for the phone
* provides optional **[notifications](#notifications)** with different icons for the phone
* provides several **[complications](#complications)** for Wear OS
* provides **[Android Auto](#android-auto)** support
* **[Tasker](#tasker)** integration
* sends glucodata broadcasts to other apps (which supports this broadcast)

## Download
You can download the current version [here](https://github.com/pachi81/GlucoDataHandler/releases).

## Installation

-> [Installation instruction](./INSTALLATION.md)

## Settings

### Wear

<img src='images/settings_wear.png' width=200>

* Vibration: the watch vibrate if the target range is left and repeat it as long as the target range is left
* Colored AOD: some watchfaces only support colored complications for the always on display, if there is no monochrom one, then you have to activate this feature
* Big trend-arrow: for watches like Samsung Galaxy Watch 5 Pro until Wear OS 3, the trend arrow is rendered too big, so you can disable this setting to have a smaller trend-arrow
* Foreground: recommended to prevent Wear OS from closing this app (also try deactivating Play Protect as this kills non Playstore apps)
* Relative time: shows the relative time in minutes since the last value was received instead of the timestamp of the last value. This may not work correct on some watches related to Android battery optimizations.
* other settings: all other settings are made in the phone app

### Phone

The settings for the phone are described in the app itself. The settings from the phone will be send to the wear, if it is connected.

<img src='images/settings_phone_1.png' width=300>

## Widgets
There are several types of widgets for the phone:

<img src='images/widgets.jpg' width=200>

There is also a floating widget, which can be controlled by Tasker.

## Notifications
There are two notifciations which can be activated. For each notification the icon can be choosen, which will appear in the status bar of your phone.
The first notification is also be used as foreground notification to prevent Android to close this app in the background. 
So if you have any trouble with this app, I recommend to activate at least the first notification.
The second notification is an empty notification, which you can activate to have an additional icon in the status bar.
<figure>
  <img src='images/notifications.jpg' width=200> 
  <figcaption>Status bar shows the usage of the trend-arrow and the delta value icons next to the glucose value icon from Juggluco.</figcaption>
</figure>

## Complications
There are several complications for the different types of wear OS complications, which provides:
* Glucose value (used also for range circle)

<img src='images/complications_glucose1.png' width=200> <img src='images/complications_glucose2.png' width=200>

* Glucose value as background image (if supported by watch face and it seems to be only available in Wear OS 3)

<img src='images/complications_large_1.png' width=200> <img src='images/complications_large_2.png' width=200>

* Delta value (per minute or per 5 minutes)

<img src='images/complications_delta.png' width=200>

* Rate (trend) as value and arrow (the arrow rotate dynamically between +2.0 (↑) and -2.0 (↓) and shows double arrows from +3.0 (⇈) and from -3.0 (⇊))

<img src='images/complications_rate.png' width=200>

* Battery level from wear and phone (if connected)

<img src='images/complications_battery.png' width=200>

**IMPORTANT:** Not all complications are fully supported by any watchface. For example the SHORT_TEXT type supports an icon, a text and a title, but the most watchfaces only show icon and text or text and title, but there are some, showing all 3 types in one.
Also the RANGE_VALUE complication is handled different in each watchface.

## Android Auto

This app supports Android Auto.

### Option #1: Using dummy media player
If you are not using any Android Auto media player for listen music, you can use GlucoDataHandler to display its values in the media tap:

<img src='images/AA_media.png' width=300>

IMPORTANT: it is recommended to disable all other media apps in the Android Auto launcher, to get this to work.

### Option #2: Using notifications

You can also use notifications:

<img src='images/AA_notification.png' width=300> <img src='images/AA_notification_view.png' width=300>

### Option #3: Using the app

<img src='images/AA_App.png' width=300>

## Tasker

-> [Tasker support](./TASKER.md)

