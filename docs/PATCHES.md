# 宿主长期补丁清单

> 2026-07-22 maintenance: build and debug-install helpers now resolve Java/ADB from the current environment instead of old machine-specific paths. `install_qq_debug.bat` only uses `adb install -r`, so it preserves local debug-app data. Removed tracked one-off compiler logs, truncated command-output files, and obsolete path-specific helper scripts. Validation: static script checks and Python syntax parsing; no APK build, device installation, or release action was run.

本文记录我们相对 OrangeChat 官方供体需要长期保留的行为差异。它不是聊天记录，
也不复制 Git 日志；每项只说明为什么保留、当前实现、验证状态和对应 commit。

官方基线：`v2.2.3@d1aad52f4e3aae0857b8f05ef46769a6de53c7d0`。

## 已实现

### 工作区文件读取护栏

- 目的：避免模型误读超大源码或 Base64 引擎正文，持续污染后续上下文和 token 用量。
- 行为：`workspace_read_file` 默认最多返回 200 行且不超过 24 KiB，支持显式翻页；
  连续的大段 Base64 载荷被跳过并给出续读位置。
- 验证：4 项针对性单元测试和完整 debug 构建通过；尚未完成真实工作区真机回归。
- commit：`1efca914 feat: guard workspace file reads`。

### 旧备份冷启动恢复与 debug 后台隔离

- 目的：兼容 2.2.2 的 Room v28 备份，避免恢复到 v29 后缺少
  `security_audit_logs`；避免测试包导入生产设置后启动第二套后台自动化。
- 行为：数据库先暂存，在 Room 初始化前替换并执行 28→29 migration；恢复任务由
  ViewModel 持有，导出前执行 WAL checkpoint；debug 变体默认关闭后台自动启动项。
- 验证：2 项数据库替换测试、工作区 4 项测试和完整 debug 构建通过；仍待真机验证
  设置、聊天、文件、skills、插件和插件设置恢复。
- commit：`544b32c7 fix: restore older backups before Room starts`。

## 待实现

### Rism 外置记忆召回反馈

- 目标：宿主保留最终 top-N 的 `source_message_id`，成功注入上下文后对源记忆执行
  同轮去重的轻量 heat 反馈；失败不影响聊天。
- 宿主范围：`ExternalMemorySummary` 来源字段、`GenerationHandler` 召回结果保留、
  反馈 RPC、失败降级、门控设置与备份兼容。
- 主计划：`orangecat-personal-addons/docs/plans/memory-recall.md`。
- 状态：尚未实现，不等待官方供体先增加回调。

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
