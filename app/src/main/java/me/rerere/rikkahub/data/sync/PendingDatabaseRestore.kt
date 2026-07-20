/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.sync

import android.content.Context
import java.io.File

/**
 * 数据库恢复必须发生在 Room 首次打开数据库之前。
 *
 * 备份导入时只把数据库文件放进待恢复目录；应用下次冷启动时，
 * [applyIfReady] 会先替换数据库，再让 Room 按 user_version 执行迁移。
 */
object PendingDatabaseRestore {
    private const val PENDING_DIR = "pending_database_restore"
    private const val READY_MARKER = "READY"
    private const val DATABASE_NAME = "rikka_hub"
    private val backupNames = listOf("rikka_hub.db", "rikka_hub-wal", "rikka_hub-shm")

    fun createStagingDir(context: Context): File =
        File(context.cacheDir, "database_restore_staging_${System.currentTimeMillis()}").apply {
            check(mkdirs()) { "Unable to create database restore staging directory" }
        }

    fun commit(context: Context, stagingDir: File) {
        require(File(stagingDir, "rikka_hub.db").isFile) {
            "Backup does not contain rikka_hub.db"
        }
        val pendingDir = File(context.noBackupFilesDir, PENDING_DIR)
        val replacementDir = File(context.noBackupFilesDir, "$PENDING_DIR.new")
        replacementDir.deleteRecursively()
        check(stagingDir.copyRecursively(replacementDir, overwrite = true)) {
            "Unable to stage database restore"
        }
        File(replacementDir, READY_MARKER).writeText("ready")

        pendingDir.deleteRecursively()
        if (!replacementDir.renameTo(pendingDir)) {
            check(replacementDir.copyRecursively(pendingDir, overwrite = true)) {
                "Unable to commit database restore"
            }
            replacementDir.deleteRecursively()
        }
    }

    fun applyIfReady(context: Context): Boolean {
        val pendingDir = File(context.noBackupFilesDir, PENDING_DIR)
        if (!File(pendingDir, READY_MARKER).isFile) return false

        replaceDatabaseFiles(
            databaseFile = context.getDatabasePath(DATABASE_NAME),
            pendingDir = pendingDir,
        )
        pendingDir.deleteRecursively()
        return true
    }

    internal fun replaceDatabaseFiles(databaseFile: File, pendingDir: File) {
        require(File(pendingDir, "rikka_hub.db").isFile) {
            "Pending restore is missing rikka_hub.db"
        }
        databaseFile.parentFile?.mkdirs()
        val liveFiles = listOf(
            databaseFile,
            File(databaseFile.parentFile, "$DATABASE_NAME-wal"),
            File(databaseFile.parentFile, "$DATABASE_NAME-shm"),
        )
        val rollbackDir = File(databaseFile.parentFile, "$DATABASE_NAME.restore_rollback")
        rollbackDir.deleteRecursively()
        check(rollbackDir.mkdirs()) { "Unable to create database rollback directory" }

        try {
            liveFiles.forEach { live ->
                if (live.isFile) live.copyTo(File(rollbackDir, live.name), overwrite = true)
            }
            liveFiles.forEach { live ->
                check(!live.exists() || live.delete()) { "Unable to replace ${live.name}" }
            }
            backupNames.forEach { backupName ->
                val staged = File(pendingDir, backupName)
                if (staged.isFile) {
                    val liveName = if (backupName == "rikka_hub.db") DATABASE_NAME else backupName
                    staged.copyTo(File(databaseFile.parentFile, liveName), overwrite = true)
                }
            }
        } catch (error: Throwable) {
            liveFiles.forEach { it.delete() }
            liveFiles.forEach { live ->
                val rollback = File(rollbackDir, live.name)
                if (rollback.isFile) rollback.copyTo(live, overwrite = true)
            }
            throw error
        } finally {
            rollbackDir.deleteRecursively()
        }
    }
}
