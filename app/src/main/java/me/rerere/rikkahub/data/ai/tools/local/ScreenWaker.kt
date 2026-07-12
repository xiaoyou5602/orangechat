package me.rerere.rikkahub.data.ai.tools.local

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager

/**
 * Reusable helpers so launch_app and any screen-automation tool that needs the screen on can
 * opt in without duplicating the wake-lock dance. Ported from the upstream agent fork's
 * WakeScreenTool.kt (only the [ScreenWaker] object; the `wake_screen` Tool itself is already
 * provided by orangechat's SystemTools, so it is intentionally NOT duplicated here).
 */
internal object ScreenWaker {
    fun isInteractive(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java)?.isInteractive == true

    fun isKeyguardLocked(ctx: Context): Boolean =
        ctx.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true

    fun isKeyguardSecure(ctx: Context): Boolean =
        ctx.getSystemService(KeyguardManager::class.java)?.isKeyguardSecure == true

    /**
     * Wake the screen if currently off. Uses an ACQUIRE_CAUSES_WAKEUP wake lock held briefly
     * (`holdMs`) - long enough for the OS to render a frame, then released so we are not
     * pinning the CPU. FULL_WAKE_LOCK is deprecated since API 17 but still functional for
     * the "turn the display on" use case.
     */
    @Suppress("DEPRECATION")
    fun wakeIfOff(ctx: Context, holdMs: Long = 3_000L): Boolean {
        val pm = ctx.getSystemService(PowerManager::class.java) ?: return false
        if (pm.isInteractive) return false
        val wl = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
            "rikkahub:wake_screen"
        )
        return try {
            wl.acquire(holdMs)
            true
        } catch (_: Throwable) {
            false
        }
    }
}
