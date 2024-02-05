[<img src='images/de.png' height=10> Deutsche Version](SOURCES_DE.md)  
[<img src='images/pl.png' height=10> Wersja polska](SOURCES_PL.md)

# LibreLink

To set up LibreLink as follower, you need the account data from LibreLinkUp.
If you have not set up your LibreLinkUp account yet, follow this [instruction](https://librelinkup.com/articles/getting-started).

Here is a quick summary of the steps to do:
* open your FreeStyle Libre App and select in the menu `Share` or `Connected Apps`
* activate LibreLinkUp connection
* install LibreLinkUp from [PlayStore](https://play.google.com/store/apps/details?id=org.nativescript.LibreLinkUp)
* setup your accout and wait for the invitation
* after accept the invitation you do not need the LibreLinkUp App "anymore"
* now you can add your LibreLinkUp account to GlucoDataHandler and activate this source

# Juggluco
If you are using Juggluco to receive glucose values, open Juggluco and enable `Glucodata broadcast` and select `de.michelinside.glucodatahandler` in Settings. Save and Ok.

<img src='images/broadcast.png' width=700>

# xDrip+
If you are using xDrip+ to receive glucose values, open xDrip+, go to setting and select Inter-app settings
* Enable "Broadcast locally"
* Enable "Compatible Broadcast"
* Check "Identify receiver" to be empty or if there is already an entry, add a new line with `de.michelinside.glucodatahandler`
  
<img src='images/xDrip_InterAppSettings.png' width=340>

# AndroidAPS
To receive values from AAPS:
* open AAPS app
* go to "Config Builder"
* enable "Samsung Tizen"

<img src='images/AAPS_config.jpg' width=340>
