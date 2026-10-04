package com.adamdelisi.kvaesitsobridge.notification

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService

object NotificationAccess {
    fun component(context: Context) = ComponentName(context, BridgeNotificationListener::class.java)
    fun granted(context: Context): Boolean = context.getSystemService(NotificationManager::class.java)
        .isNotificationListenerAccessGranted(component(context))
    fun requestReconnect(context: Context) {
        if (granted(context)) NotificationListenerService.requestRebind(component(context))
    }
    fun openSettings(context: Context) {
        val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
            .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component(context).flattenToString())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { context.startActivity(detail) } catch (_: android.content.ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
