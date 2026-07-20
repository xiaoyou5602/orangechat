package me.rerere.rikkahub.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class PendingDatabaseRestoreTest {
    @Test
    fun `replaces database set and removes stale wal files`() {
        val root = Files.createTempDirectory("orangechat-db-restore").toFile()
        try {
            val databases = root.resolve("databases").apply { mkdirs() }
            val liveDatabase = databases.resolve("rikka_hub")
            liveDatabase.writeText("old-db")
            databases.resolve("rikka_hub-wal").writeText("old-wal")
            databases.resolve("rikka_hub-shm").writeText("old-shm")

            val pending = root.resolve("pending").apply { mkdirs() }
            pending.resolve("rikka_hub.db").writeText("new-db")
            pending.resolve("rikka_hub-shm").writeText("new-shm")

            PendingDatabaseRestore.replaceDatabaseFiles(liveDatabase, pending)

            assertEquals("new-db", liveDatabase.readText())
            assertFalse(databases.resolve("rikka_hub-wal").exists())
            assertEquals("new-shm", databases.resolve("rikka_hub-shm").readText())
            assertFalse(databases.resolve("rikka_hub.restore_rollback").exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `refuses pending restore without primary database`() {
        val root = Files.createTempDirectory("orangechat-db-restore-invalid").toFile()
        try {
            val databases = root.resolve("databases").apply { mkdirs() }
            val liveDatabase = databases.resolve("rikka_hub").apply { writeText("old-db") }
            val pending = root.resolve("pending").apply { mkdirs() }
            pending.resolve("rikka_hub-wal").writeText("orphan-wal")

            val result = runCatching {
                PendingDatabaseRestore.replaceDatabaseFiles(liveDatabase, pending)
            }

            assertTrue(result.isFailure)
            assertEquals("old-db", liveDatabase.readText())
        } finally {
            root.deleteRecursively()
        }
    }
}
