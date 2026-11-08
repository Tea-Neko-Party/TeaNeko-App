# v4.9.0-l 实施验证记录

原重构验证日期：2026/11/07。五个实施闭环完成，5/5；真实环境验收独立保留在[下一步](next-steps.md)。原需求与答复见[需求归档](../../plan/old/4.9.0-plugin-requirements.md)。

最新的 2026/11/08 注解扩展、配置职责和目录结构复核见[本轮验证](extension-structure.md)：1436 项完整隔离回归、18 组浏览器交互及新产物核对通过。以下保留前次结果及当时产物信息。

## 已完成范围

| 闭环 | 结果与关键证据 |
| --- | --- |
| P1 插件管理底座 | 沿用插件目录、SPI 和代次，补充依赖图、客户端提供者、持久意愿、影响预览、回执、失败重试及可重复生命周期。`PluginManagementTest` 覆盖原子文件保存、修订冲突、联动停用/恢复、失败清理、取消观察和最终关闭；连接与普通调用同时排空。 |
| P2 平台与通用业务 | general、OneBot、Web 按职责分类。命令、事件、任务和插件阶段跟随真实完成阶段占用插件及其传递依赖；业务发送和事务提交前检查固定代次。普通查询、审核/成员事件及游戏倒计时覆盖停启后的迟到结果；官方协议与发送适配保留 Client。 |
| P3 认知与表达 | 业务组件、Store、配置、工具、模型辅助工作和管理页面整体迁入分类插件。运行骨架通过类型化端口调用；保留通用交付、模型供应商、凭据和数据库事务底座。记忆与跨聊天回忆合并，隐私、来源、真实送达与联合事务原合同有回归覆盖。 |
| P4 社交、自动化与升级 | 社交声明认知依赖，组件级装配独立判断。旧命令开关精确迁移为 command，关闭状态不新增能力。完整保存装配和有效运行投影分离；旧平铺配置、隐藏参数和秘密保留，自定义模型凭据路由与有效装配版本分别验证。 |
| P5 交付验证 | 统一前后端归属与筛选、插件总管理和构建期 Vue 页面扩展完成；开发指南、示例、升级说明与版本同步。隔离回归、生产目录装配 smoke、浏览器、JavaDoc、格式和实际产物检查通过。 |

## 实际结果

- JDK 25，Gradle 离线回归：191 个测试类、1417 项测试，失败 0、错误 0。按既有隔离方式排除 8 个需要真实 Spring/MySQL/模型/平台环境的测试类，不将它们计为通过。记录：`temp/4.9-offline-regression-final.log`、`temp/verification/4.9.0-l/test-result.json`。
- `InstalledPluginCatalogTest` 使用真实 ClassScanner/BeanScanner、模块、Feature 工厂与组件、System、配置、页面、工具、命令、任务声明，检查 16 个模块（含条件示例）、167 项贡献、9 类目录和停用后管理清单。业务依赖及持久/协调副作用隔离，未连接真实数据库。
- 前端 TypeScript 检查与生产构建通过；最新生产资源在 Chrome 的 16 组隔离交互通过，覆盖 ID 筛选、配置跳转、依赖预览、操作回执、页面撤除、弹窗清理、插件客户端秘密撤除、轮询失效、跨停启迟到响应、聊天范围精确匹配及系统模型不依赖插件目录。记录：`temp/verification/4.9.0-l/plugins-browser-result.json`、同目录截图。
- JavaDoc doclint 通过；新增 Java 改动检查通过，最新检查 546 个当前变更/新增文件。迁移文件通过独立临时 Git 索引识别重命名，不改真实暂存区、不将历史未改行算作新增格式。JavaDoc 输出位于 `temp/javadoc/4.9.0-l/`。
- 分层与插件边界测试包含在完整回归；正式源码、前端、文档和 README 的参考名称检查无匹配。完整差异执行 `git diff --check`。

## 追加反馈修正

2026/11/07 启动反馈修正：实际启动日志显示两个 `/agent` 子命令贡献因 `CommandExtension.value` 被 Spring 解释为同名 Bean 而冲突。现改为独立 `prefix` 属性，保留默认 Bean 命名和原命令路由。新增回归扫描全部生产组件，排除测试夹具，不实例化外部业务依赖；没有通过 Bean 覆盖或手工改名绕过冲突。

本次追加的命令、目录及分层定向回归为 4 个测试类、32 项测试，全部通过；记录为 `temp/4.9-startup-scan-regression.log` 与 `temp/verification/4.9.0-l/startup-fix-test-result.json`。JavaDoc 再次通过。此前 1417 项完整隔离结果保留为重构交付证据，不冒充本补丁后重复执行的全量结果。真实联网启动仍按环境清单安排。

同时清理迁移遗留的 45 个空包目录（生产源码 40 个、测试源码 5 个），未删除文件、配置或业务数据；逐目录检查的清单位于 `temp/verification/4.9.0-l/empty-source-directories-cleanup.json`。

2026/11/07 继续修正控制台反馈：

- 补充 `templates/config/plugins.yml`，默认 `version: 1`、`migrated: false`、`enabled: {}`。既有首次迁移测试通过，模板在源码、运行资源和 JAR 中逐字节一致，未覆盖本地生成配置。
- 补齐 `/api/v1/management-calls` 及插件管理固定 HTTP 端点的管理员安全路由；真实安全链回归覆盖读取、预览、变更和回执，以及匿名、缺失 CSRF、错误方法、未知子路径和资源白名单拒绝。没有把 HTTP 路由许可当作资源授权，也没有扩大既有本地白名单。
- 指令一览按各方法实际声明精确匹配聊天范围，去除类默认规则对方法覆写的错误匹配；“全部聊天范围”保持不过滤，“所有聊天”只显示通用范围声明。
- 模型配置作为系统内置文件表单，`pluginId` 为空，不依赖插件管理权限或业务插件启停。真实文件回归在管理资源未授权、四个 Agent 业务插件停用时完成读取、保存和应用，并验证凭据保护及 `agent.models` 文件权限撤销后的拒绝。

这批定向回归包含 5 个测试类、35 项测试，全部通过，另有模板首次迁移单项验证通过；记录为 `temp/4.9-console-fixes.log`、`temp/4.9-plugin-template.log` 和 `temp/verification/4.9.0-l/console-fixes-test-result.json`。此前完整隔离结果仍保留原验证时间与范围。

## 实际产物

`bootJar` 与普通 `jar` 构建成功。主运行产物为 `build/libs/TeaNeko-App-4.9.0-l.jar`，Manifest 与 `teaneko-version.properties` 均为 `4.9.0-l`。

- 所有当前生产 Java 顶层类型进入 JAR。
- 检查 257 个删除/迁移源码路径：启动类目录与 JAR 中均无旧 class 及 `$` 内部类残留。
- 48 个前端文件逐字节匹配最新构建结果，无旧哈希分包残留。
- 2026/11/07 当时修正产物 SHA-256：`cd915281a4581453d5dae2fd9b9db935e573d8cfd00ac3c741346f3ee8e73634`。
- 构建记录：原交付 `temp/4.9-artifact-build.log`，启动修正 `temp/4.9-startup-command-fix.log`，最新控制台修正 `temp/4.9-console-fixes.log`；最新产物核对为 `temp/verification/4.9.0-l/artifact-result.json`。

保留原 `gradle.properties` 用户修改。未执行真实环境验收、Git 提交、发布标签、推送或部署；这些状态不影响上面明确限定的隔离验证结论。
