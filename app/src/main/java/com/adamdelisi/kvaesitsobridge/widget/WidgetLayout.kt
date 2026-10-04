package com.adamdelisi.kvaesitsobridge.widget

import com.adamdelisi.kvaesitsobridge.notification.FeedEntry
import com.adamdelisi.kvaesitsobridge.settings.BridgeSettings

/** Conservative height budget, including font scaling and a 48dp dismiss touch target. */
object WidgetLayout {
    fun fit(entries: List<FeedEntry>, heightDp: Float, fontScale: Float, settings: BridgeSettings): List<FeedEntry> {
        var remaining = (heightDp - 16).coerceAtLeast(0f)
        return entries.takeWhile { entry ->
            val textHeight = (settings.appNameSize * 1.3f + if (entry.content == null) 0f else 4f + settings.contentSize * 1.35f * settings.maxLines) * fontScale
            val rowHeight = maxOf(48f, textHeight) + if (settings.comfortableSpacing) 14f else 6f
            if (remaining >= rowHeight) { remaining -= rowHeight; true } else false
        }
    }
}
