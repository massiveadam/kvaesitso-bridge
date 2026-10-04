package com.adamdelisi.kvaesitsobridge.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

@Composable
fun AppearanceScreen(settings: BridgeSettings, update: ((BridgeSettings) -> BridgeSettings) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("The widget is transparent and uses text only. Android's text scale also applies.", style = MaterialTheme.typography.bodyMedium)
        IntegerSetting("App name size", settings.appNameSize, 12..18) { update { s -> s.copy(appNameSize = it) } }
        IntegerSetting("Message size", settings.contentSize, 12..20) { update { s -> s.copy(contentSize = it) } }
        IntegerSetting("Lines per message", settings.maxLines, 1..3) { update { s -> s.copy(maxLines = it) } }
        SettingSwitch("Comfortable spacing", "Turn off for a denser feed.", settings.comfortableSpacing) { update { s -> s.copy(comfortableSpacing = it) } }
        Text("Widget text", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextTone.entries.forEach { tone ->
                FilterChip(selected = settings.textTone == tone, onClick = { update { s -> s.copy(textTone = tone) } },
                    label = { Text(when (tone) { TextTone.AUTOMATIC -> "Auto"; TextTone.LIGHT -> "Light"; TextTone.DARK -> "Dark" }) })
            }
        }
        Text("Auto follows Android's light or dark mode. If your wallpaper needs more contrast, choose Light or Dark. Widgets cannot reliably infer launcher wallpaper brightness.", style = MaterialTheme.typography.bodySmall)
    }
}
