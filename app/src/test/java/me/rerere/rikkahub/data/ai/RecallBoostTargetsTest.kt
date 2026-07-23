package me.rerere.rikkahub.data.ai

import me.rerere.rikkahub.data.model.ExternalMemory
import org.junit.Assert.assertEquals
import org.junit.Test

class RecallBoostTargetsTest {
    @Test
    fun `same source is boosted once per memory config`() {
        val firstConfig = ExternalMemory(name = "first")
        val secondConfig = ExternalMemory(name = "second")

        val targets = recallBoostTargets(
            listOf(
                RecalledExternalMemory("first copy", firstConfig, 42L),
                RecalledExternalMemory("duplicate", firstConfig, 42L),
                RecalledExternalMemory("other database", secondConfig, 42L),
                RecalledExternalMemory("text fallback", firstConfig),
            )
        )

        assertEquals(2, targets.size)
        assertEquals(
            setOf(firstConfig.id to 42L, secondConfig.id to 42L),
            targets.map { it.config.id to it.sourceMessageId }.toSet(),
        )
    }
}
