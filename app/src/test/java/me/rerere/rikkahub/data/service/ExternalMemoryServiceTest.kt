package me.rerere.rikkahub.data.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import me.rerere.rikkahub.data.model.ExternalMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalMemoryServiceTest {
    private val service = ExternalMemoryService(ExternalMemory())

    @Test
    fun `summary parser preserves bigint source message id`() {
        val summaries = service.parseSummaries(
            """
            [
              {
                "id": 7,
                "assistant_id": "rism-id",
                "content": "memory",
                "created_at": "2026-07-24T00:00:00Z",
                "embedding": "[0.25,-0.5]",
                "source_message_id": 922337203685477000
              },
              {
                "id": 8,
                "assistant_id": "rism-id",
                "content": "legacy summary",
                "created_at": "2026-07-24T00:00:01Z",
                "embedding": null,
                "source_message_id": null
              }
            ]
            """.trimIndent()
        )

        assertEquals(2, summaries.size)
        assertEquals(922337203685477000L, summaries[0].sourceMessageId)
        assertEquals(listOf(0.25f, -0.5f), summaries[0].embedding)
        assertNull(summaries[1].sourceMessageId)
    }

    @Test
    fun `recall boost request cannot choose heat amount`() {
        val body = Json.parseToJsonElement(
            buildRecallBoostRequest(
                assistantId = "rism-id",
                sourceMessageId = 42L,
            )
        ).jsonObject

        assertEquals(setOf("p_assistant_id", "p_mem_id"), body.keys)
        assertEquals("rism-id", body.getValue("p_assistant_id").jsonPrimitive.content)
        assertEquals(42L, body.getValue("p_mem_id").jsonPrimitive.long)
        assertFalse("amount" in body)
    }
}
