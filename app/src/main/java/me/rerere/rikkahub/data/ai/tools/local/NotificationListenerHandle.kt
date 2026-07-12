package me.rerere.rikkahub.data.ai.tools.local

/**
 * Bridge the workflow engine's `notification_received` trigger pre-flight check to the
 * notification listener connection state. The engine calls [isBound] before firing a
 * notification-triggered workflow so a missing listener surfaces as a clear FAILED row
 * ("notification_listener_not_enabled") instead of silently never matching.
 *
 * [me.rerere.rikkahub.data.service.RikkaNotificationListenerService] flips [connected] from
 * its onListenerConnected / onListenerDisconnected callbacks.
 */
object NotificationListenerHandle {
    @Volatile
    var connected: Boolean = false

    fun isBound(): Boolean = connected
}
