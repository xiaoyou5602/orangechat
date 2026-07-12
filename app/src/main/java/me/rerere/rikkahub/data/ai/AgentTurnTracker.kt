package me.rerere.rikkahub.data.ai

/**
 * No-op stub of the agent fork's AgentTurnTracker.
 *
 * The upstream agent uses this to record that an automation action (tap / swipe / gesture)
 * happened during the current turn, so the UI can show an "automation happened" indicator and
 * the safety system can rate-limit. orangechat does not port that tracking surface, so
 * [recordAutomationAction] is a cheap no-op. The screen automation tools call it unconditionally;
 * this stub keeps their source identical to upstream.
 */
object AgentTurnTracker {
    fun recordAutomationAction() = Unit
}
