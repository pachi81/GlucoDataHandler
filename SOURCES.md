[<img src='images/de.png' height=10> Deutsche Version](SOURCES_DE.md)  
[<img src='images/pl.png' height=10> Wersja polska](SOURCES_PL.md)

# Sources <!-- omit in toc -->
- [LibreLinkUp](#librelinkup)
- [Dexcom Share](#dexcom-share)
- [Juggluco](#juggluco)
- [xDrip+](#xdrip)
- [AndroidAPS](#androidaps)
- [Eversense](#eversense)
- [Dexcom BYODA](#dexcom-byoda)


# LibreLinkUp

[Video Tutorial](https://youtu.be/YRs9nY4DgnA?si=v108UhnBNfQSeK2I)

To set up LibreLink as follower, you need the account data from LibreLinkUp.
If you have not set up your LibreLinkUp account yet, follow this [instruction](https://librelinkup.com/articles/getting-started).

Here is a quick summary of the steps to do:
* open your FreeStyle Libre App and select in the menu `Share` or `Connected Apps`
* activate LibreLinkUp connection
* install LibreLinkUp from [PlayStore](https://play.google.com/store/apps/details?id=org.nativescript.LibreLinkUp)
* setup your accout and wait for the invitation
* after accept the invitation you do not need the LibreLinkUp App "anymore"
* now you can add your LibreLinkUp account to GlucoDataHandler and activate this source
    
# Dexcom Share

To receive data from Dexcom Share:

In the **Dexcom App**:
- Create a follower
- Under \"Connections\" ensure \"Share On\" is displayed

In **GlucoDataHandler**:
- Enter your Dexcom Clarity username and password (if you use your phone number as your username, include your country code)
- Check the setting for a US account

**Important**: It does not work with a follower account!

# Juggluco

[Video Tutorial](https://youtu.be/evS5rXDiciY?si=DTxlGYhYm-nNboHl)

If you are using Juggluco to receive glucose values, open Juggluco and enable `Glucodata broadcast` and select `de.michelinside.glucodatahandler` (or `de.michelinside.glucodataauto` for [GlucoDataAuto](https://github.com/pachi81/GlucoDataAuto/blob/main/README.md)) in Settings. Save and Ok.

<img src='images/broadcast.png' width=700>

# xDrip+
If you are using xDrip+ to receive glucose values, open xDrip+, go to setting and select Inter-app settings

* Enable "Broadcast Service API"

<img src='images/xdrip_broadcast_api.jpg' height=340>

## Alternative: Broadcast locally <!-- omit in toc -->
Go to setting and select Inter-app settings
* Enable "Broadcast locally"
* Enable "Compatible Broadcast"
* Check "Identify receiver" to be empty or if an entry already exists, then add `de.michelinside.glucodatahandler` (or `de.michelinside.glucodataauto` for [GlucoDataAuto](https://github.com/pachi81/GlucoDataAuto/blob/main/README.md)) separated by a space
* set \"Noise Blocking\" to \"Send even Extremely noisy signals\"
  
<img src='images/xDrip_InterAppSettings.png' height=340> <img src='images/xDrip+_noise_blocking.jpg' height=340>

# AndroidAPS
To receive values from AAPS:
* open AAPS app
* go to "Config Builder"
* enable "Samsung Tizen" or "Data Broadcaster"

<img src='images/AAPS_config.jpg' width=340>

# Eversense
To receive values from Eversense, you have to use [ESEL](https://github.com/BernhardRo/Esel) either in companion mode (reading notification) or connected to the patched Eversense app.

# Dexcom BYODA
To receive values from Dexcom BYODA you have to enable broadcast to xDrip+, AAPS or both while building the app.
