package de.michelinside.glucodatahandler.common.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import de.michelinside.glucodatahandler.common.utils.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import de.michelinside.glucodatahandler.common.R
import de.michelinside.glucodatahandler.common.notifier.InternalNotifier
import de.michelinside.glucodatahandler.common.notifier.NotifySource
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean


object TextToSpeechUtils {
    private val LOG_ID = "GDH.TTS_Utils"
    private var textToSpeech: TextToSpeech? = null
    private var curLocal = ""
    private var enabled = AtomicBoolean(false)
    private var reInitOnError = false

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
                    try {
                        Log.d(LOG_ID, "initTextToSpeech status=$status")
                        if (status == TextToSpeech.SUCCESS) {
                            if(textToSpeech!!.voices != null && textToSpeech!!.voices.isNotEmpty()) {
                                Log.i(LOG_ID, "language: ${textToSpeech!!.language} - default: ${textToSpeech!!.defaultVoice?.name} - voices: ${textToSpeech!!.voices.size}")
                                val curLanguage = textToSpeech!!.voice?.locale
                                curLocal = context.resources.getString(R.string.locale)
                                textToSpeech!!.language = Locale(curLocal)
                                if(textToSpeech!!.voice == null) {
                                    Log.w(LOG_ID, "TextToSpeech voice is null, try default: ${textToSpeech!!.defaultVoice} or old language: ${curLanguage}")
                                    if(textToSpeech!!.defaultVoice != null)
                                        textToSpeech!!.voice = textToSpeech!!.defaultVoice
                                    else if(curLanguage != null) {
                                        textToSpeech!!.language = curLanguage
                                    }
                                }
                                if(textToSpeech!!.voice == null) {
                                    Log.w(LOG_ID, "TextToSpeech voice is still null, destroy it")
                                    destroyTextToSpeech(context)
                                } else {
                                    Log.i(LOG_ID, "TextToSpeech enabled for language=${textToSpeech!!.voice?.locale}")
                                    InternalNotifier.notify(context, NotifySource.TTS_STATE_CHANGED, null)
                                    enabled.set(true)
                                }
                            } else {
                                Log.w(LOG_ID, "TextToSpeech failed to init, no voices available")
                                destroyTextToSpeech(context)
                            }
                        } else {
                            Log.w(LOG_ID, "TextToSpeech failed to init with error=$status")
                            destroyTextToSpeech(context)
                        }
                    } catch (exc: Exception) {
                        Log.e(LOG_ID, "initTextToSpeech status exception: " + exc.toString())
                        destroyTextToSpeech(context)
                    }
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "initTextToSpeech exception: " + exc.toString())
            destroyTextToSpeech(context)
        }
    }

    fun destroyTextToSpeech(context: Context) {
        Log.i(LOG_ID, "destroyTextToSpeech called")
        try {
            enabled.set(false)
            if (textToSpeech != null) {
                textToSpeech!!.shutdown()
                textToSpeech = null
                InternalNotifier.notify(context, NotifySource.TTS_STATE_CHANGED, null)
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "destroyTextToSpeech exception: " + exc.toString())
        }
    }

    private fun reInitTextToSpeech(context: Context) {
        Log.i(LOG_ID, "reInitTextToSpeech called")
        destroyTextToSpeech(context)
        initTextToSpeech(context)
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

    private fun escapeSsml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    fun getAsFile(text: String, context: Context): File? {
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
                        Log.w(LOG_ID, "onError called for utteranceId=$utteranceId")
                        error.set(true)
                        created.set(true)
                    }
                })
                if(result == TextToSpeech.SUCCESS){
                    val tempFile = kotlin.io.path.createTempFile(prefix = "gdh_tts", suffix = ".wav")

                    val delayMs = 500

                    // Construct the SSML string
                    val ssmlText = """
                        <?xml version="1.0"?>
                        <speak version="1.0" xmlns="http://www.w3.org/2001/10/synthesis" xml:lang="$curLocal">
                            <break time="${delayMs}ms"/>
                            <s>${escapeSsml(text)}</s>
                        </speak>
                    """.trimIndent()

                    textToSpeech!!.synthesizeToFile(ssmlText, null, tempFile.toFile(), "createFile")
                    var count = 0
                    while(!created.get() && count++ < 100) {
                        Thread.sleep(50)
                    }
                    if(error.get()) {
                        Log.w(LOG_ID, "getAsFile error")
                        if(!reInitOnError) {
                            reInitOnError = true
                            reInitTextToSpeech(context)
                        }
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
            if(Log.isLoggable(LOG_ID, android.util.Log.DEBUG))
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
    override fun doWork(): Result {
        TextToSpeechUtils.destroyTextToSpeech(applicationContext)
        TextToSpeechUtils.initTextToSpeech(applicationContext)
        return Result.success()
    }
}

