package de.michelinside.glucodatahandler.common.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.Intents
import de.michelinside.glucodatahandler.common.ReceiveData
import de.michelinside.glucodatahandler.common.SourceState
import de.michelinside.glucodatahandler.common.SourceStateData
import de.michelinside.glucodatahandler.common.database.dbAccess
import de.michelinside.glucodatahandler.common.notifier.DataSource
import de.michelinside.glucodatahandler.common.utils.GlucoDataUtils
import de.michelinside.glucodatahandler.common.utils.TextToSpeechUtils
import de.michelinside.glucodatahandler.common.utils.Utils


class InternalActionReceiver: BroadcastReceiver() {

    private val LOG_ID = "GDH.InternalActionReceiver"
    override fun onReceive(context: Context, intent: Intent) {       
        try {
            Log.d(LOG_ID, "Action received: ${intent.action} - bundle: ${Utils.dumpBundle(intent.extras)}")
            when(intent.action) {
                Intent.ACTION_DATE_CHANGED -> {
                    Log.i(LOG_ID, "Action: date changed received - trigger db cleanup")
                    dbAccess.cleanUpOldData()
                }
                Constants.ACTION_FLOATING_WIDGET_TOGGLE -> {
                    Log.d(LOG_ID, "Action: floating widget toggle")
                    val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, Context.MODE_PRIVATE)
                    with(sharedPref.edit()) {
                        putBoolean(
                            Constants.SHARED_PREF_FLOATING_WIDGET, !sharedPref.getBoolean(
                                Constants.SHARED_PREF_FLOATING_WIDGET, false))
                        apply()
                    }
                }
                Constants.ACTION_DUMMY_VALUE -> {
                    ReceiveData.handleIntent(context, DataSource.JUGGLUCO, GlucoDataUtils.getDummyGlucodataIntent(false).extras)
                }
                Constants.ACTION_SPEAK -> {
                    TextToSpeechUtils.speak(ReceiveData.getAsText(context, true))
                }
                Intents.GLUCODATA_ACTION -> {
                    val extras = intent.extras!!
                    val lastTime = ReceiveData.time
                    val lastIobCobTime = ReceiveData.iobCobTime
                    val lastIob = ReceiveData.iob
                    val lastCob = ReceiveData.cob
                    val source = DataSource.fromIndex(extras.getInt(Constants.EXTRA_SOURCE_INDEX,
                        DataSource.NONE.ordinal))
                    ReceiveData.handleIntent(context, source, extras, false)
                    if (ReceiveData.time == lastTime && (lastIobCobTime == ReceiveData.iobCobTime || (lastCob == ReceiveData.cob && lastIob == ReceiveData.iob)))
                        SourceStateData.setState(source, SourceState.NO_NEW_VALUE)
                }
                else -> {
                    Log.w(LOG_ID, "Unknown action '${intent.action}' received!" )
                }
            }
        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }
}
