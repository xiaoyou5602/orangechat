package me.rerere.rikkahub.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class WorkspaceBackupPathsTest {
    @Test
    fun `backs up only workspace user files directories`() {
        val root = Files.createTempDirectory("orangechat-workspaces").toFile()
        try {
            val first = root.resolve("workspace-a").apply { mkdirs() }
            first.resolve("files").mkdirs()
            first.resolve("linux").mkdirs()
            root.resolve("invalid root").resolve("files").mkdirs()

            assertEquals(
                listOf(first.resolve("files")),
                WorkspaceBackupPaths.userFilesDirectories(root),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `resolves user file entry inside matching workspace`() {
        val root = Files.createTempDirectory("orangechat-workspaces").toFile()
        try {
            val resolved = WorkspaceBackupPaths.resolveRestoreTarget(
                workspacesRoot = root,
                entryName = "workspaces/workspace-a/files/notes/today.md",
            )

            assertEquals(
                root.resolve("workspace-a/files/notes/today.md").canonicalFile,
                resolved,
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `rejects rootfs and traversal entries`() {
        val root = Files.createTempDirectory("orangechat-workspaces").toFile()
        try {
            assertNull(
                WorkspaceBackupPaths.resolveRestoreTarget(
                    root,
                    "workspaces/workspace-a/linux/bin/sh",
                )
            )
            assertNull(
                WorkspaceBackupPaths.resolveRestoreTarget(
                    root,
                    "workspaces/workspace-a/files/../../secret.txt",
                )
            )
        } finally {
            root.deleteRecursively()
        }
    }
}
