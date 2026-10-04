package com.adamdelisi.kvaesitsobridge.notification

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.adamdelisi.kvaesitsobridge.actions.BridgeAction
import kotlinx.coroutines.launch

/** Activity PendingIntent avoids prohibited widget broadcast/service activity trampolines. */
class NotificationOpenActivity : ComponentActivity() {
    private var handled = false
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState) }
    override fun onResume() {
        super.onResume()
        if (handled) return
        handled = true
        val key = intent.getStringExtra(EXTRA_KEY)
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        if (key == null || pkg == null) { finish(); return }
        lifecycleScope.launch {
            if (!NotificationActionExecutor().open(this@NotificationOpenActivity, BridgeAction.OpenNotification(key, pkg))) {
                Toast.makeText(this@NotificationOpenActivity, "This notification is no longer available", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }
    companion object {
        const val EXTRA_KEY = "notification_key"
        const val EXTRA_PACKAGE = "notification_package"
    }
}
