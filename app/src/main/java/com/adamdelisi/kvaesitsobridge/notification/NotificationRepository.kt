package com.adamdelisi.kvaesitsobridge.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NotificationSnapshot(val connected: Boolean = false, val notifications: List<NotificationSummary> = emptyList())

/** Only the listener writes snapshots. Reconnection replaces the whole set, removing stale keys. */
class NotificationRepository {
    private val mutable = MutableStateFlow(NotificationSnapshot())
    val state = mutable.asStateFlow()
    fun replace(notifications: List<NotificationSummary>) {
        mutable.value = NotificationSnapshot(true, notifications.associateBy { it.key }.values.toList())
    }
    fun disconnect() { mutable.value = NotificationSnapshot() }
    fun removePackage(packageName: String) {
        mutable.value = mutable.value.copy(notifications = mutable.value.notifications.filterNot { it.packageName == packageName })
    }
}
