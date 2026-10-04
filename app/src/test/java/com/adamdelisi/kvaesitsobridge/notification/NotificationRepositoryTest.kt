package com.adamdelisi.kvaesitsobridge.notification

import org.junit.Assert.*
import org.junit.Test

class NotificationRepositoryTest {
    private fun item(key: String = "key", text: String = "first") = NotificationSummary(key, "chat", "Chat", text = text, timestamp = 100)
    @Test fun `update replaces the same key and never duplicates it`() {
        val repository = NotificationRepository()
        repository.replace(listOf(item(), item(text = "updated")))
        assertEquals("updated", repository.state.value.notifications.single().text)
    }
    @Test fun `authoritative resync removes dismissed and stale keys`() {
        val repository = NotificationRepository()
        repository.replace(listOf(item("a"), item("b")))
        repository.replace(listOf(item("b")))
        assertEquals(listOf("b"), repository.state.value.notifications.map { it.key })
        repository.replace(emptyList())
        assertTrue(repository.state.value.connected)
        assertTrue(repository.state.value.notifications.isEmpty())
    }
    @Test fun `disconnect clears sensitive text and reconnect starts fresh`() {
        val repository = NotificationRepository()
        repository.replace(listOf(item()))
        repository.disconnect()
        assertFalse(repository.state.value.connected)
        assertTrue(repository.state.value.notifications.isEmpty())
        repository.replace(listOf(item("fresh")))
        assertEquals("fresh", repository.state.value.notifications.single().key)
    }
    @Test fun `uninstalled source is removed`() {
        val repository = NotificationRepository()
        repository.replace(listOf(item(), item("other").copy(packageName = "mail")))
        repository.removePackage("chat")
        assertEquals("mail", repository.state.value.notifications.single().packageName)
    }
}
