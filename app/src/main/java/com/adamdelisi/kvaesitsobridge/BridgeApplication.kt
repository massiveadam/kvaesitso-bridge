package com.adamdelisi.kvaesitsobridge

import android.app.Application
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.adamdelisi.kvaesitsobridge.notification.*
import com.adamdelisi.kvaesitsobridge.settings.SettingsRepository
import com.adamdelisi.kvaesitsobridge.widget.NotificationWidgetUpdater
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class BridgeApplication : Application() {
    lateinit var graph: BridgeGraph
        private set
    override fun onCreate() {
        super.onCreate()
        graph = BridgeGraph(this)
        val screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { graph.refreshEnvironment() }
        }
        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenReceiver, screenFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            // These three system broadcasts are protected on Android 12.
            registerReceiver(screenReceiver, screenFilter)
        }
        graph.start()
    }
}

val Context.bridgeGraph: BridgeGraph get() = (applicationContext as BridgeApplication).graph

data class EnvironmentState(val granted: Boolean, val locked: Boolean)

class BridgeGraph(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings = SettingsRepository(context)
    val notifications = NotificationRepository()
    var listener: BridgeNotificationListener? = null
        internal set
    private val environment = MutableStateFlow(readEnvironment())
    val feed: Flow<FeedState> = combine(notifications.state, settings.settings, environment) { snapshot, prefs, env ->
        NotificationFilter.state(snapshot.notifications, prefs, env.granted, snapshot.connected, env.locked)
    }.distinctUntilChanged()

    fun start() {
        NotificationWidgetUpdater(context, scope, combine(feed, settings.settings) { state, prefs -> state to prefs }).start()
        NotificationAccess.requestReconnect(context)
    }
    fun refreshEnvironment() {
        environment.value = readEnvironment()
        if (!environment.value.granted) notifications.disconnect()
    }
    private fun readEnvironment() = EnvironmentState(NotificationAccess.granted(context), context.getSystemService(KeyguardManager::class.java).isDeviceLocked)

    suspend fun awaitListener(): BridgeNotificationListener? {
        listener?.let { return it }
        NotificationAccess.requestReconnect(context)
        withTimeoutOrNull(2500) { notifications.state.first { it.connected } }
        return listener
    }
}

class BridgeLifecycleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        context.bridgeGraph.refreshEnvironment()
        NotificationAccess.requestReconnect(context)
        val pending = goAsync()
        context.bridgeGraph.scope.launch {
            try { NotificationWidgetUpdater.update(context) } finally { pending.finish() }
        }
    }
}

class PackageRemovedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_FULLY_REMOVED) return
        val pkg = intent.data?.schemeSpecificPart ?: return
        val graph = context.bridgeGraph
        graph.notifications.removePackage(pkg)
        val pending = goAsync()
        graph.scope.launch {
            try { graph.settings.removeApp(pkg); NotificationWidgetUpdater.update(context) }
            finally { pending.finish() }
        }
    }
}
