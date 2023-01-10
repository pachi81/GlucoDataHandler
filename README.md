# Glucose Data Handler

This Android App provides an interface between Juggluco and Tasker.
It converts the Glucodata broadcast from Juggluco to an event for Tasker.
This event can be used for Tasker profiles.

## Installation
Install the last released version and start it once. 
You will be asked to disable battery optimization. Please turn it off.

## Configuration
In Juggluco activate "Glucodata broadcast" in the settings menu.


If all works like expected you can create a new profile in Tasker and select as event the plugin Juggluco Tasker Plugin.
Now you can use the variables for anything you like to do with.

### Tasker Variables

The event contains these variables:


| Tasker Variable | glucodata.Minute Variable | Description                                                                            |
| ----------------- | --------------------------- | ---------------------------------------------------------------------------------------- |
| %alarm          | Alarm                     | Alarm value set by Juggluco (0: no value, 6: high glucose value, 7: low glucose value) |
| %arrow          | -                         | Calculate unicode arrow for the current rate value                                     |
| %delta          | -                         | Delta per minute between the current and the last value (mg/dl or mmol/l)              |
| %dexcomlabel    | -                         | Calculated dexcom specific label for the current rate value                            |
| %glucose        | glucose                   | Glucose value in the unit, defined in Juggluco app (mg/dl or mmol/l)                   |
| %rate           | Rate                      | Rate of change of the glucose value                                                    |
| %ratelabel      | -                         | Calculated label for the current rate value                                            |
| %rawvalue       | mgdl                      | Glucose value in mg/dl                                                                 |
| %sensorid       | SerialNumber              | Serial number of the current used sensor                                               |
| %time           | Time                      | Timestamp in ms since 1.1.1970                                                         |
| %timediff       | -                         | Duration in ms between the current and the previous received value                     |
| %unit           | -                         | Unit of the glucose value, either mg/dl or mmol/l                                      |
