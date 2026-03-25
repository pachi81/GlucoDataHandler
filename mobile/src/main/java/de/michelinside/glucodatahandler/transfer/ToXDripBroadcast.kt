package de.michelinside.glucodatahandler.transfer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.utils.Utils

class ToXDripBroadcast : AppBroadcasts() {
    override val LOG_ID = "GDH.transfer.ToXDripBroadcast"
    override val receiverPrefKey = Constants.SHARED_PREF_XDRIP_RECEIVERS
    override val enablePref = Constants.SHARED_PREF_SEND_TO_XDRIP

    override fun getIntent(context: Context): Intent {
        val intent = Intent(Constants.XDRIP_ACTION_GLUCOSE_READING)
        // always sends time as start time, because it is only set, if the sensorId have changed!
        val sensor = Bundle()
        sensor.putLong("sensorStartTime", if(ReceiveData.sensorStartTime > 0) ReceiveData.sensorStartTime else Utils.getDayStartTime())  // use start time of the current day
        val currentSensor = Bundle()
        currentSensor.putBundle("currentSensor", sensor)
        intent.putExtra("sas", currentSensor)
        val bleManager = Bundle()
        bleManager.putString("sensorSerial", ReceiveData.sensorID ?: context.packageName)
        intent.putExtra("bleManager", bleManager)
        intent.putExtra("glucose", ReceiveData.rawValue.toDouble())
        intent.putExtra("timestamp", ReceiveData.time)
        return intent
    }

}