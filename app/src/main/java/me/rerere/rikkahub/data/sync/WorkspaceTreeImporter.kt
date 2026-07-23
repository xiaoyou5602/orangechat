/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.sync

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

internal object WorkspaceTreeImporter {
    suspend fun import(context: Context, treeUri: Uri): WorkspaceImportResult = withContext(Dispatchers.IO) {
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        require('/' !in rootDocumentId) {
            "请选择工作区根目录，不要选择其中的子文件夹"
        }

        val workspacesRoot = File(context.filesDir, WorkspaceBackupPaths.WORKSPACES_DIR)
        val targetFilesDir = WorkspaceBackupPaths.resolveImportedFilesDirectory(
            workspacesRoot = workspacesRoot,
            root = rootDocumentId,
        ) ?: error("无效的工作区标识")

        val stagingDir = File(
            context.cacheDir,
            "workspace_import_${System.currentTimeMillis()}",
        )
        try {
            stagingDir.mkdirs()
            val importedFiles = copyChildren(
                resolver = context.contentResolver,
                treeUri = treeUri,
                parentDocumentId = rootDocumentId,
                targetDir = stagingDir,
            )

            val conflict = stagingDir.walkTopDown()
                .drop(1)
                .firstOrNull { staged ->
                    val relative = staged.relativeTo(stagingDir)
                    File(targetFilesDir, relative.path).exists()
                }
            require(conflict == null) {
                "目标工作区已有同名内容：${conflict?.relativeTo(stagingDir)?.invariantSeparatorsPath}"
            }

            stagingDir.walkTopDown().drop(1).forEach { staged ->
                val target = File(targetFilesDir, staged.relativeTo(stagingDir).path)
                if (staged.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    staged.copyTo(target, overwrite = false)
                }
            }
            targetFilesDir.mkdirs()

            WorkspaceImportResult(
                root = rootDocumentId,
                importedFiles = importedFiles,
            )
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    private fun copyChildren(
        resolver: ContentResolver,
        treeUri: Uri,
        parentDocumentId: String,
        targetDir: File,
    ): Int {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val children = mutableListOf<SourceDocument>()
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                children += SourceDocument(
                    id = cursor.getString(idColumn),
                    name = cursor.getString(nameColumn),
                    mimeType = cursor.getString(mimeColumn),
                )
            }
        } ?: error("无法读取所选工作区")

        var importedFiles = 0
        children.forEach { child ->
            val target = resolveChild(targetDir, child.name)
            if (child.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                target.mkdirs()
                importedFiles += copyChildren(
                    resolver = resolver,
                    treeUri = treeUri,
                    parentDocumentId = child.id,
                    targetDir = target,
                )
            } else {
                target.parentFile?.mkdirs()
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, child.id)
                resolver.openInputStream(documentUri)?.use { input ->
                    FileOutputStream(target).use { output -> input.copyTo(output) }
                } ?: error("无法读取工作区文件：${child.name}")
                importedFiles++
            }
        }
        return importedFiles
    }

    private fun resolveChild(parent: File, name: String): File {
        require(name.isNotBlank() && name != "." && name != "..") { "无效文件名" }
        val canonicalParent = parent.canonicalFile
        val child = File(canonicalParent, name).canonicalFile
        require(child.parentFile == canonicalParent) { "文件名越出工作区：$name" }
        return child
    }

    private data class SourceDocument(
        val id: String,
        val name: String,
        val mimeType: String,
    )
}

data class WorkspaceImportResult(
    val root: String,
    val importedFiles: Int,
    val workspaceName: String? = null,
)
