/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.plugin.scanner

import java.io.File

internal object PluginStoragePaths {
    private const val ISOLATED_PLUGINS_DIR = "plugins"

    fun resolve(
        appFilesDir: File,
        sharedStorageRoot: File,
        useIsolatedStorage: Boolean,
    ): File {
        return if (useIsolatedStorage) {
            File(appFilesDir, ISOLATED_PLUGINS_DIR)
        } else {
            File(sharedStorageRoot, PluginScanner.PLUGINS_DIR)
        }
    }
}
