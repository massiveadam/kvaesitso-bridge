package com.adamdelisi.kvaesitsobridge.actions

/** Future commands can add cases without depending on the notification model. */
sealed interface BridgeAction {
    data class OpenNotification(val key: String, val packageName: String) : BridgeAction
    data class DismissNotification(val key: String) : BridgeAction
}
