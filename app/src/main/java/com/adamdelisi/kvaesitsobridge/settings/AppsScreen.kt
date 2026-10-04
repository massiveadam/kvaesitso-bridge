package com.adamdelisi.kvaesitsobridge.settings

import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun AppsScreen(settings: BridgeSettings, update: ((BridgeSettings) -> BridgeSettings) -> Unit) {
    val context = LocalContext.current
    val installed = remember(settings.observedApps) {
        settings.observedApps.filter { (pkg, _) ->
            try { context.packageManager.getApplicationInfo(pkg, 0); true }
            catch (_: PackageManager.NameNotFoundException) { false }
        }.toList().sortedBy { it.second.lowercase() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Apps appear after they send a notification. Full shows text, App only hides it, and Hidden removes the app from the feed.", style = MaterialTheme.typography.bodyMedium)
        if (installed.isEmpty()) Text("No notification apps yet. Enable access and wait for an app to notify you.")
        installed.forEach { (pkg, name) ->
            val rule = settings.appRules[pkg] ?: AppRule()
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppMode.entries.forEach { mode ->
                        FilterChip(selected = rule.mode == mode, onClick = {
                            update { s -> s.copy(appRules = s.appRules + (pkg to (s.appRules[pkg] ?: AppRule()).copy(mode = mode))) }
                        }, label = { Text(when (mode) { AppMode.FULL -> "Full"; AppMode.APP_ONLY -> "App only"; AppMode.HIDDEN -> "Hidden" }) })
                    }
                }
                if (rule.mode != AppMode.HIDDEN) {
                    SettingSwitch("Include background status", "Override noise filtering for this app. Ongoing items also need the global toggle.", rule.includeNoise) { enabled ->
                        update { s -> s.copy(appRules = s.appRules + (pkg to (s.appRules[pkg] ?: AppRule()).copy(includeNoise = enabled))) }
                    }
                }
            }
        }
    }
}
