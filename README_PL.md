[<img src='images/en.png' height=10> English version](README.md)  
[<img src='images/de.png' height=10> Deutsche Version](README_DE.md)

# Glucose Data Handler

## Funkcje

- odbiera wartości glukozy z Juggluco
- odbiera wartości glukozy z xDrip+
- provides several **[widgets](#widgets)** and a floating widget for the phone
- provides optional **[notifications](#notifications)** with different icons for the phone
- zapewnia kilka [komplikacji](#komplikacje) dla Wear OS
- umożliwia połączenie z [Android Auto](#android-auto)
- [zdarzenie w aplikacji Tasker](#aplikacja-tasker) integration
- wysyła transmisje danych o glukozie do innych aplikacji (które obsługują tę transmisję)

## Pobierz

Aktualną wersję można pobrać [tutaj](https://github.com/pachi81/GlucoDataHandler/releases).

## Instalacja

-\> [Instrukcja instalacji](./INSTALLATION_PL.md)

## Ustawienia

### Zegarek

<img src='images/pl/settings_wear.png' width=200>

- Wibracja: zegarek wibruje, jeśli zakres docelowy został przekroczony i powtarza wibracje tak długo, jak długo glukoza pozostaje poza zakresem docelowym
- Colored AOD: some watchfaces only support colored complications for the always on display, if there is no monochrom one, then you have to activate this feature
- Big trend-arrow: for watches like Samsung Galaxy Watch 5 Pro, the trend arrow is rendered too big, so you can disable this setting to have a normal sized trend-arrow
- Pierwszy plan: opcja zalecana, aby zapobiec zamykaniu tej aplikacji przez Wear OS (spróbuj również dezaktywować Play Protect, ponieważ funkcja ta zamyka aplikacje spoza Sklepu Play)
- inne ustawienia: wszystkie inne ustawienia wprowadza się w aplikacji na telefonie

### Telefon

Ustawienia dla aplikacji na telefon opisane są w samej aplikacji. Ustawienia z telefonu zostaną przesłane do zegarka, jeśli jest on podłączony.

<img src='images/pl/settings_phone_1.jpg' width=300>

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

## Komplikacje

Istnieje kilka komplikacji dla różnych typów komplikacji w ramach Wear OS, które mogą wyświetlać:

- Wartość glukozy (używana również do koła zakresu)

<img src='images/complications_glucose1.png' width=200> <img src='images/complications_glucose2.png' width=200>

- Wartość glukozy jako obraz tła (jeśli funkcja ta jest obsługiwana przez tarczę zegarka i zdaje się, że jest dostępna tylko w Wear OS 3)

<img src='images/complications_large_1.png' width=200> <img src='images/complications_large_2.png' width=200>

- Wartość delty (na minutę lub na 5 minut)

<img src='images/complications_delta.png' width=200>

- Tempo (trend) jako wartość i strzałka (strzałka obraca się dynamicznie między +2,0 (↑) a -2,0(↓) i pokazuje podwójne strzałki od +3,0 (⇈) i od -3,0 (⇊))

<img src='images/complications_rate.png' width=200>

- Poziom baterii w zegarku i w telefonie (jeśli jest podłączony)

<img src='images/complications_battery.png' width=200>

WAŻNA UWAGA: Nie wszystkie komplikacje są w pełni obsługiwane przez każdą tarczę zegarka. Na przykład typ KRÓTKI\_TEKST obsługuje ikonę, tekst i tytuł, ale większość tarcz zegarków pokazuje tylko ikonę i tekst lub tekst i tytuł, ale są też takie, które pokazują wszystkie 3 typy w jednym. Również komplikacja WARTOŚĆ\_ZAKRESU jest obsługiwana inaczej na każdej tarczy zegarka.

## Android Auto

Ta aplikacja obsługuje Android Auto.

### Opcja nr 1: Korzystanie z fikcyjnego odtwarzacza multimediów

Jeśli nie używasz żadnego odtwarzacza multimedialnego w Android Auto do słuchania muzyki, możesz użyć aplikacji GlucoDataHandler, aby wyświetlić jego wartości w wiadomościach dotyczących multimediów:

<img src='images/AA_media.png' width=300>

WAŻNE: aby to działało, zaleca się wyłączenie wszystkich innych aplikacji multimedialnych w programie uruchamiającym Android Auto.

### Opcja nr 2: Użycie powiadomień

Można również korzystać z powiadomień:

<img src='images/AA_notification.png' width=300> <img src='images/AA_notification_view.png' width=300>

### Opcja nr 3: Korzystanie z aplikacji

<img src='images/AA_App.png' width=300>

## Aplikacja Tasker

-> [Obsługa aplikacji Tasker](./TASKER.md)
