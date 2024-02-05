[<img src='images/de.png' height=10> Deutsche Version](INSTALLATION_DE.md)  
[<img src='images/pl.png' height=10> Wersja polska](INSTALLATION_PL.md)

# Install

## Prerequisite
Juggluco or xDrip+ is installed on your phone or wear and connected to your sensor.

## 1 - Install GlucoDataHandler on your phone

On your phone go the the [Releases](https://github.com/pachi81/GlucoDataHandler/releases) page and select the last (top) pre-release or release.  
Download and install `GlucoDataHandler.apk` (allow install from unknown source if required).

<img src='images/download.png' width=350>

## 2 - Install GlucoDataHandler on your watch

Now download `GlucoDataHandler-Wear.apk` on your phone but **do not install it**.

<img src='images/download.png' width=350>

### Method #1 - Install with Wear Installer 2

Follow the instructions in [this video](https://www.youtube.com/watch?v=ejrmH-JEeE0) to install `GlucoDataHandler-Wear.apk` on your watch.

### Method #2 - Install with Wear Installer

Follow the instructions in [this video](https://www.youtube.com/watch?v=8HsfWPTFGQI) to install `GlucoDataHandler-Wear.apk` on your watch.

### Method #3 - Install with Android Debug

Download `GlucoDataHandler-Wear.apk` on your computer,install ADB, enable developer mode on the watch (see the videos above), once connected type this command in ADB, from the folder where you downloaded the wear extension file.

```
adb install -r -g GlucoDataHandler-Wear.apk
```

### Wear OS 4

With Wear OS 4, you have to pair you phone/computer first with your watch. In Wear Installer 2 there is an [introduction video](https://youtu.be/abgN4jQqHb0) for. 

For computer, the handling is similar:

* activate ADB Debugging on your watch
* activate Wireless Debugging on your watch
* press Pair new device on your watch
* on computer type: 
```
adb pair [ip]:[port] [pairing-code]
```

The pairing has to be done only once for each device. After a device is paired, you only have to connect with ip AND(!) port (not from pairing) to the device the next time:
```
adb connect [ip]:[port]
```

### More information

See [here](https://forum.xda-developers.com/t/how-to-install-apps-on-wear-os-all-methods.4510255/) for more information.

### Check installation

You should now have the GlucoDataHandler app on your watch.

<img src='images/watch_apps.png' width=300>

Open the app and make sure "Foreground" is activated (recommended).

<img src='images/watch_gdh_app.png' width=300>

## 3 - Setup a complication

Select a watchface that supports a complication, you will recognize it by the cog below.

<img src='images/complication1.png' width=300>

Select a field.

<img src='images/complication2.png' width=300>

Select the GlucoDataHandler app, then the information type.

<img src='images/complication3.png' width=300>

You can customize other fields with different information.

And you should be set.

<img src='images/complication4.png' width=300>
