package de.michelinside.glucodatahandler.common.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.provider.Settings
import de.michelinside.glucodatahandler.common.Constants
import de.michelinside.glucodatahandler.common.Intents
import de.michelinside.glucodatahandler.common.receiver.AAPSReceiver
import de.michelinside.glucodatahandler.common.receiver.AidexBroadcastReceiver
import de.michelinside.glucodatahandler.common.receiver.DexcomBroadcastReceiver
import de.michelinside.glucodatahandler.common.receiver.DiaboxReceiver
import de.michelinside.glucodatahandler.common.receiver.GlucoseDataReceiver
import de.michelinside.glucodatahandler.common.receiver.LibrePatchedReceiver
import de.michelinside.glucodatahandler.common.receiver.NamedBroadcastReceiver
import de.michelinside.glucodatahandler.common.receiver.NamedReceiver
import de.michelinside.glucodatahandler.common.receiver.NotificationReceiver
import de.michelinside.glucodatahandler.common.receiver.NsEmulatorReceiver
import de.michelinside.glucodatahandler.common.receiver.XDripBroadcastReceiver
import de.michelinside.glucodatahandler.common.utils.Log
import de.michelinside.glucodatahandler.common.utils.PackageUtils

object ReceiverManager {
    private const val LOG_ID = "GDH.srv.ReceiverManager"

    private var glucoDataReceiver: GlucoseDataReceiver? = null
    private var xDripReceiver: XDripBroadcastReceiver?  = null
    private var librePatchedReceiver: LibrePatchedReceiver?  = null
    private var aapsReceiver: AAPSReceiver?  = null
    private var dexcomReceiver: DexcomBroadcastReceiver? = null
    private var nsEmulatorReceiver: NsEmulatorReceiver? = null
    private var diaboxReceiver: DiaboxReceiver? = null
    private var notificationReceiver: NotificationReceiver? = null
    private var aidexReceiver: AidexBroadcastReceiver? = null
    private val registeredReceivers = mutableSetOf<String>()

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun registerReceiver(context: Context, receiver: NamedReceiver, filter: IntentFilter): Boolean {
        Log.i(LOG_ID, "Register receiver ${receiver.getName()} for $receiver on $context")
        try {
            if (receiver is NamedBroadcastReceiver) {
                PackageUtils.registerReceiver(context, receiver, filter)
            }
            registeredReceivers.add(receiver.getName())
            return true
        } catch (exc: Exception) {
            Log.e(LOG_ID, "registerReceiver exception: $exc")
        }
        return false
    }

    fun unregisterReceiver(context: Context, receiver: NamedReceiver?) {
        try {
            if (receiver != null) {
                Log.i(LOG_ID, "Unregister receiver ${receiver.getName()} on $context")
                registeredReceivers.remove(receiver.getName())
                if (receiver is NamedBroadcastReceiver) {
                    context.unregisterReceiver(receiver)
                }
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "unregisterReceiver exception: $exc")
        }
    }

    fun isRegistered(receiver: NamedReceiver): Boolean {
        return registeredReceivers.contains(receiver.getName())
    }

    fun updateSourceReceiver(context: Context, key: String? = null) {
        Log.d(LOG_ID, "Register receiver")
        try {
            val sharedPref = context.getSharedPreferences(Constants.SHARED_PREF_TAG, MODE_PRIVATE)
            if(key.isNullOrEmpty() || key == Constants.SHARED_PREF_SOURCE_JUGGLUCO_ENABLED) {
                if (sharedPref.getBoolean(Constants.SHARED_PREF_SOURCE_JUGGLUCO_ENABLED, true)) {
                    if(glucoDataReceiver == null) {
                        glucoDataReceiver = GlucoseDataReceiver()
                        if(!registerReceiver(context, glucoDataReceiver!!, IntentFilter("glucodata.Minute")))
                            glucoDataReceiver = null
                    }
                } else if (glucoDataReceiver != null) {
                    unregisterReceiver(context, glucoDataReceiver)
                    glucoDataReceiver = null
                }
            }

            if(key.isNullOrEmpty() || key == Constants.SHARED_PREF_SOURCE_XDRIP_ENABLED) {
                if (sharedPref.getBoolean(Constants.SHARED_PREF_SOURCE_XDRIP_ENABLED, true)) {
                    if(xDripReceiver == null) {
                        xDripReceiver = XDripBroadcastReceiver()
                        if(!registerReceiver(context, xDripReceiver!!, IntentFilter("com.eveningoutpost.dexdrip.BgEstimate")))
                            xDripReceiver = null
                    }
                } else if (xDripReceiver != null) {
                    unregisterReceiver(context, xDripReceiver)
                    xDripReceiver = null
                }
            }

            if(key.isNullOrEmpty() || key == Constants.SHARED_PREF_SOURCE_AAPS_ENABLED) {
                if (sharedPref.getBoolean(Constants.SHARED_PREF_SOURCE_AAPS_ENABLED, true)) {
                    if(aapsReceiver == null) {
                        aapsReceiver = AAPSReceiver()
                        if(!registerReceiver(context, aapsReceiver!!, IntentFilter(Intents.AAPS_BROADCAST_ACTION)))
                            aapsReceiver = null
                    }
                } else if (aapsReceiver != null) {
                    unregisterReceiver(context, aapsReceiver)
                    aapsReceiver = null
                }
            }

            if(key.isNullOrEmpty() || key == Constants.SHARED_PREF_SOURCE_BYODA_ENABLED) {
                if (sharedPref.getBoolean(Constants.SHARED_PREF_SOURCE_BYODA_ENABLED, true)) {
                    if(dexcomReceiver == null) {
                        val dexcomFilter = IntentFilter()
                        dexcomFilter.addAction(Intents.DEXCOM_CGM_BROADCAST_ACTION)
                        dexcomFilter.addAction(Intents.DEXCOM_G7_BROADCAST_ACTION)
                        dexcomReceiver = DexcomBroadcastReceiver()
                        if(!registerReceiver(context, dexcomReceiver!!, dexcomFilter))
                            dexcomReceiver = null
                    }
                } else if (dexcomReceiver != null) {
                    unregisterReceiver(context, dexcomReceiver)
                    dexcomReceiver = null
                }
            }

            if(key.isNullOrEmpty() || key == Constants.SHARED_PREF_SOURCE_EVERSENSE_ENABLED) {
                if (sharedPref.getBoolean(Constants.SHARED_PREF_SOURCE_EVERSENSE_ENABLED, true)) {
                    if(nsEmulatorReceiver == null) {
                        nsEmulatorReceiver = NsEmulatorReceiver()
                        if(!registerReceiver(context, nsEmulatorReceiver!!, IntentFilter(Intents.NS_EMULATOR_BROADCAST_ACTION)))
                            nsEmulatorReceiver = null
                    }
                } else if (nsEmulatorReceiver != null) {
                    unregisterReceiver(context, nsEmulatorReceiver)
                    nsEmulatorReceiver = null
                }
            }

            if(key.isNullOrEmpty() || key == Constants.SHARED_PREF_SOURCE_DIABOX_ENABLED) {
                if (sharedPref.getBoolean(Constants.SHARED_PREF_SOURCE_DIABOX_ENABLED, true)) {
                    if(diaboxReceiver == null) {
                        diaboxReceiver = DiaboxReceiver()
                        if(!registerReceiver(context, diaboxReceiver!!, IntentFilter(Intents.DIABOX_BROADCAST_ACTION)))
                            diaboxReceiver = null
                    }
                } else if (diaboxReceiver != null) {
                    unregisterReceiver(context, diaboxReceiver)
                    diaboxReceiver = null
                }
            }

            if(key.isNullOrEmpty() || key == Constants.SHARED_PREF_SOURCE_LIBRE_PATCHED_ENABLED) {
                if (sharedPref.getBoolean(Constants.SHARED_PREF_SOURCE_LIBRE_PATCHED_ENABLED, true)) {
                    if(librePatchedReceiver == null) {
                        librePatchedReceiver = LibrePatchedReceiver()
                        val filter = IntentFilter()
                        filter.addAction(Constants.XDRIP_ACTION_GLUCOSE_READING)
                        filter.addAction(Constants.XDRIP_ACTION_SENSOR_ACTIVATE)
                        if(!registerReceiver(context, librePatchedReceiver!!, filter))
                            librePatchedReceiver = null
                    }
                } else if (librePatchedReceiver != null) {
                    unregisterReceiver(context, librePatchedReceiver)
                    librePatchedReceiver = null
                }
            }
            if (key.isNullOrEmpty() || key == Constants.SHARED_PREF_SOURCE_AIDEX_ENABLED) {
                if (sharedPref.getBoolean(Constants.SHARED_PREF_SOURCE_AIDEX_ENABLED, true)) {
                    if (aidexReceiver == null) {
                        aidexReceiver = AidexBroadcastReceiver()
                        val filter = IntentFilter()
                        filter.addAction(Intents.AIDEX_BROADCAST_ACTION)
                        if (!registerReceiver(context, aidexReceiver!!, filter))
                            aidexReceiver = null
                    }
                } else if (aidexReceiver != null) {
                    unregisterReceiver(context, aidexReceiver)
                    aidexReceiver = null
                }

            }

            if (key.isNullOrEmpty() || key == Constants.SHARED_PREF_SOURCE_NOTIFICATION_ENABLED) {
                updateNotificationReceiver(sharedPref, context)
            }


        } catch (exc: Exception) {
            Log.e(LOG_ID, "registerSourceReceiver exception: $exc")
        }
    }


    fun checkNotificationReceiverPermission(context: Context, requestPermission: Boolean): Boolean {
        val notificationListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        if(!notificationListeners.contains(context.packageName)) {
            if(requestPermission) {
                // request permissions
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            return false
        }
        return true
    }

    fun updateNotificationReceiver(sharedPref: SharedPreferences, context: Context) {
        try {
            // default to false because reading notifications is a scary permission to give for no reason
            if (sharedPref.getBoolean(Constants.SHARED_PREF_SOURCE_NOTIFICATION_ENABLED, false)) {
                Log.i(LOG_ID, "Notification source enabled")
                checkNotificationReceiverPermission(context, true)
                notificationReceiver = NotificationReceiver()
                registerReceiver(context, notificationReceiver!!, IntentFilter())
            } else if(notificationReceiver!=null) {
                Log.i(LOG_ID, "Notification source disabled")
                unregisterReceiver(context, notificationReceiver)
                notificationReceiver = null
            }
            // notification listeners can not be unregistered
        } catch (exc: Exception) {
            Log.e(LOG_ID, "updateNotificationReceiver exception: $exc")
        }
    }

    fun unregisterSourceReceiver(context: Context) {
        try {
            Log.d(LOG_ID, "Unregister receiver")
            if (glucoDataReceiver != null) {
                unregisterReceiver(context, glucoDataReceiver)
                glucoDataReceiver = null
            }
            if (xDripReceiver != null) {
                unregisterReceiver(context, xDripReceiver)
                xDripReceiver = null
            }
            if (dexcomReceiver != null) {
                unregisterReceiver(context, dexcomReceiver)
                dexcomReceiver = null
            }
            if (nsEmulatorReceiver != null) {
                unregisterReceiver(context, nsEmulatorReceiver)
                nsEmulatorReceiver = null
            }
            if (aapsReceiver != null) {
                unregisterReceiver(context, aapsReceiver)
                aapsReceiver = null
            }
            if (diaboxReceiver != null) {
                unregisterReceiver(context, diaboxReceiver)
                diaboxReceiver = null
            }
            if (librePatchedReceiver != null) {
                unregisterReceiver(context, librePatchedReceiver)
                librePatchedReceiver = null
            }
            if(notificationReceiver != null) {
                unregisterReceiver(context, notificationReceiver)
                notificationReceiver = null
            }
            if (aidexReceiver != null) {
                unregisterReceiver(context, aidexReceiver)
                aidexReceiver = null
            }
        } catch (exc: Exception) {
            Log.e(LOG_ID, "unregisterSourceReceiver exception: $exc")
        }
    }
}