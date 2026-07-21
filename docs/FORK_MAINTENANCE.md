# OrangeChat 宿主 fork 维护说明

本文只描述宿主源码如何长期维护。Rism 的功能目标、插件、Supabase、VPS worker、
跨仓库计划和总体路线图位于 `orangecat-personal-addons`；当前本地入口为
`C:\Users\youzi\orangechat-rism\README.md`。

## 当前状态

- 官方基线：OrangeChat `v2.2.3`，commit
  `d1aad52f4e3aae0857b8f05ef46769a6de53c7d0`；
- 当前本地分支：`codex/workspace-guardrails`；
- 已完成本地补丁：工作区读取护栏、旧备份冷启动恢复、debug 后台隔离；
- 个人 GitHub fork 尚未建立，`origin` 仍指向官方供体；
- 当前改动只在本地 commit 中保存，尚未推送或发布；
- 单元测试与完整 debug 构建通过，旧备份恢复仍待真机复验。

## 仓库边界

本仓库负责：

- Android、Kotlin、Jetpack Compose 与 Room；
- Assistant 上下文组装、宿主工具和 ExternalMemory；
- 本地数据库、备份导入导出与版本迁移；
- debug/release 变体、构建、测试和 APK；
- 相对官方供体长期保留的宿主补丁。

本仓库不维护：

- Rism 人格、世界书和功能目标；
- QuickJS 插件与 Supabase schema；
- VPS worker、跨端记忆与跨仓库 Roadmap。

跨仓库功能在 `orangecat-personal-addons/docs/plans/` 保留一份主计划，本仓库仅在
[`PATCHES.md`](PATCHES.md)记录宿主实现、测试和对应 commit。

## 目标 remote 结构

建立个人 GitHub fork 后，目标关系是：

```text
origin    → toge 的 OrangeChat fork
upstream  → sue1231513/orangechat
```

在 remote 尚未调整前：

- 不运行 `git push`；
- 不把 `origin` 当作个人仓库；
- 不自动合并官方 HEAD；
- 官方变化先审阅，再决定 merge、rebase 或 cherry-pick；
- 不允许官方更新覆盖 [`PATCHES.md`](PATCHES.md) 中仍需保留的行为。

remote 调整与首次推送属于外部状态变更，必须由 toge 明确授权后执行。

## 分支与提交

- 稳定 fork 建立前继续使用范围明确的 `codex/*` 功能分支；
- 一个 commit 只完成一个可验证的宿主变化；
- 不把 APK、构建目录、聊天记录、备份或凭据纳入 commit；
- 功能通过测试后更新 `PATCHES.md`；跨仓库功能同时更新 Rism 主计划与迭代日志；
- 本地 commit 不等于推送、发布或安装，交付时分别报告这些状态。

## Room 与备份兼容

- 修改 schema 必须提高数据库版本并提供明确 migration；
- 至少覆盖“上一正式版本 → 当前版本”的迁移测试；
- 旧备份数据库先暂存，在下次冷启动且 Room 初始化前替换；
- 不得在应用运行中直接覆盖已打开的主数据库；
- 替换前保留回滚副本，缺少或损坏主数据库时快速失败；
- 导出数据库前执行 WAL checkpoint，恢复时检查主库及相关文件；
- Compose 页面重组不能取消恢复任务，长任务由 ViewModel 或更稳定的作用域持有；
- 真机必须分别验证设置、聊天、上传文件、skills、插件、插件设置与新表迁移。

## 测试变体隔离

debug 测试包用于导入真实备份和验证宿主改动，因此必须避免成为第二套正式实例：

- application ID 与正式包不同；
- 桌面名称明确标记为测试版；
- 默认不自动启动 Web 服务、主动消息、Supabase 同步、设备事件、工作流、
  App Lock、激进模式和插件日程；
- 手动聊天、设置检查与当前待测工具仍可使用；
- 不自动卸载或覆盖正式橘瓣。

## 验证顺序

1. 运行与本次改动直接相关的单元测试；
2. 运行 `./gradlew test` 或风险相称的模块测试；
3. 运行 `./gradlew assembleDebug`；
4. 核对 application ID、桌面名称、APK 路径和 SHA-256；
5. 涉及数据库、备份、通知、后台服务或 UI 时执行真机验收；
6. 更新 `PATCHES.md`、跨仓库计划和迭代日志后再创建本地 commit。

Windows 当前可使用：

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
```

权威构建输出位于 `app/build/outputs/apk/`。仓库根目录中人工命名的 APK 可能早于
最新 commit，未重新核对哈希前不得交付或安装。

## 完成定义

一项宿主修改只有同时满足以下条件才算完成：

- 代码与测试通过；
- 风险需要真机时已经真机验证，或明确标记仍待验证；
- `PATCHES.md` 记录长期行为和对应 commit；
- 跨仓库主计划、Roadmap 与迭代日志状态一致；
- 已创建范围明确的本地 commit，并准确报告是否推送、发布与安装。
