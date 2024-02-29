[<img src='images/en.png' height=10> English version](SOURCES.md)  
[<img src='images/pl.png' height=10> Wersja polska](SOURCES_PL.md)

# LibreLink

Um LibreLink zu verwenden wird ein LibreLinkUp Konto benötigt.
Wenn noch keines erstellt wurde, folgen Sie bitte dieser [Anleitung](https://librelinkup.com/articles/getting-started).

Eine kleiner Zusammenfassung der Schritte, welche zu erledigen sind:
* FreeStyle Libre App öffnen und unter `Verbinden` auf `Teilen` oder `Verbundene Anwendungen` klicken
* hier muss der LibreLinkUp eingerichtet werden
* danach die LibreLinkUp App aus dem [PlayStore](https://play.google.com/store/apps/details?id=org.nativescript.LibreLinkUp) installieren
* in der LibreLinkUp App einloggen und die Einladung annehmen
* danach wird die LibreLinkUp App nicht mehr zwingend benötigt und kann wieder deinstalliert werden
* jetzt können die LibreLinkUp Kontodaten in GlucoDataHandler hinterlegt und die Quelle aktiviert werden

# Juggluco
Für die Benuzer von Juggluco, nach der Installation des GlucoDataHandler in den Juggluco Einstellungen den Punkt `Glucodata broadcast` aktivieren und `de.michelinside.glucodatahandler` auswählen.
Anschließend speichern und mit OK beenden.

<img src='images/broadcast.png' width=700>

# xDrip+
Für die Benutzer von xDrip+, in den Einstellung den Eintrag Inter-App Einstellungen öffnen und wie folgt konfigurieren:
* "Lokaler Broadcast" aktivieren
* "Kompatible Broadcast" aktivieren
* prüfen, ob "Identifiziere Empfänger" leer ist; ist bereits ein Eintrag vorhanden, dann eine neue Zeile mit `de.michelinside.glucodatahandler` hinzufügen
* in Verrauschungsunterdrückung \"Send even Extremely noisy signals\" auswählen
  
<img src='images/de/xDrip_InterAppSettings.png' height=340> <img src='images/de/xDrip+_Verrauschungsunterdrückung.jpg' height=340>

# AndroidAPS
Konfiguration von AAPS:
* AAPS App öffnen
* "Konfiguration" öffnen
* "Samsung Tizen" aktivieren

<img src='images/de/AAPS_config.jpg' width=340>
