package de.michelinside.glucodatahandler.common.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.util.Log
import de.michelinside.glucodatahandler.common.R
import java.util.Locale


object TextToSpeechUtils {
    private val LOG_ID = "GDH.TTS_Utils"
    private var textToSpeech: TextToSpeech? = null
    private var curLocal = ""

    fun isAvailable(): Boolean {
        return textToSpeech != null
    }

    fun localChanged(context: Context): Boolean {
        val newLocale = context.resources.getString(R.string.locale)
        Log.d(LOG_ID, "check locale changed with curLocal='${curLocal}' - newLocale='${newLocale}'")
        return curLocal != newLocale
    }

    fun initTextToSpeech(context: Context) {
        Log.d(LOG_ID, "initTextToSpeech called")
        try {
            if(textToSpeech == null) {
                textToSpeech = TextToSpeech(context) { status ->
                    Log.d(LOG_ID, "initTextToSpeech status=$status")
                    if (status == TextToSpeech.SUCCESS) {
                        curLocal = context.resources.getString(R.string.locale)
                        textToSpeech!!.language = Locale(curLocal)
                        Log.i(LOG_ID, "TextToSpeech enabled for language=${textToSpeech!!.voice.locale}")
                    } else {
                        Log.w(LOG_ID, "TextToSpeech failed to init with error=$status")
                        destroyTextToSpeech()
                    }
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "initTextToSpeech exception: " + exc.toString())
        }
    }

    fun destroyTextToSpeech() {
        Log.i(LOG_ID, "destroyTextToSpeech called")
        try {
            if (textToSpeech != null) {
                textToSpeech!!.shutdown()
                textToSpeech = null
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "destroyTextToSpeech exception: " + exc.toString())
        }
    }

    fun speak(text: String) {
        Log.d(LOG_ID, "speak called with text='${text}'")
        if(textToSpeech != null) {
            if(textToSpeech!!.isSpeaking) {
                textToSpeech!!.stop()
            } else {
                /*val bundle = Bundle()
                bundle.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_ALARM)*/
                textToSpeech!!.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
    }
}



class LocaleChangeNotifier: BroadcastReceiver() {

    private val LOG_ID = "GDH.LocaleChangeNotifier"
    override fun onReceive(context: Context, intent: Intent) {
        try {
            Log.d(LOG_ID, "Action received: ${intent.action} - bundle: ${Utils.dumpBundle(intent.extras)}")
            if (TextToSpeechUtils.localChanged(context)) {
                TextToSpeechUtils.destroyTextToSpeech()
                TextToSpeechUtils.initTextToSpeech(context)
            }
        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + ": " + exc.stackTraceToString())
        }
    }
}

