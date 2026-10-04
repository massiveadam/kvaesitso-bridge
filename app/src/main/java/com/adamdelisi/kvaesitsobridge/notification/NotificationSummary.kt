package com.adamdelisi.kvaesitsobridge.notification

/** Framework-free snapshot. PendingIntents and framework notifications never leave the boundary. */
data class NotificationSummary(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String? = null,
    val text: String? = null,
    val timestamp: Long,
    val isClearable: Boolean = true,
    val isOngoing: Boolean = false,
    val category: String? = null,
    val groupKey: String? = null,
    val isGroupSummary: Boolean = false,
    val isForegroundService: Boolean = false,
    val isMedia: Boolean = false,
    val isConversation: Boolean = false,
    val importance: Int = 3,
)

data class FeedEntry(
    val key: String,
    val packageName: String,
    val appName: String,
    val content: String?,
    val isClearable: Boolean,
)

sealed interface FeedState {
    data object PermissionRequired : FeedState
    data object Connecting : FeedState
    data object Empty : FeedState
    data object AllAppsHidden : FeedState
    data class Content(val entries: List<FeedEntry>) : FeedState
}
