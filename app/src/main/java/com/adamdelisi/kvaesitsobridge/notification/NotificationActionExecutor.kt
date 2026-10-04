package com.adamdelisi.kvaesitsobridge.notification

import android.app.Activity
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.os.Build
import com.adamdelisi.kvaesitsobridge.actions.BridgeAction
import com.adamdelisi.kvaesitsobridge.bridgeGraph

class NotificationActionExecutor {
    /** Caller is a resumed, visible activity opened directly by the widget host. */
    suspend fun open(activity: Activity, action: BridgeAction.OpenNotification): Boolean {
        val listener = activity.bridgeGraph.awaitListener()
        val sbn = listener?.findActive(action.key)?.takeIf { it.packageName == action.packageName }
        val pending = sbn?.notification?.contentIntent
        if (pending != null) {
            try {
                val options = visibleActivityOptions()
                pending.send(activity, 0, null, null, null, null, options.toBundle())
                listener.cancelAfterOpen(action.key)
                return true
            } catch (_: PendingIntent.CanceledException) { /* Source app replaced or canceled the intent. */ }
            catch (_: SecurityException) { /* Try the public app launch path. */ }
        }
        try {
            val launch = activity.packageManager.getLaunchIntentForPackage(action.packageName)
            if (launch != null) { activity.startActivity(launch); return true }
            // Android 13+: launch sender does not require package visibility.
            if (Build.VERSION.SDK_INT >= 33) {
                activity.startIntentSender(activity.packageManager.getLaunchIntentSenderForPackage(action.packageName), null, 0, 0, 0, visibleActivityOptions().toBundle())
                return true
            }
        } catch (_: ActivityNotFoundException) { }
        catch (_: android.content.IntentSender.SendIntentException) { }
        catch (_: SecurityException) { }
        return false
    }

    suspend fun dismiss(context: Context, action: BridgeAction.DismissNotification): Boolean =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
            context.bridgeGraph.awaitListener()?.dismiss(action.key) == true
        }

    private fun visibleActivityOptions() = ActivityOptions.makeBasic().apply {
        if (Build.VERSION.SDK_INT >= 36) {
            setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE)
        } else if (Build.VERSION.SDK_INT >= 34) {
            @Suppress("DEPRECATION")
            setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
        }
    }
}
