package me.rerere.rikkahub.plugin.scanner

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class PluginStoragePathsTest {
    @Test
    fun `all build variants use legacy shared plugin directory`() {
        val resolved = PluginStoragePaths.resolve(
            sharedStorageRoot = File("shared-storage"),
        )

        assertEquals(File("shared-storage", PluginScanner.PLUGINS_DIR), resolved)
    }
}
