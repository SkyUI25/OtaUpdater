package com.lich.updater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AppUpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val notification = com.lich.updater.Notifications.AppUpdateNotification(context)
            // It will cancel the installing notification (if still alive) and show the success one
            notification.showInstalled()
        }
    }
}
