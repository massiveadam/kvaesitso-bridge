package com.adamdelisi.kvaesitsobridge.widget

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.color.ColorProvider as DayNightColorProvider
import androidx.glance.unit.ColorProvider
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.adamdelisi.kvaesitsobridge.actions.BridgeAction
import com.adamdelisi.kvaesitsobridge.MainActivity
import com.adamdelisi.kvaesitsobridge.R
import com.adamdelisi.kvaesitsobridge.bridgeGraph
import com.adamdelisi.kvaesitsobridge.notification.*
import com.adamdelisi.kvaesitsobridge.settings.BridgeSettings
import com.adamdelisi.kvaesitsobridge.settings.TextTone
import kotlinx.coroutines.flow.first

class NotificationWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact
    override val stateDefinition = null

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val graph = context.bridgeGraph
        graph.refreshEnvironment()
        val prefs = graph.settings.settings.first()
        val initial = graph.feed.first()
        provideContent {
            val settings by graph.settings.settings.collectAsState(prefs)
            val state by graph.feed.collectAsState(initial)
            Feed(context, state, settings)
        }
    }
}

@Composable
private fun Feed(context: Context, state: FeedState, settings: BridgeSettings) {
    val tone = when (settings.textTone) {
        TextTone.AUTOMATIC -> DayNightColorProvider(day = Color(0xFF202720), night = Color(0xFFF0F3EF))
        TextTone.LIGHT -> ColorProvider(Color(0xFFF0F3EF))
        TextTone.DARK -> ColorProvider(Color(0xFF202720))
    }
    Column(GlanceModifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp)) {
        when (state) {
            FeedState.PermissionRequired -> Prompt(context, R.string.widget_access, R.string.widget_enable, tone)
            FeedState.Connecting -> Prompt(context, R.string.widget_connecting, R.string.widget_connect_help, tone)
            FeedState.AllAppsHidden -> Prompt(context, R.string.widget_hidden, R.string.widget_settings, tone)
            FeedState.Empty -> Unit
            is FeedState.Content -> {
                val fitting = WidgetLayout.fit(state.entries, LocalSize.current.height.value, context.resources.configuration.fontScale, settings)
                // On very small sizes, show an app-only row rather than clipping content/actions.
                val rows = if (fitting.isEmpty()) state.entries.take(1).map { it.copy(content = null) } else fitting
                rows.forEach { entry ->
                    NotificationRow(context, entry, settings, tone)
                    Spacer(GlanceModifier.height(if (settings.comfortableSpacing) 14.dp else 6.dp))
                }
            }
        }
    }
}

@Composable
private fun Prompt(context: Context, title: Int, subtitle: Int, tone: androidx.glance.unit.ColorProvider) {
    Column(GlanceModifier.fillMaxWidth().clickable(actionStartActivity(Intent(context, MainActivity::class.java))).padding(vertical = 6.dp)) {
        Text(context.getString(title), style = TextStyle(color = tone, fontSize = 14.sp, fontWeight = FontWeight.Medium))
        Text(context.getString(subtitle), style = TextStyle(color = tone, fontSize = 12.sp))
    }
}

@Composable
private fun NotificationRow(context: Context, entry: FeedEntry, settings: BridgeSettings, tone: androidx.glance.unit.ColorProvider) {
    val openIntent = Intent(context, NotificationOpenActivity::class.java)
        .setData("bridge://notification/${android.net.Uri.encode(entry.key)}".toUri())
        .putExtra(NotificationOpenActivity.EXTRA_KEY, entry.key)
        .putExtra(NotificationOpenActivity.EXTRA_PACKAGE, entry.packageName)
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(openIntent)).padding(vertical = 3.dp)) {
            Text(entry.appName, style = TextStyle(color = tone, fontSize = settings.appNameSize.sp, fontWeight = FontWeight.Medium), maxLines = 1)
            entry.content?.let {
                Spacer(GlanceModifier.height(4.dp))
                Text(it, style = TextStyle(color = tone, fontSize = settings.contentSize.sp), maxLines = settings.maxLines)
            }
        }
        if (entry.isClearable) {
            Box(GlanceModifier.size(48.dp).clickable(actionRunCallback<DismissNotificationAction>(actionParametersOf(NotificationKey to entry.key))), contentAlignment = Alignment.Center) {
                Image(provider = ImageProvider(R.drawable.ic_dismiss), contentDescription = context.getString(R.string.dismiss_notification, entry.appName),
                    modifier = GlanceModifier.size(14.dp), colorFilter = ColorFilter.tint(tone))
            }
        }
    }
}

private val NotificationKey = ActionParameters.Key<String>("notification_key")

class DismissNotificationAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val key = parameters[NotificationKey] ?: return
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) {
            if (!NotificationActionExecutor().dismiss(context, BridgeAction.DismissNotification(key))) {
                android.widget.Toast.makeText(context, "Notification cannot be dismissed", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        NotificationWidgetUpdater.update(context)
    }
}
