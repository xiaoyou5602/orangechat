package me.rerere.rikkahub.plugin.scanner

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class PluginStoragePathsTest {
    @Test
    fun `debug build uses app private plugin directory`() {
        val resolved = PluginStoragePaths.resolve(
            appFilesDir = File("app-files"),
            sharedStorageRoot = File("shared-storage"),
            useIsolatedStorage = true,
        )

        assertEquals(File("app-files", "plugins"), resolved)
    }

    @Test
    fun `release build keeps legacy shared plugin directory`() {
        val resolved = PluginStoragePaths.resolve(
            appFilesDir = File("app-files"),
            sharedStorageRoot = File("shared-storage"),
            useIsolatedStorage = false,
        )

        assertEquals(File("shared-storage", PluginScanner.PLUGINS_DIR), resolved)
    }
}
