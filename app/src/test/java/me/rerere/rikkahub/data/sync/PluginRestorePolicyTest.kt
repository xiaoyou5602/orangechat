package me.rerere.rikkahub.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginRestorePolicyTest {
    @Test
    fun `restores plugin files only when requested and shared storage is writable`() {
        assertTrue(
            PluginRestorePolicy.shouldRestoreFiles(
                includePlugins = true,
                filesSelected = true,
                canWriteSharedStorage = true,
            ),
        )
        assertFalse(
            PluginRestorePolicy.shouldRestoreFiles(
                includePlugins = true,
                filesSelected = true,
                canWriteSharedStorage = false,
            ),
        )
        assertFalse(
            PluginRestorePolicy.shouldRestoreFiles(
                includePlugins = false,
                filesSelected = true,
                canWriteSharedStorage = true,
            ),
        )
        assertFalse(
            PluginRestorePolicy.shouldRestoreFiles(
                includePlugins = true,
                filesSelected = false,
                canWriteSharedStorage = true,
            ),
        )
    }
}
