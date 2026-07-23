# 宿主长期补丁清单

> 2026-07-22 maintenance: build and debug-install helpers now resolve Java/ADB from the current environment instead of old machine-specific paths. `install_qq_debug.bat` only uses `adb install -r`, so it preserves local debug-app data. Removed tracked one-off compiler logs, truncated command-output files, and obsolete path-specific helper scripts. Validation: static script checks and Python syntax parsing; no APK build, device installation, or release action was run.

本文记录我们相对 OrangeChat 官方供体需要长期保留的行为差异。它不是聊天记录，
也不复制 Git 日志；每项只说明为什么保留、当前实现、验证状态和对应 commit。

官方基线：`v2.2.3@d1aad52f4e3aae0857b8f05ef46769a6de53c7d0`。

## 已实现

### Rism 外置记忆召回反馈

- 目的：宿主保留最终 top-N 的 `source_message_id`，在记忆真正进入本轮最终模型
  上下文后给予轻量 heat 反馈，让持续被想起的主题逐渐升温。
- 行为：`ExternalMemorySummary` 按 `Long` 解析来源 ID；只有没有待续工具调用的最终
  生成步骤才提交反馈，自动工具续跑与人工批准暂停不会重复累计；同一记忆库内相同来源
  同轮去重。反馈通过 `AppScope` 异步调用固定 `+0.25` 的专用 RPC，失败只写简短日志，
  不阻塞聊天、不记录记忆正文；手动重新生成属于新一轮。
- 验证：来源 ID、固定请求体与同轮去重 3 项针对性单元测试通过，完整 debug APK
  构建通过；仍待执行线上 RPC schema 并真机验证 heat 变化、失败降级和重新生成语义。
- commit：`e29238b0 feat: warm recalled Rism memories`。

### 工作区文件读取护栏

- 目的：避免模型误读超大源码或 Base64 引擎正文，持续污染后续上下文和 token 用量。
- 行为：`workspace_read_file` 默认最多返回 200 行且不超过 24 KiB，支持显式翻页；
  连续的大段 Base64 载荷被跳过并给出续读位置。
- 验证：4 项针对性单元测试和完整 debug 构建通过；尚未完成真实工作区真机回归。
- commit：`1efca914 feat: guard workspace file reads`。

### 旧备份冷启动恢复、工作区迁移与 debug 后台隔离

- 目的：兼容 2.2.2 的 Room v28 备份，避免恢复到 v29 后缺少
  `security_audit_logs`；避免测试包导入生产设置后启动第二套后台自动化或因公共插件
  目录无权限而中止聊天数据库恢复。
- 行为：数据库先暂存，在 Room 初始化前替换并执行 28→29 migration；恢复任务由
  ViewModel 持有，导出前执行 WAL checkpoint；debug 变体默认关闭后台自动启动项，
  正式版与 debug 版继续共用 `/storage/emulated/0/Orangechat/plugins`，便于并存包自动读取
  同一套插件；未授予“所有文件访问”时，恢复会跳过插件文件但继续恢复聊天数据库，不再
  因 `EACCES` 中止。新备份包含各工作区的用户 `files/`，不包含可重建的 Linux rootfs
  和临时目录；旧正式版工作区可通过系统文件选择器逐个导入，导入时沿用来源 root 作为
  id/root 并自动补建 Room 记录，目录暂缺时不再删除数据库记录。debug 桌面入口和 SAF
  根目录使用独立名称“橘瓣·护栏测试版”，避免与正式版混淆。
- 验证：2 项数据库替换测试、1 项插件目录策略测试、1 项插件恢复权限策略测试、
  3 项工作区备份路径测试、3 项工作区导入登记测试、工作区读取护栏 4 项测试和完整
  debug 构建通过；2026-07-24 已在 `24129PN74C` 覆盖安装并完成真机验收，独立桌面名称、
  公共插件读取、旧备份恢复及 SAF 工作区对应均确认通过。
- commits：`544b32c7 fix: restore older backups before Room starts`、
  `29601167 fix: isolate debug plugin restore storage`、
  `8ff04cf7 fix: preserve workspaces across backup restore`、
  `76630521 fix: align debug plugins and workspace imports`。

## 待实现

### 工作区工具护栏后续

- 目标：限制 shell 输出、缩短模型可见错误，并阻止同轮无状态变化的重复调用。
- 状态：尚未实现；文件读取护栏不代表整个工作区防护已经完成。

## 上游同步检查

审阅官方变化时逐项确认：

- 官方是否已经等价实现本补丁；
- 实现是否与当前补丁冲突；
- 数据库版本与迁移路径是否变化；
- debug/release application ID 或后台启动条件是否变化；
- 相关单元测试和真机验收是否需要重跑。

只有确认官方实现等价并完成验证后，才能从本清单移除补丁。
