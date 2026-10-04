package com.adamdelisi.kvaesitsobridge.widget

import com.adamdelisi.kvaesitsobridge.notification.FeedEntry
import com.adamdelisi.kvaesitsobridge.settings.BridgeSettings
import org.junit.Assert.*
import org.junit.Test

class WidgetLayoutTest {
    private val entries = (1..5).map { FeedEntry("$it", "chat", "Chat", "Hello", true) }
    @Test fun `taller widget fits more rows while keeping the order`() {
        val small = WidgetLayout.fit(entries, 180f, 1f, BridgeSettings())
        val tall = WidgetLayout.fit(entries, 400f, 1f, BridgeSettings())
        assertTrue(tall.size > small.size)
        assertEquals(entries.take(tall.size), tall)
    }
    @Test fun `large text respects the same height budget`() {
        assertTrue(WidgetLayout.fit(entries, 300f, 2f, BridgeSettings()).size < WidgetLayout.fit(entries, 300f, 1f, BridgeSettings()).size)
    }
    @Test fun `app only rows fit more densely`() {
        assertTrue(WidgetLayout.fit(entries.map { it.copy(content = null) }, 300f, 1f, BridgeSettings()).size > WidgetLayout.fit(entries, 300f, 1f, BridgeSettings()).size)
    }
    @Test fun `zero height has no fitting rows`() { assertTrue(WidgetLayout.fit(entries, 0f, 1f, BridgeSettings()).isEmpty()) }
}
