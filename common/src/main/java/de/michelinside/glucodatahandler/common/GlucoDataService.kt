package de.michelinside.glucodatahandler.common

import android.os.Bundle
import android.os.Parcel
import android.util.Log
import com.google.android.gms.wearable.*


open class GlucoDataService : WearableListenerService(), MessageClient.OnMessageReceivedListener {
    private val LOG_ID = "GlucoDataHandler.GlucoDataService"

    override fun onCreate() {
        super.onCreate()
        Log.d(LOG_ID, "onCreate called")
        /*if (Build.VERSION.SDK_INT >= 26) {
            val CHANNEL_ID = "my_channel_01"
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Channel human readable title",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                channel
            )
            val notification: Notification = Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("")
                .setContentText("").build()
            startForeground(1, notification)
        }*/

        Wearable.getMessageClient(this).addListener(this)
        Log.d(LOG_ID, "MessageClient added")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(LOG_ID, "onDestroy called")
    }

    fun bytesToBundle(bytes: ByteArray): Bundle? {
        val parcel = Parcel.obtain()
        parcel.unmarshall(bytes, 0, bytes.size)
        parcel.setDataPosition(0)
        val bundle = parcel.readBundle(GlucoDataService::class.java.getClassLoader())
        parcel.recycle()
        return bundle
    }

    override fun onMessageReceived(p0: MessageEvent) {
        try {
            Log.d(LOG_ID, "onMessageReceived called: " + p0.toString())
            ReceiveData.handleIntent(this, ReceiveDataSource.MESSAGECLIENT, bytesToBundle(p0.data))
        } catch (exc: Exception) {
            Log.e(LOG_ID, "onMessageReceived exception: " + exc.message.toString() )
        }
    }
}