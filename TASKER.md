### Tasker Event

The phone application supports [Tasker](https://play.google.com/store/apps/details?id=net.dinglisch.android.taskerm) event for each new received glucose value.

#### Tasker Event variables

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
