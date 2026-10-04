package com.adamdelisi.kvaesitsobridge.notification

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.adamdelisi.kvaesitsobridge.bridgeGraph
import kotlinx.coroutines.launch

class BridgeNotificationListener : NotificationListenerService() {
    private val graph get() = applicationContext.bridgeGraph
    private val mapper by lazy { AndroidNotificationMapper(this) }
    private var connected = false

    override fun onListenerConnected() {
        connected = true
        graph.listener = this
        graph.refreshEnvironment()
        refresh(currentRanking)
    }
    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap) { refresh(rankingMap) }
    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap) { refresh(rankingMap) }
    override fun onNotificationRankingUpdate(rankingMap: RankingMap) { refresh(rankingMap) }

    // An authoritative active set on each event also catches removals missed during reconnection.
    private fun refresh(rankings: RankingMap) {
        if (!connected) return
        if (!NotificationAccess.granted(this)) { detach(); return }
        try {
            val summaries = activeNotifications.orEmpty().filter { it.packageName != packageName }.map { sbn ->
                val ranking = Ranking().takeIf { rankings.getRanking(sbn.key, it) }
                mapper.map(sbn, ranking)
            }
            graph.notifications.replace(summaries)
            graph.scope.launch { graph.settings.observeApps(summaries.associate { it.packageName to it.appName }) }
        } catch (_: SecurityException) { detach() }
    }
    override fun onListenerDisconnected() {
        detach()
        NotificationAccess.requestReconnect(this)
    }
    override fun onDestroy() { detach(); super.onDestroy() }
    private fun detach() {
        connected = false
        if (graph.listener === this) {
            graph.listener = null
            graph.notifications.disconnect()
            graph.refreshEnvironment()
        }
    }

    /** These methods are used on the main thread, only while Android has connected the listener. */
    fun findActive(key: String): StatusBarNotification? {
        if (!connected || !NotificationAccess.granted(this)) return null
        return try { getActiveNotifications(arrayOf(key))?.firstOrNull { it.key == key } }
        catch (_: SecurityException) { null }
    }
    fun dismiss(key: String): Boolean {
        val sbn = findActive(key) ?: return false
        if (!sbn.isClearable) return false
        return try { cancelNotification(key); true } catch (_: SecurityException) { false }
    }
    fun cancelAfterOpen(key: String) {
        val sbn = findActive(key) ?: return
        if (sbn.notification.flags and Notification.FLAG_AUTO_CANCEL != 0 && sbn.isClearable) dismiss(key)
    }
}
