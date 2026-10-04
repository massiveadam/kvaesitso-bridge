package com.adamdelisi.kvaesitsobridge.settings

enum class AppMode { FULL, APP_ONLY, HIDDEN }
enum class TextTone { AUTOMATIC, LIGHT, DARK }

data class AppRule(val mode: AppMode = AppMode.FULL, val includeNoise: Boolean = false)

data class BridgeSettings(
    val allowOngoing: Boolean = false,
    val maxNotifications: Int = 5,
    val newestFirst: Boolean = true,
    val hideContent: Boolean = false,
    val hideContentWhenLocked: Boolean = true,
    val appNameSize: Int = 14,
    val contentSize: Int = 15,
    val maxLines: Int = 2,
    val comfortableSpacing: Boolean = true,
    val textTone: TextTone = TextTone.AUTOMATIC,
    val appRules: Map<String, AppRule> = emptyMap(),
    val observedApps: Map<String, String> = emptyMap(),
)
