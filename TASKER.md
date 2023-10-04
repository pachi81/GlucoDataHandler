# Tasker

The phone application supports [Tasker](https://play.google.com/store/apps/details?id=net.dinglisch.android.taskerm):

## New glucose value event
This event is triggered each time, a new glucose value is received.
The event contains these variables:

| Tasker Variable | glucodata.Minute Variable | Description                                                                                                                                                   |
| ----------------- | --------------------------- |---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| %alarm          | Alarm                     | Alarm value set by Juggluco (0: no value, 6/14: high glucose value, 7/15: low glucose value)<br/>Alarm levels 14 and 15 are set, if Juggluco raises an alert. |
| %arrow          | -                         | Calculate unicode arrow for the current rate value                                                                                                            |
| %delta          | -                         | Delta per minute between the current and the last value (mg/dl or mmol/l)                                                                                     |
| %dexcomlabel    | -                         | Calculated dexcom specific label for the current rate value                                                                                                   |
| %glucose        | glucose                   | Glucose value in the unit, defined in Juggluco app (mg/dl or mmol/l)                                                                                          |
| %rate           | Rate                      | Rate of change of the glucose value                                                                                                                           |
| %ratelabel      | -                         | Calculated label for the current rate value                                                                                                                   |
| %rawvalue       | mgdl                      | Glucose value in mg/dl                                                                                                                                        |
| %sensorid       | SerialNumber              | Serial number of the current used sensor                                                                                                                      |
| %time           | Time                      | Timestamp in ms since 1.1.1970                                                                                                                                |
| %timediff       | -                         | Duration in ms between the current and the previous received value                                                                                            |
| %unit           | -                         | Unit of the glucose value, either mg/dl or mmol/l                                                                                                             |

## Obsolete value event
This event is triggered after 5 and 10 minutes, if there is no new value received in this time

This event contains all variables from the [New glucose value event](#new-glucose-value-event) and addtional:
| Tasker Variable | glucodata.Minute Variable | Description                                                                                                                                                   |
| ----------------- | --------------------------- |---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| %obsolete_time    | Time since last value       | Time in minutes (5 or 10) since last value was received.             |

## Glucose alarm event
This event is only triggered, if Juggluco triggers an alarm.
This event contains all variables from the [New glucose value event](#new-glucose-value-event).

## Wear connection state
This state is triggered by wear connection state changes.

## Android Auto connection state
This state is triggered by Android Auto connection state changes.

## Settings action
With this action you can change different settings of the phone and wear application.
