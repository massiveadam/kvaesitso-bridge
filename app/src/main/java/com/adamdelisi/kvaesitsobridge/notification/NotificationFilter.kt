package com.adamdelisi.kvaesitsobridge.notification

import com.adamdelisi.kvaesitsobridge.settings.AppMode
import com.adamdelisi.kvaesitsobridge.settings.AppRule
import com.adamdelisi.kvaesitsobridge.settings.BridgeSettings

/** Policy is independent of both Android and launcher choice. */
object NotificationFilter {
    private val backgroundCategories = setOf("service", "sys", "transport", "progress")
    private val humanCategories = setOf("msg", "email", "call", "event", "reminder", "alarm")

    fun entries(source: List<NotificationSummary>, settings: BridgeSettings, locked: Boolean = false): List<FeedEntry> {
        val eligible = source.distinctBy { it.key }.filter { n ->
            val rule = settings.appRules[n.packageName] ?: AppRule()
            rule.mode != AppMode.HIDDEN &&
                (!n.isOngoing || settings.allowOngoing) &&
                (rule.includeNoise || !isNoise(n))
        }
        // Show children instead of their aggregate. Keep a summary when no eligible child exists.
        val groupsWithChildren = eligible.filter { !it.isGroupSummary && it.groupKey != null }
            .map { it.packageName to it.groupKey }.toSet()
        val ungrouped = eligible.filter {
            !it.isGroupSummary || (it.packageName to it.groupKey) !in groupsWithChildren
        }
        val ordered = if (settings.newestFirst) {
            ungrouped.sortedWith(compareByDescending<NotificationSummary> { it.timestamp }.thenBy { it.key })
        } else {
            ungrouped.sortedWith(compareBy<NotificationSummary> { it.timestamp }.thenBy { it.key })
        }
        // Different keys may be identical messages. Keep them: distinct conversations can share text.
        return ordered.take(settings.maxNotifications.coerceIn(1, 8)).map { n ->
            val private = settings.hideContent || (locked && settings.hideContentWhenLocked) ||
                settings.appRules[n.packageName]?.mode == AppMode.APP_ONLY
            FeedEntry(n.key, n.packageName, n.appName, if (private) null else content(n), n.isClearable)
        }
    }

    fun state(source: List<NotificationSummary>, settings: BridgeSettings, granted: Boolean, connected: Boolean, locked: Boolean): FeedState {
        if (!granted) return FeedState.PermissionRequired
        if (!connected) return FeedState.Connecting
        val entries = entries(source, settings, locked)
        if (entries.isNotEmpty()) return FeedState.Content(entries)
        val candidates = source.filter { n ->
            (!n.isOngoing || settings.allowOngoing) &&
                (settings.appRules[n.packageName]?.includeNoise == true || !isNoise(n))
        }
        if (candidates.isNotEmpty() && candidates.all { settings.appRules[it.packageName]?.mode == AppMode.HIDDEN }) {
            return FeedState.AllAppsHidden
        }
        return FeedState.Empty
    }

    private fun isNoise(n: NotificationSummary): Boolean {
        val human = n.isConversation || n.category in humanCategories
        return n.isMedia || n.isForegroundService || n.category in backgroundCategories ||
            (n.importance < 3 && !human)
    }

    private fun content(n: NotificationSummary): String? {
        val title = n.title?.trim()?.takeIf { it.isNotEmpty() }
        val text = n.text?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            title == null -> text
            text == null || title == text -> title
            else -> "$title: $text"
        }
    }
}
