/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.ai.tools
 
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.utils.generateUnifiedDiff
import me.rerere.workspace.WorkspaceCommandResult
import me.rerere.workspace.WorkspaceFileEntry
import me.rerere.workspace.WorkspaceManager
import me.rerere.workspace.WorkspaceStorageArea
import org.koin.java.KoinJavaComponent.getKoin
import java.io.ByteArrayOutputStream
 
private const val SHELL_TIMEOUT_MAX_SECONDS = 600L
private const val MAX_READ_FILE_BYTES = 8L * 1024 * 1024
private const val DEFAULT_READ_MAX_LINES = 200
private const val MAX_READ_MAX_LINES = 400
private const val MAX_READ_RESULT_BYTES = 24 * 1024
private const val MIN_BASE64_FRAGMENT_LENGTH = 64
private const val MIN_BASE64_RUN_BYTES = 512
 
val WorkspaceToolDefaultApprovals: Map<String, Boolean> = mapOf(
    "workspace_read_file" to false,
    "workspace_write_file" to false,
    "workspace_edit_file" to false,
    "workspace_shell" to true,
)
 
fun resolveWorkspaceToolApproval(name: String, overrides: Map<String, Boolean>): Boolean =
    overrides[name] ?: WorkspaceToolDefaultApprovals[name] ?: false
 
suspend fun createWorkspaceTools(
    workspaceId: String?,
    workspaceRepository: WorkspaceRepository,
    cwd: String? = null,
): List<Tool> {
    if (workspaceId.isNullOrBlank()) return emptyList()
    val approvalOverrides = workspaceRepository.getById(workspaceId)?.toolApprovalOverrides().orEmpty()
    fun needsApproval(name: String) = resolveWorkspaceToolApproval(name, approvalOverrides)
 
    val shellCwd = cwd?.removePrefix("/workspace/")?.removePrefix("/workspace")
 
    return listOf(
        createReadFileTool(workspaceId, needsApproval("workspace_read_file"), workspaceRepository),
        createWriteFileTool(workspaceId, needsApproval("workspace_write_file"), workspaceRepository),
        createEditFileTool(workspaceId, needsApproval("workspace_edit_file"), workspaceRepository),
        createShellTool(workspaceId, needsApproval("workspace_shell"), workspaceRepository, shellCwd),
    )
}
 
private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg")
 
private fun String.isImagePath(): Boolean =
    substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS
 
private fun createReadFileTool(
    workspaceId: String,
    needsApproval: Boolean,
    workspaceRepository: WorkspaceRepository,
) = Tool(
    name = "workspace_read_file",
    description = """
        Read a file using the assistant's bound workspace Rootfs. Paths must be absolute inside Rootfs.
        Use /workspace for the workspace files area.
        Supports UTF-8 text files and image files (png, jpg, jpeg, gif, webp, bmp).
        Text is returned in pages: by default 200 lines and at most 24 KiB. Use start_line and max_lines to read another page.
        Large encoded payloads are withheld; use the reported next_start_line to continue after them.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putPathProperty(required = true)
                put("start_line", buildJsonObject {
                    put("type", "integer")
                    put("description", "1-based first line to read. Defaults to 1.")
                })
                put("max_lines", buildJsonObject {
                    put("type", "integer")
                    put("description", "Maximum lines to return. Defaults to $DEFAULT_READ_MAX_LINES, max $MAX_READ_MAX_LINES.")
                })
            },
            required = listOf("path"),
        )
    },
    needsApproval = needsApproval,
    execute = {
        val path = it.jsonObject.absolutePath("path")
        if (path.isImagePath()) {
            workspaceRepository.readImageInRootfs(workspaceId, path)
        } else {
            val text = workspaceRepository.readTextInRootfs(workspaceId, path)
            val startLine = it.jsonObject.positiveInt("start_line") ?: 1
            val maxLines = (it.jsonObject.positiveInt("max_lines") ?: DEFAULT_READ_MAX_LINES)
                .coerceAtMost(MAX_READ_MAX_LINES)
            val page = text.toSafeReadPage(startLine, maxLines)
            listOf(
                UIMessagePart.Text(
                    buildJsonObject {
                        put("path", path)
                        put("start_line", page.startLine)
                        put("end_line", page.endLine)
                        put("total_lines", page.totalLines)
                        put("text", page.text)
                        if (page.truncated) put("truncated", true)
                        page.nextStartLine?.let { put("next_start_line", it) }
                        page.withheldEncodedPayload?.let { range ->
                            put("withheld_encoded_payload", true)
                            put("withheld_start_line", range.first)
                            put("withheld_end_line", range.last)
                            put("guidance", "A large encoded payload was withheld to keep the chat context small. Continue with start_line=${range.last + 1}, or run the program instead of reading its engine data.")
                        }
                    }.toString()
                )
            )
        }
    },
)
 
private fun createWriteFileTool(
    workspaceId: String,
    needsApproval: Boolean,
    workspaceRepository: WorkspaceRepository,
) = Tool(
    name = "workspace_write_file",
    description = """
        Write a UTF-8 text file using the assistant's bound workspace Rootfs. Paths must be absolute inside Rootfs.
        Use /workspace for the workspace files area.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putPathProperty(required = true)
                put("text", buildJsonObject {
                    put("type", "string")
                    put("description", "UTF-8 text content to write")
                })
                put("overwrite", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Whether to overwrite an existing file. Defaults to true.")
                })
            },
            required = listOf("path", "text"),
        )
    },
    needsApproval = needsApproval,
    execute = {
        val params = it.jsonObject
        val path = params.absolutePath("path")
        val text = params.string("text") ?: error("text is required")
        val overwrite = params["overwrite"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: true
        val entry = workspaceRepository.writeTextInRootfs(workspaceId, path, text, overwrite)
        listOf(UIMessagePart.Text(entry.toJson().toString()))
    },
)
 
private fun createEditFileTool(
    workspaceId: String,
    needsApproval: Boolean,
    workspaceRepository: WorkspaceRepository,
) = Tool(
    name = "workspace_edit_file",
    description = """
        Edit a UTF-8 text file using the assistant's bound workspace Rootfs. Paths must be absolute inside Rootfs.
        Use /workspace for the workspace files area.
        Provide old_text and new_text. By default old_text must occur exactly once; set replace_all=true to replace every occurrence.
        If no exact match is found, whitespace-tolerant line matching is attempted automatically.
    """.trimIndent().replace("\n", " "),
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                putPathProperty(required = true)
                put("old_text", buildJsonObject {
                    put("type", "string")
                    put("description", "Exact text to replace")
                })
                put("new_text", buildJsonObject {
                    put("type", "string")
                    put("description", "Replacement text")
                })
                put("replace_all", buildJsonObject {
                    put("type", "boolean")
                    put("description", "Whether to replace every occurrence. Defaults to false.")
                })
            },
            required = listOf("path", "old_text", "new_text"),
        )
    },
    needsApproval = needsApproval,
    execute = {
        val params = it.jsonObject
        val path = params.absolutePath("path")
        val oldText = params.string("old_text") ?: error("old_text is required")
        val newText = params.string("new_text") ?: error("new_text is required")
        val replaceAll = params["replace_all"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
        require(oldText.isNotEmpty()) { "old_text must not be empty" }
 
        val original = workspaceRepository.readTextInRootfs(workspaceId, path)
        // 逐级尝试 exact -> line_trimmed -> block_anchor 替换器, 见 TextReplacers.kt
        val result = try {
            replaceText(original, oldText, newText, replaceAll)
        } catch (e: IllegalArgumentException) {
            error("${e.message} (path: $path)")
        }
        val entry = workspaceRepository.writeTextInRootfs(workspaceId, path, result.updated, overwrite = true)
        val diff = generateUnifiedDiff(original, result.updated, entry.path)
        listOf(
            UIMessagePart.Text(
                text = buildJsonObject {
                    put("path", entry.path)
                    put("replacements", result.replacements)
                    if (result.strategy != ExactReplacer.name) put("matchStrategy", result.strategy)
                    put("sizeBytes", entry.sizeBytes)
                    put("updatedAt", entry.updatedAt)
                }.toString(),
            ).apply {
                // diff 存入 metadata 供 UI 渲染 diff view, 不会随工具结果发送给 API
                if (diff != null) {
                    metadata = buildJsonObject { put("diff", diff) }
                }
            }
        )
    },
)
 
private fun createShellTool(
    workspaceId: String,
    needsApproval: Boolean,
    workspaceRepository: WorkspaceRepository,
    defaultCwd: String? = null,
) = Tool(
    name = "workspace_shell",
    description = buildString {
        append("Run a shell command in the assistant's bound workspace Rootfs. The workspace files area is mounted at /workspace. ")
        append("Use cwd for a path relative to the workspace files root. ")
        if (!defaultCwd.isNullOrBlank()) {
            append("Defaults to '$defaultCwd'. ")
        }
        append("Requires Rootfs to be installed and ready.")
    },
    parameters = {
        InputSchema.Obj(
            properties = buildJsonObject {
                put("command", buildJsonObject {
                    put("type", "string")
                    put("description", "Shell command to run")
                })
                put("cwd", buildJsonObject {
                    put("type", "string")
                    put(
                        "description",
                        if (!defaultCwd.isNullOrBlank()) {
                            "Working directory relative to the workspace files root. Defaults to '$defaultCwd'."
                        } else {
                            "Working directory relative to the workspace files root. Defaults to root."
                        }
                    )
                })
                put("timeout", buildJsonObject {
                    put("type", "integer")
                    put(
                        "description",
                        "Command timeout in seconds. Defaults to 30, max $SHELL_TIMEOUT_MAX_SECONDS."
                    )
                })
            },
            required = listOf("command"),
        )
    },
    needsApproval = needsApproval,
    execute = {
        val params = it.jsonObject
        val command = params.string("command") ?: error("command is required")
        val cwd = (params.string("cwd") ?: defaultCwd.orEmpty())
            .removePrefix("/workspace/").removePrefix("/workspace")
        val timeoutMillis = params.string("timeout")?.toLongOrNull()
            ?.coerceIn(1L, SHELL_TIMEOUT_MAX_SECONDS)
            ?.times(1_000L)
            ?: WorkspaceManager.DEFAULT_COMMAND_TIMEOUT_MS
        val result = workspaceRepository.executeCommand(workspaceId, command, cwd, timeoutMillis)
        listOf(
            UIMessagePart.Text(
                buildJsonObject {
                    put("exitCode", result.exitCode)
                    put("stdout", result.stdout)
                    put("stderr", result.stderr)
                    put("timedOut", result.timedOut)
                    if (result.truncated) put("truncated", true)
                }.toString()
            )
        )
    },
)
 
private fun kotlinx.serialization.json.JsonObject.string(name: String): String? =
    this[name]?.jsonPrimitive?.contentOrNull

private fun kotlinx.serialization.json.JsonObject.positiveInt(name: String): Int? {
    val value = string(name) ?: return null
    return value.toIntOrNull()?.also { require(it > 0) { "$name must be a positive integer" } }
}

internal data class WorkspaceReadPage(
    val startLine: Int,
    val endLine: Int,
    val totalLines: Int,
    val text: String,
    val truncated: Boolean,
    val nextStartLine: Int?,
    val withheldEncodedPayload: IntRange?,
)

internal fun String.toSafeReadPage(startLine: Int, maxLines: Int): WorkspaceReadPage {
    val lines = lines()
    val totalLines = lines.size
    if (startLine > totalLines) {
        return WorkspaceReadPage(startLine, startLine - 1, totalLines, "", false, null, null)
    }

    val encodedRanges = lines.encodedPayloadRanges()
    val firstIndex = startLine - 1
    val requestedLastIndex = minOf(firstIndex + maxLines - 1, totalLines - 1)
    val blockedRange = encodedRanges.firstOrNull { it.first - 1 <= requestedLastIndex && it.last - 1 >= firstIndex }
    val contentLastIndex = when {
        blockedRange == null -> requestedLastIndex
        blockedRange.first - 1 > firstIndex -> blockedRange.first - 2
        else -> firstIndex - 1
    }

    val selected = mutableListOf<String>()
    var resultBytes = 0
    var byteLimited = false
    for (index in firstIndex..contentLastIndex) {
        val line = lines[index]
        val lineBytes = line.toByteArray(Charsets.UTF_8).size + if (selected.isEmpty()) 0 else 1
        if (resultBytes + lineBytes > MAX_READ_RESULT_BYTES) {
            byteLimited = true
            break
        }
        selected += line
        resultBytes += lineBytes
    }

    val returnedLastIndex = firstIndex + selected.size - 1
    val withheldRange = if (byteLimited) null else blockedRange
    val nextStartLine = when {
        byteLimited -> returnedLastIndex + 2
        blockedRange != null -> blockedRange.last + 1
        returnedLastIndex < totalLines - 1 -> returnedLastIndex + 2
        else -> null
    }
    return WorkspaceReadPage(
        startLine = startLine,
        endLine = if (selected.isEmpty()) startLine - 1 else returnedLastIndex + 1,
        totalLines = totalLines,
        text = selected.joinToString("\n"),
        truncated = nextStartLine != null,
        nextStartLine = nextStartLine,
        withheldEncodedPayload = withheldRange,
    )
}

private fun List<String>.encodedPayloadRanges(): List<IntRange> {
    val ranges = mutableListOf<IntRange>()
    var index = 0
    while (index < size) {
        if (!this[index].isBase64Fragment()) {
            index += 1
            continue
        }
        val start = index
        var bytes = 0
        while (index < size && this[index].isBase64Fragment()) {
            bytes += this[index].base64FragmentLength()
            index += 1
        }
        val length = index - start
        if (bytes >= MIN_BASE64_RUN_BYTES || (length == 1 && bytes >= MAX_READ_RESULT_BYTES)) {
            ranges += (start + 1)..index
        }
    }
    return ranges
}

private fun String.isBase64Fragment(): Boolean = base64FragmentLength() >= MIN_BASE64_FRAGMENT_LENGTH

private fun String.base64FragmentLength(): Int {
    val payload = trim().trim('"', '\'', ',', '(', ')').trim()
    if (payload.length < MIN_BASE64_FRAGMENT_LENGTH) return 0
    if (!payload.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }) return 0
    return payload.length
}
 
private suspend fun WorkspaceRepository.readTextInRootfs(
    workspaceId: String,
    path: String,
): String {
    val (area, relativePath) = rootfsPathToAreaAndRelative(path)
    val size = fileSize(workspaceId, area, relativePath)
    require(size <= MAX_READ_FILE_BYTES) {
        "File is too large to read: $path (${size / 1024 / 1024}MB, max ${MAX_READ_FILE_BYTES / 1024 / 1024}MB). Use shell commands like head, tail, or grep to read parts of it."
    }
    val buffer = ByteArrayOutputStream(size.toInt())
    exportFile(workspaceId, area, relativePath, buffer)
    return buffer.toString(Charsets.UTF_8.name())
}
 
private fun rootfsPathToAreaAndRelative(path: String): Pair<WorkspaceStorageArea, String> {
    val trimmed = path.trimEnd('/')
    return if (trimmed == "/workspace" || trimmed.startsWith("/workspace/")) {
        WorkspaceStorageArea.FILES to trimmed.removePrefix("/workspace").trimStart('/')
    } else {
        WorkspaceStorageArea.LINUX to trimmed.trimStart('/')
    }
}
 
private suspend fun WorkspaceRepository.readImageInRootfs(
    workspaceId: String,
    path: String,
): List<UIMessagePart> {
    val (area, relativePath) = rootfsPathToAreaAndRelative(path)
    val buffer = ByteArrayOutputStream()
    exportFile(workspaceId, area, relativePath, buffer)
    val bytes = buffer.toByteArray()
 
    val filesManager = getKoin().get<FilesManager>()
    val uris = filesManager.createChatFilesByByteArrays(listOf(bytes))
    return listOf(
        UIMessagePart.Image(url = uris.first().toString()),
        UIMessagePart.Text(
            buildJsonObject {
                put("path", path)
                put("description", "Image file read successfully")
            }.toString()
        ),
    )
}
 
private suspend fun WorkspaceRepository.writeTextInRootfs(
    workspaceId: String,
    path: String,
    text: String,
    overwrite: Boolean,
): WorkspaceFileEntry {
    val pathArg = path.shellQuote()
    val result = runRootfsCommand(
        workspaceId = workspaceId,
        action = "Write file",
        command = """
            if [ -e $pathArg ] && [ ${(!overwrite).shellFlag()} = 1 ]; then
              printf '%s\n' ${"File already exists: $path".shellQuote()} >&2
              exit 1
            fi
            if [ -e $pathArg ] && [ ! -f $pathArg ]; then
              printf '%s\n' ${"Path is not a file: $path".shellQuote()} >&2
              exit 1
            fi
            parent=${'$'}(dirname -- $pathArg) || exit 1
            mkdir -p -- "${'$'}parent" || exit 1
            cat > $pathArg || exit 1
            ${statEntryCommand(path)}
        """.trimIndent(),
        stdin = text.toByteArray(Charsets.UTF_8),
    )
    return result.stdout.parseRootfsEntry()
}
 
private suspend fun WorkspaceRepository.runRootfsCommand(
    workspaceId: String,
    action: String,
    command: String,
    stdin: ByteArray? = null,
): WorkspaceCommandResult {
    val result = executeCommand(
        id = workspaceId,
        command = command,
        timeoutMillis = WorkspaceManager.DEFAULT_COMMAND_TIMEOUT_MS,
        stdin = stdin,
    )
    if (result.timedOut) {
        error("$action timed out")
    }
    if (result.exitCode != 0) {
        val message = result.stderr.ifBlank { result.stdout }.trim()
        error(if (message.isBlank()) "$action failed with exit code ${result.exitCode}" else message)
    }
    if (result.truncated) {
        error("$action output is too large")
    }
    return result
}
 
private fun statEntryCommand(path: String): String {
    val pathArg = path.shellQuote()
    return """
        if [ -d $pathArg ]; then entry_type=d; else entry_type=f; fi
        entry_size=${'$'}(stat -c '%s' -- $pathArg) || exit 1
        entry_mtime=${'$'}(stat -c '%Y' -- $pathArg) || exit 1
        printf '%s\0%s\0%s\0%s\0' "${'$'}entry_type" "${'$'}entry_size" "${'$'}entry_mtime" $pathArg
    """.trimIndent()
}
 
private fun String.parseRootfsEntry(): WorkspaceFileEntry =
    parseRootfsEntries().singleOrNull() ?: error("Invalid file metadata output")
 
private fun String.parseRootfsEntries(): List<WorkspaceFileEntry> {
    val fields = split('\u0000').dropLastWhile { it.isEmpty() }
    require(fields.size % 4 == 0) { "Invalid file metadata output" }
    return fields.chunked(4).map { chunk ->
        val type = chunk[0]
        val size = chunk[1].toLongOrNull() ?: error("Invalid file size: ${chunk[1]}")
        val updatedAt = (chunk[2].toLongOrNull() ?: error("Invalid file mtime: ${chunk[2]}")) * 1_000L
        val path = chunk[3]
        WorkspaceFileEntry(
            path = path,
            name = path.rootfsName(),
            isDirectory = type == "d",
            sizeBytes = size,
            updatedAt = updatedAt,
        )
    }
}
 
private fun kotlinx.serialization.json.JsonObject.absolutePath(name: String): String {
    val path = string(name)?.replace('\\', '/')?.trim() ?: error("$name is required")
    require(path.isNotBlank()) { "$name is required" }
    require(path.startsWith("/")) { "$name must be an absolute path inside Rootfs" }
    require(!path.contains('\u0000')) { "$name contains invalid character" }
    return path
}
 
private fun String.rootfsName(): String =
    trimEnd('/').substringAfterLast('/').ifBlank { "/" }
 
private fun String.shellQuote(): String =
    "'" + replace("'", "'\"'\"'") + "'"
 
private fun Boolean.shellFlag(): Int = if (this) 1 else 0
 
private fun JsonObjectBuilder.putPathProperty(required: Boolean) {
    put("path", buildJsonObject {
        put("type", "string")
        put(
            "description",
            if (required) {
                "Absolute path inside Rootfs. Use /workspace for the workspace files area."
            } else {
                "Optional absolute path inside Rootfs. Use /workspace for the workspace files area."
            }
        )
    })
}
 
private fun WorkspaceFileEntry.toJson() = buildJsonObject {
    put("path", path)
    put("name", name)
    put("isDirectory", isDirectory)
    put("sizeBytes", sizeBytes)
    put("updatedAt", updatedAt)
}
