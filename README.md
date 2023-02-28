# Glucose Data Handler

<img src='images/watch.png' width=100>

This Android and Wear OS App receives glucodata broadcasts from Juggluco (mobile or/and wear version).
The mobile version provides a tasker event plugin to handle these data in tasker.
The wear version provides some complication, which can be used in every watchface with complication support.

The wear version is a standalone version, you don´t need the mobile version, if you don´t have problems with the communication between the mobile and wear version of Juggluco. If you have any troubles or you can not install Juggluco on your mobile device for any reason, you can use my both apps which are also exchanging the glucodata broadcast.

## Installation

-> [Installation instruction](./INSTALLATION.md)

## Configuration
In Juggluco activate "Glucodata broadcast" in the settings menu.

### Wear

#### Settings

<img src='images/settings_wear.png' width=200>

* Target range: set your target glucose range needed for yellow color of the rate image (red color is set for an alarm)
* Vibration: the watch vibrate if the target range is left and repeat it as long as the target range is left
* Foreground: only if you have trouble with updating complications (also try deactivating Play Protect as this kills non Playstore apps)

#### Complications
There are several complications for the different types of wear OS complications, which provides:
* Glucose value (used also for range circle)

<img src='images/complications_glucose1.png' width=200>
<img src='images/complications_glucose2.png' width=200>

* Delta value (per minute)

<img src='images/complications_delta.png' width=200>

* Rate (trend) as value and arrow

<img src='images/complications_rate.png' width=200>

**IMPORTANT:** Not all complications are fully supported by any watchface. For example the SHORT_TEXT type supports an icon, a text and a title, but the most watchfaces only show icon and text or text and title, but there are some, showing all 3 types in one.
Also the RANGE_VALUE complication is handled different in each watchface.

### Tasker

-> [Tasker support](./TASKER.md)

