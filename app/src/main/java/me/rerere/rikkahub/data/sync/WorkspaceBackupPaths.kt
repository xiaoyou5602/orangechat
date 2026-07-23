/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.sync

import java.io.File

internal object WorkspaceBackupPaths {
    const val WORKSPACES_DIR = "workspaces"
    const val FILES_DIR = "files"
    const val ENTRY_PREFIX = "$WORKSPACES_DIR/"

    private val rootPattern = Regex("[A-Za-z0-9._-]+")

    fun isValidRoot(root: String): Boolean = root.matches(rootPattern)

    fun userFilesDirectories(workspacesRoot: File): List<File> {
        return workspacesRoot.listFiles()
            ?.filter { workspaceDir ->
                workspaceDir.isDirectory &&
                    isValidRoot(workspaceDir.name) &&
                    File(workspaceDir, FILES_DIR).isDirectory
            }
            ?.map { File(it, FILES_DIR) }
            ?.sortedBy { it.parentFile?.name }
            .orEmpty()
    }

    fun resolveRestoreTarget(workspacesRoot: File, entryName: String): File? {
        if (!entryName.startsWith(ENTRY_PREFIX)) return null
        val relative = entryName.removePrefix(ENTRY_PREFIX)
        val root = relative.substringBefore('/', missingDelimiterValue = "")
        val afterRoot = relative.substringAfter('/', missingDelimiterValue = "")
        if (!isValidRoot(root) || !afterRoot.startsWith("$FILES_DIR/")) return null

        val fileRelativePath = afterRoot.removePrefix("$FILES_DIR/")
        if (fileRelativePath.isBlank()) return null

        val filesRoot = File(File(workspacesRoot, root), FILES_DIR).canonicalFile
        val target = File(filesRoot, fileRelativePath).canonicalFile
        return target.takeIf {
            it.path.startsWith(filesRoot.path + File.separator)
        }
    }

    fun resolveImportedFilesDirectory(workspacesRoot: File, root: String): File? {
        if (!isValidRoot(root)) return null
        val canonicalWorkspaces = workspacesRoot.canonicalFile
        val filesDir = File(File(canonicalWorkspaces, root), FILES_DIR).canonicalFile
        return filesDir.takeIf {
            it.path.startsWith(canonicalWorkspaces.path + File.separator)
        }
    }
}
