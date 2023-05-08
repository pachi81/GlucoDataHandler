package de.michelinside.glucodatahandler.common.notifier

import android.content.Context
import android.os.Bundle

interface NotifierInterface {
    fun OnNotifyData(context: Context, dataSource: NotifyDataSource, extras: Bundle?)
}