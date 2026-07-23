/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.plugin.scanner

import java.io.File

internal object PluginStoragePaths {
    fun resolve(
        sharedStorageRoot: File,
    ): File = File(sharedStorageRoot, PluginScanner.PLUGINS_DIR)
}
