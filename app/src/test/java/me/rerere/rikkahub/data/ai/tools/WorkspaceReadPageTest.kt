package me.rerere.rikkahub.data.ai.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceReadPageTest {
    @Test
    fun `returns a line page with a continuation cursor`() {
        val text = (1..250).joinToString("\n") { "line $it" }

        val page = text.toSafeReadPage(startLine = 1, maxLines = 200)

        assertEquals(1, page.startLine)
        assertEquals(200, page.endLine)
        assertEquals(250, page.totalLines)
        assertEquals(201, page.nextStartLine)
        assertTrue(page.truncated)
        assertTrue(page.text.endsWith("line 200"))
    }

    @Test
    fun `withholds a continuous quoted base64 payload and resumes after it`() {
        val encodedLine = "    \"${"A".repeat(76)}\""
        val text = buildString {
            appendLine("import base64")
            appendLine("_BLOB = (")
            repeat(8) { appendLine(encodedLine) }
            appendLine(")")
            append("print('ready')")
        }

        val page = text.toSafeReadPage(startLine = 1, maxLines = 200)

        assertEquals("import base64\n_BLOB = (", page.text)
        assertEquals(3..10, page.withheldEncodedPayload)
        assertEquals(11, page.nextStartLine)
        assertTrue(page.truncated)

        val resumed = text.toSafeReadPage(startLine = page.nextStartLine!!, maxLines = 200)
        assertEquals(")\nprint('ready')", resumed.text)
        assertNull(resumed.withheldEncodedPayload)
        assertFalse(resumed.truncated)
    }

    @Test
    fun `caps ordinary text output below twenty four kibibytes`() {
        val line = "x".repeat(200)
        val text = (1..400).joinToString("\n") { "$it-$line" }

        val page = text.toSafeReadPage(startLine = 1, maxLines = 400)

        assertTrue(page.text.toByteArray(Charsets.UTF_8).size <= 24 * 1024)
        assertTrue(page.endLine < 400)
        assertEquals(page.endLine + 1, page.nextStartLine)
        assertTrue(page.truncated)
    }

    @Test
    fun `does not treat normal source code as encoded payload`() {
        val text = listOf(
            "fun main() {",
            "    val greeting = \"hello world\"",
            "    println(greeting)",
            "}",
        ).joinToString("\n")

        val page = text.toSafeReadPage(startLine = 1, maxLines = 200)

        assertEquals(text, page.text)
        assertNull(page.withheldEncodedPayload)
        assertFalse(page.truncated)
    }
}
