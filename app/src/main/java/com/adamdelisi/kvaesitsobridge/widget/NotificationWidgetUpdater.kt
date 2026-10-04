package com.adamdelisi.kvaesitsobridge.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

class NotificationWidgetUpdater(
    private val context: Context,
    private val scope: CoroutineScope,
    private val changes: Flow<*>,
) {
    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch { changes.debounce(150).collect { update(context) } }
    }
    companion object {
        suspend fun update(context: Context) {
            try { NotificationWidget().updateAll(context) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                // Never log notification content or identifiers. Future events retry the update.
                Log.w("BridgeWidget", "Widget update failed: ${e.javaClass.simpleName}")
            }
        }
    }
}
