[<img src='images/de.png' height=10> Deutsche Version](README_DE.md)  
[<img src='images/pl.png' height=10> Wersja polska](README_PL.md)

# GlucoDataHandler (GDH)

<img src='images/playstore/Playstore_Present_2.png'>

This innovative app receives data from various sources and visualizes it clearly on your Android smartphone, smartwatch (Wear OS, Miband, and Amazfit), and in your car (via [GlucoDataAuto](https://github.com/pachi81/GlucoDataAuto/blob/main/README.md)).

## Features

* **Diverse Data [Sources](./SOURCES.md):**
    * **Cloud Services:**
        * Receives glucose values as **[LibreLinkUp follower](./SOURCES.md#librelinkup)**
        * Receives glucose values as **[Dexcom Share follower](./SOURCES.md#dexcom-share)**
        * Receives glucose, IOB and COB values from **Nightscout** (pebble interface)
    * **Local Apps:**
        * Receives glucose, IOB and COB values from **[AndroidAPS](./SOURCES.md#androidaps)**
        * Receives glucose values from **[Juggluco](./SOURCES.md#juggluco)**
        * Receives glucose values from **[xDrip+](./SOURCES.md#xdrip)**
        * Receives glucose values from **[Eversense](./SOURCES.md#eversense)** (using **[ESEL](https://github.com/BernhardRo/Esel)**)
        * Receives glucose values from **[Dexcom BYODA](./SOURCES.md#dexcom-byoda)** (not tested, yet!)
    * **Notifications (Beta!):** Receives values from Cam APS FX, Dexcom G6/G7, Eversense, and potentially many more apps

* **Comprehensive Visualization:**
    * Provides several **widgets** and a floating widget for the phone.
    * Provides optional **notifications** with different icons for the phone.
    * Optional display as lock screen wallpaper.
    * Always On Display (AOD) support.

* **Customizable Alarms:**
    * Support for **alarms**:
        * Alarm for very low, low, high, very high and obsolete glucose values.
        * Individual sound settings for each alarm type.
        * Fullscreen alarm on lockscreen.

* **Wear OS Integration:**
    * Provides several **complications** for Wear OS.
    * Receive alarms directly on your watch.
    * **IMPORTANT NOTE:** GDH is not a standalone Wear OS app. The phone app is required for setup.

* **WatchDrip+ Support:** Use GDH with specific Miband and Amazfit devices.

* **Accessibility:** Full TalkBack support (Thanks to Alex for testing!).

* **Android Auto:** **Android Auto** support using [GlucoDataAuto app](https://github.com/pachi81/GlucoDataAuto/blob/main/README.md)

* **Tasker Integration:** **[Tasker](./TASKER.md)** integration

* **Data Forwarding:** Sends glucodata broadcasts to other apps (which supports this broadcast).

## Download
[<img src='https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png' height=100>](https://play.google.com/store/apps/details?id=de.michelinside.glucodatahandler) 
[<img src='https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png' height=100>](https://apt.izzysoft.de/fdroid/index/apk/de.michelinside.glucodatahandler) 

You can also download and install it manually. See [here](./INSTALLATION.md) for more information.

## Screenshots

### Phone
<img src='images/playstore/phone_main.png' width=200>  <img src='images/playstore/phone_alarm_fullscreen_notification.png' width=200>  <img src='images/playstore/phone_ps_2.png' width=200>  <img src='images/playstore/phone_widgets.png' width=200>

### Watch
<img src='images/playstore/gdh_wear.png' width=200>  <img src='images/playstore/gdh_wear_graph.png' width=200>  <img src='images/playstore/gdh_wear_notification.png' width=200>  <img src='images/playstore/Complications1x1.png' width=200> 

### Tablet
<img src='images/playstore/tablet_10.png' width=800>

## Watchfaces

GlucoDataHandler only provide Wear OS complications to be used in other watchfaces.

But there are two users creating watchfaces especially for the complications of GlucoDataHandler:

- [watchfaces](https://sites.google.com/view/diabeticmaskedman) provided by @[sderaps](https://github.com/sderaps)
- [watchfaces](https://play.google.com/store/apps/dev?id=7197840107055554214) provided by Graham

# Contributing Developers
@[RobertoW-UK](https://github.com/RobertoW-UK): AOD, Battery Widget

@[rgodha24](https://github.com/rgodha24): Notification Reader

# Special thanks
@[lostboy86](https://github.com/lostboy86) for testing, motivation and feedback

@[froter82](https://github.com/froster82) for Polish translation, testing and feedback

@[nevergiveup](https://github.com/nevergiveup) for testing, motivation and feedback

# Support my work
[<img src='https://www.paypalobjects.com/webstatic/de_DE/i/de-pp-logo-100px.png'>](https://paypal.me/pachi81)

[🍺 Buy me a beer](https://www.buymeacoffee.com/pachi81)
