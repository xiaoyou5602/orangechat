package me.rerere.rikkahub.data.sync

import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class WorkspaceImportRegistrationTest {
    @Test
    fun `new import keeps source root as workspace id and root`() {
        val imported = WorkspaceImportRegistration.resolve(
            root = "workspace-source-id",
            existing = emptyList(),
            now = 123L,
        )

        assertEquals("workspace-source-id", imported.id)
        assertEquals("workspace-source-id", imported.root)
        assertEquals("导入工作区 · workspac", imported.name)
    }

    @Test
    fun `existing workspace with matching root is reused`() {
        val existing = workspace(id = "existing-id", root = "workspace-source-id")

        val imported = WorkspaceImportRegistration.resolve(
            root = "workspace-source-id",
            existing = listOf(existing),
            now = 456L,
        )

        assertSame(existing, imported)
    }

    @Test
    fun `generated import name avoids an existing name`() {
        val imported = WorkspaceImportRegistration.resolve(
            root = "workspace-source-id",
            existing = listOf(
                workspace(
                    id = "another-id",
                    root = "another-root",
                    name = "导入工作区 · workspac",
                ),
            ),
            now = 789L,
        )

        assertEquals("导入工作区 · workspac (2)", imported.name)
    }

    private fun workspace(
        id: String,
        root: String,
        name: String = "Existing",
    ) = WorkspaceEntity(
        id = id,
        name = name,
        root = root,
        createdAt = 1L,
        updatedAt = 1L,
    )
}
