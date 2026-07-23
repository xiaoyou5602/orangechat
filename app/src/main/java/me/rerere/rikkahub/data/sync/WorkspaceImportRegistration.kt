/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.sync

import me.rerere.rikkahub.data.db.entity.WorkspaceEntity

internal object WorkspaceImportRegistration {
    fun resolve(
        root: String,
        existing: List<WorkspaceEntity>,
        now: Long,
    ): WorkspaceEntity {
        require(WorkspaceBackupPaths.isValidRoot(root)) { "无效的工作区标识" }

        existing.firstOrNull { it.root == root }?.let { return it }
        require(existing.none { it.id == root }) { "工作区标识与现有工作区冲突" }

        val baseName = "导入工作区 · ${root.take(8)}"
        val existingNames = existing.mapTo(mutableSetOf()) { it.name.trim() }
        var name = baseName
        var suffix = 2
        while (name in existingNames) {
            name = "$baseName ($suffix)"
            suffix++
        }

        return WorkspaceEntity(
            id = root,
            name = name,
            root = root,
            createdAt = now,
            updatedAt = now,
            lastAccessAt = null,
        )
    }
}
