package com.adamdelisi.kvaesitsobridge.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adamdelisi.kvaesitsobridge.BridgeGraph
import com.adamdelisi.kvaesitsobridge.BuildConfig
import com.adamdelisi.kvaesitsobridge.notification.*
import com.adamdelisi.kvaesitsobridge.widget.NotificationWidgetReceiver
import java.io.IOException
import kotlinx.coroutines.launch

private enum class Screen { Home, Notifications, Apps, Appearance, About }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(graph: BridgeGraph) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) darkColorScheme(primary = Color(0xFFB9CCBE), background = Color(0xFF111612), surface = Color(0xFF111612))
        else lightColorScheme(primary = Color(0xFF435C49), background = Color(0xFFF8F9F5), surface = Color(0xFFF8F9F5))
    MaterialTheme(colorScheme = colors) {
        var screenName by rememberSaveable { mutableStateOf(Screen.Home.name) }
        val screen = Screen.valueOf(screenName)
        val settings by graph.settings.settings.collectAsStateWithLifecycle(BridgeSettings())
        val feed by graph.feed.collectAsStateWithLifecycle(FeedState.PermissionRequired)
        val scope = rememberCoroutineScope()
        val snackbar = remember { SnackbarHostState() }
        val update: ((BridgeSettings) -> BridgeSettings) -> Unit = { change ->
            scope.launch {
                try { graph.settings.update(change) }
                catch (_: IOException) { snackbar.showSnackbar("Could not save settings. Try again.") }
            }
        }
        BackHandler(screen != Screen.Home) { screenName = Screen.Home.name }
        Scaffold(
            topBar = {
                TopAppBar(title = { Text(if (screen == Screen.Home) "Kvaesitso Bridge" else screen.name, fontSize = 21.sp) },
                    navigationIcon = { if (screen != Screen.Home) TextButton(onClick = { screenName = Screen.Home.name }) { Text("Back") } })
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { insets ->
            LazyColumn(Modifier.fillMaxSize().padding(insets).padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
                when (screen) {
                    Screen.Home -> {
                        item { HomeStatus(feed, graph) }
                        item { WidgetSetup() }
                        item {
                            Text("Your feed", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(12.dp))
                            FeedPreview(feed)
                        }
                        item {
                            Column {
                                Screen.entries.filter { it != Screen.Home }.forEach { destination ->
                                    TextButton(onClick = { screenName = destination.name }, modifier = Modifier.fillMaxWidth()) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(destination.name, fontSize = 16.sp); Text("›")
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Screen.Notifications -> {
                        item { Text("Keep the feed quiet. Messages on silent channels remain visible; background status is filtered by default.", style = MaterialTheme.typography.bodyMedium) }
                        item { SettingSwitch("Allow ongoing notifications", "Per-app background overrides still require this permission for ongoing items.", settings.allowOngoing) { update { s -> s.copy(allowOngoing = it) } } }
                        item { IntegerSetting("Maximum entries", settings.maxNotifications, 1..8) { update { s -> s.copy(maxNotifications = it) } } }
                        item { SettingSwitch("Newest first", "Turn off to show the oldest waiting notifications first.", settings.newestFirst) { update { s -> s.copy(newestFirst = it) } } }
                        item { SettingSwitch("Hide all message content", "Show only app names in the feed and preview.", settings.hideContent) { update { s -> s.copy(hideContent = it) } } }
                        item { SettingSwitch("Hide content while locked", "App names remain visible. Android may briefly retain the previous widget image while refreshing.", settings.hideContentWhenLocked) { update { s -> s.copy(hideContentWhenLocked = it) } } }
                        item { Text("Notification text stays in memory on this device. It is never stored as history, backed up, logged, or sent anywhere. Android may redact sensitive notifications before Bridge receives them.", style = MaterialTheme.typography.bodySmall) }
                    }
                    Screen.Apps -> item { AppsScreen(settings, update) }
                    Screen.Appearance -> item { AppearanceScreen(settings, update) }
                    Screen.About -> item {
                        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            Text("Kvaesitso Bridge ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
                            Text("A quiet notification feed for your home screen.")
                            Text("Works with stock Kvaesitso and any launcher that supports Android widgets. Bridge contains no Kvaesitso code or private integrations.")
                            Text("No network permission. No analytics or ads. Notification content stays on this device. Only preferences and the names of apps that notify you are saved locally. Android backup is disabled.")
                            Text("Android owns notification access and background service binding. After a force stop, open Bridge once to let Android reconnect. Work profiles and sensitive content may be restricted by Android.")
                            Text("License: Apache 2.0. This is an independent companion app and is not affiliated with Kvaesitso.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeStatus(feed: FeedState, graph: BridgeGraph) {
    val context = LocalContext.current
    val snapshot by graph.notifications.state.collectAsStateWithLifecycle()
    // Reading the actual permission also handles a return from Android Settings.
    val granted = feed != FeedState.PermissionRequired
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (granted) "Notification access enabled" else "Notification access required", style = MaterialTheme.typography.titleMedium)
        Text("Bridge reads your active notifications to show a text feed and open or dismiss them. Grant Notification Access in Android Settings.", style = MaterialTheme.typography.bodyMedium)
        if (granted) {
            Text(if (snapshot.connected) "${(feed as? FeedState.Content)?.entries?.size ?: 0} visible notifications" else "Waiting for Android to connect the listener", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = { NotificationAccess.openSettings(context) }) { Text(if (granted) "Manage access" else "Enable notification access") }
    }
}

@Composable
private fun WidgetSetup() {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Add the widget", style = MaterialTheme.typography.titleMedium)
        Text("In Kvaesitso, open the widget page, enter its edit mode and add Kvaesitso Bridge. Resize it vertically to fit your feed. A 4 × 3 area is a good start.", style = MaterialTheme.typography.bodyMedium)
        val manager = context.getSystemService(AppWidgetManager::class.java)
        if (manager.isRequestPinAppWidgetSupported) {
            OutlinedButton(onClick = { manager.requestPinAppWidget(ComponentName(context, NotificationWidgetReceiver::class.java), null, null) }) { Text("Add to home screen") }
        }
    }
}

@Composable
private fun FeedPreview(state: FeedState) {
    val context = LocalContext.current
    when (state) {
        FeedState.PermissionRequired -> Text("Enable access to see your feed here.", style = MaterialTheme.typography.bodyMedium)
        FeedState.Connecting -> Text("Connecting to Android notifications…", style = MaterialTheme.typography.bodyMedium)
        FeedState.Empty -> Text("Quiet for now. The widget stays empty.", style = MaterialTheme.typography.bodyMedium)
        FeedState.AllAppsHidden -> Text("All apps with active notifications are hidden. Choose an app in Apps.", style = MaterialTheme.typography.bodyMedium)
        is FeedState.Content -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            state.entries.forEach { entry ->
                TextButton(onClick = {
                    context.startActivity(Intent(context, NotificationOpenActivity::class.java)
                        .putExtra(NotificationOpenActivity.EXTRA_KEY, entry.key)
                        .putExtra(NotificationOpenActivity.EXTRA_PACKAGE, entry.packageName))
                }, contentPadding = PaddingValues(0.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(entry.appName, style = MaterialTheme.typography.labelLarge)
                        entry.content?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3, color = MaterialTheme.colorScheme.onSurface) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingSwitch(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun IntegerSetting(title: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        TextButton(onClick = { onChange(value - 1) }, enabled = value > range.first) { Text("−") }
        Text(value.toString())
        TextButton(onClick = { onChange(value + 1) }, enabled = value < range.last) { Text("+") }
    }
}
