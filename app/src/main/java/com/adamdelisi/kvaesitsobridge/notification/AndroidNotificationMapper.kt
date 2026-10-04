package com.adamdelisi.kvaesitsobridge.notification

import android.app.Notification
import android.content.Context
import android.os.Build
import android.os.Parcelable
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService.Ranking
import android.service.notification.StatusBarNotification

/** Extract only text, with a bounded size. No bitmap, remote views, or intent enters the model. */
class AndroidNotificationMapper(private val context: Context) {
    fun map(sbn: StatusBarNotification, ranking: Ranking?): NotificationSummary {
        val n = sbn.notification
        val extras = n.extras
        val messageBundles = if (Build.VERSION.SDK_INT >= 33) {
            extras.getParcelableArray(Notification.EXTRA_MESSAGES, Parcelable::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        }
        val messages = Notification.MessagingStyle.Message.getMessagesFromBundleArray(messageBundles)
        val latestMessage = messages.lastOrNull()
        val messageText = latestMessage?.text?.toString()
        val sender = latestMessage?.senderPerson?.name?.toString()
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.mapNotNull { clean(it?.toString()) }?.takeLast(3)?.joinToString(" · ")
        val title = clean(sender) ?: clean(extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        val text = clean(messageText) ?: clean(extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString())
            ?: clean(extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()) ?: clean(lines)
        val appName = try {
            val info = context.packageManager.getApplicationInfo(sbn.packageName, 0)
            context.packageManager.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) { sbn.packageName }
        return NotificationSummary(
            key = sbn.key, packageName = sbn.packageName, appName = appName,
            title = title, text = text, timestamp = sbn.postTime,
            isClearable = sbn.isClearable, isOngoing = sbn.isOngoing,
            category = n.category, groupKey = if (sbn.isGroup) sbn.groupKey else null,
            isGroupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            isForegroundService = n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0,
            isMedia = extras.containsKey(Notification.EXTRA_MEDIA_SESSION),
            isConversation = ranking?.isConversation == true || messages.isNotEmpty(),
            importance = ranking?.importance ?: 3,
        )
    }
    private fun clean(value: String?): String? = value?.replace(Regex("\\s+"), " ")?.trim()?.take(1500)?.takeIf { it.isNotEmpty() }
}
