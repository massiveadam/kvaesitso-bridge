package com.adamdelisi.kvaesitsobridge.notification

import com.adamdelisi.kvaesitsobridge.settings.*
import org.junit.Assert.*
import org.junit.Test

class NotificationFilterTest {
    private fun notification(key: String = "one", pkg: String = "chat", time: Long = 100) =
        NotificationSummary(key, pkg, pkg, title = "Meg", text = "Dinner?", timestamp = time)
    private fun feed(vararg notifications: NotificationSummary, settings: BridgeSettings = BridgeSettings(), locked: Boolean = false) =
        NotificationFilter.entries(notifications.toList(), settings, locked)

    @Test fun `missing access takes priority over notifications`() {
        assertEquals(FeedState.PermissionRequired, NotificationFilter.state(listOf(notification()), BridgeSettings(), false, true, false))
    }
    @Test fun `granted but disconnected never exposes stale content`() {
        assertEquals(FeedState.Connecting, NotificationFilter.state(listOf(notification()), BridgeSettings(), true, false, false))
    }
    @Test fun `connected empty feed has a quiet empty state`() {
        assertEquals(FeedState.Empty, NotificationFilter.state(emptyList(), BridgeSettings(), true, true, false))
    }
    @Test fun `one message preserves its action identity and content`() {
        assertEquals(FeedEntry("one", "chat", "chat", "Meg: Dinner?", true), feed(notification()).single())
    }
    @Test fun `newest first orders across apps and caps after filtering`() {
        val input = listOf(notification("a", "mail", 10), notification("b", "sms", 30), notification("c", "chat", 20))
        val settings = BridgeSettings(maxNotifications = 2)
        assertEquals(listOf("b", "c"), NotificationFilter.entries(input, settings).map { it.key })
        assertEquals(listOf("a", "c"), NotificationFilter.entries(input, settings.copy(newestFirst = false)).map { it.key })
    }
    @Test fun `hidden app is removed before maximum is applied`() {
        val settings = BridgeSettings(maxNotifications = 1, appRules = mapOf("chat" to AppRule(AppMode.HIDDEN)))
        assertEquals("other", feed(notification(), notification("other", "mail", 20), settings = settings).single().key)
        assertEquals(FeedState.AllAppsHidden, NotificationFilter.state(listOf(notification()), settings, true, true, false))
    }
    @Test fun `background system noise does not swallow hidden apps guidance`() {
        val source = listOf(notification(), notification("system", "android").copy(category = "sys", isOngoing = true))
        val settings = BridgeSettings(appRules = mapOf("chat" to AppRule(AppMode.HIDDEN)))
        assertEquals(FeedState.AllAppsHidden, NotificationFilter.state(source, settings, true, true, false))
    }
    @Test fun `app only hides title and text but keeps notification actions`() {
        val settings = BridgeSettings(appRules = mapOf("chat" to AppRule(AppMode.APP_ONLY)))
        val entry = feed(notification(), settings = settings).single()
        assertNull(entry.content)
        assertEquals("one", entry.key)
        assertTrue(entry.isClearable)
    }
    @Test fun `global privacy and lock privacy apply to every app`() {
        assertNull(feed(notification(), settings = BridgeSettings(hideContent = true)).single().content)
        assertNull(feed(notification(), locked = true).single().content)
        assertNotNull(feed(notification(), settings = BridgeSettings(hideContentWhenLocked = false), locked = true).single().content)
    }
    @Test fun `ongoing is opt in even with an app override`() {
        val item = notification().copy(isOngoing = true)
        val settings = BridgeSettings(appRules = mapOf("chat" to AppRule(includeNoise = true)))
        assertTrue(feed(item, settings = settings).isEmpty())
        assertEquals(1, feed(item, settings = settings.copy(allowOngoing = true)).size)
    }
    @Test fun `clearability comes from Android rather than ongoing policy`() {
        assertFalse(feed(notification().copy(isClearable = false)).single().isClearable)
        assertTrue(feed(notification()).single().isClearable)
    }
    @Test fun `background status is suppressed without app specific names`() {
        val source = listOf(
            notification("vpn", "arbitrary.vpn").copy(category = "service"),
            notification("music").copy(isMedia = true),
            notification("work").copy(isForegroundService = true),
            notification("system").copy(category = "sys"),
            notification("download").copy(category = "progress"),
            notification("silent").copy(importance = 2),
        )
        assertTrue(NotificationFilter.entries(source, BridgeSettings()).isEmpty())
        val override = BridgeSettings(appRules = source.associate { it.packageName to AppRule(includeNoise = true) })
        assertEquals(source.size, NotificationFilter.entries(source, override.copy(maxNotifications = 8)).size)
    }
    @Test fun `silent human messages and conversations remain visible`() {
        assertEquals(1, feed(notification().copy(category = "msg", importance = 1)).size)
        assertEquals(1, feed(notification().copy(isConversation = true, importance = 2)).size)
    }
    @Test fun `null and empty text retain an app row without fabricated content`() {
        assertNull(feed(notification().copy(title = null, text = null)).single().content)
        assertNull(feed(notification().copy(title = "  ", text = "")).single().content)
        assertEquals("Meg", feed(notification().copy(text = null)).single().content)
        assertEquals("Dinner?", feed(notification().copy(title = null)).single().content)
        assertEquals("Meg", feed(notification().copy(text = "Meg")).single().content)
    }
    @Test fun `group summary is replaced by eligible children in the same package`() {
        val summary = notification("summary").copy(groupKey = "g", isGroupSummary = true)
        val child = notification("child").copy(groupKey = "g")
        assertEquals(listOf("child"), feed(summary, child).map { it.key })
        assertEquals(listOf("summary"), feed(summary).map { it.key })
        val otherApp = child.copy(packageName = "other")
        assertEquals(2, feed(summary, otherApp).size)
    }
    @Test fun `filtered children do not swallow an otherwise visible summary`() {
        val summary = notification("summary").copy(groupKey = "g", isGroupSummary = true)
        val child = notification("child").copy(groupKey = "g", isOngoing = true)
        assertEquals("summary", feed(summary, child).single().key)
    }
    @Test fun `same app has multiple independently actionable rows`() {
        assertEquals(2, feed(notification("a"), notification("b")).size)
        assertEquals(1, feed(notification(), notification()).size)
    }
    @Test fun `equal timestamps have a deterministic order`() {
        assertEquals(listOf("a", "b"), feed(notification("b"), notification("a")).map { it.key })
    }
}
