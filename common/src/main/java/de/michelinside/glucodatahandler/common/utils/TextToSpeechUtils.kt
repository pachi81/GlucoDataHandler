package de.michelinside.glucodatahandler.common.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import de.michelinside.glucodatahandler.common.R
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean


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
        try {
            if(textToSpeech == null) {
                Log.i(LOG_ID, "initTextToSpeech called")
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
            destroyTextToSpeech()
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

    fun getAsFile(text: String): File? {
        try {
            Log.d(LOG_ID, "getAsFile called with text='${text}'")
            if(textToSpeech != null) {
                val created = AtomicBoolean(false)
                val error = AtomicBoolean(true)
                val result = textToSpeech!!.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d(LOG_ID, "onStart called for utteranceId=$utteranceId")
                    }
                    override fun onDone(utteranceId: String?) {
                        Log.d(LOG_ID, "onDone called for utteranceId=$utteranceId")
                        error.set(false)
                        created.set(true)
                    }
                    override fun onError(utteranceId: String?) {
                        Log.d(LOG_ID, "onError called for utteranceId=$utteranceId")
                        error.set(true)
                        created.set(true)
                    }
                })
                if(result == TextToSpeech.SUCCESS){
                    val tempFile = kotlin.io.path.createTempFile(prefix = "gdh_tts", suffix = ".wav")
                    textToSpeech!!.synthesizeToFile(text, null, tempFile.toFile(), "createFile")
                    var count = 0
                    while(!created.get() && count++ < 100) {
                        Thread.sleep(50)
                    }
                    if(error.get()) {
                        Log.d(LOG_ID, "getAsFile error")
                        tempFile.toFile().delete()
                        return null
                    }
                    Log.d(LOG_ID, "file created: ${tempFile.fileName}")
                    return tempFile.toFile()
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "getAsFile exception: " + exc.toString())
        }
        return null
    }
}

class LocaleChangeNotifier: BroadcastReceiver() {
    private val LOG_ID = "GDH.LocaleChangeNotifier"
    override fun onReceive(context: Context, intent: Intent) {
        try {
            Log.d(LOG_ID, "Action received: ${intent.action} - bundle: ${Utils.dumpBundle(intent.extras)}")
            if (TextToSpeechUtils.localChanged(context)) {
                val workRequest =
                    OneTimeWorkRequestBuilder<TextToSpeechWorker>().build()
                WorkManager.getInstance(context).enqueue(workRequest)
            }
        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + ": " + exc.stackTraceToString())
        }
    }
}

class TextToSpeechWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {
    override fun doWork(): Result {TextToSpeechUtils.destroyTextToSpeech()
        TextToSpeechUtils.initTextToSpeech(applicationContext)
        return Result.success()
    }
}

