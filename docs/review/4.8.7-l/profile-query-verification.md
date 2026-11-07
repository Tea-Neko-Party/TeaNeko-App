# v4.8.7-l 用户画像请求修正

日期：2026/11/07。适用入口为 Agent 工作区的共享记忆用户画像，以及复用同一分层接口的聊天工作区。

## 根因与修复

点击用户同时请求补充事实与长期画像。长期画像请求遗漏 `eventsOnly`，运行环境的严格 JSON 解析器在 `WebManagementCalls.invokeGlobal()` 将请求绑定为 `AgentLayersConsole.Query` 时拒绝把缺失值转为基本类型 `boolean`，尚未进入数据读取。该异常不是数据库旧 schema 或记忆记录损坏。

前端为个人画像、长期修改、遗忘和整理恢复明确传入 `false`，后续重试与回执查询沿用原请求；共享事件仍传 `true`。后端通过字段级空值策略将这个可选筛选项的缺省及 null 定义为 false，不关闭全局基本类型校验，也不放宽其他字段、权限或版本条件。

## 验证

| 范围 | 实际结果 |
| --- | --- |
| 复现 | 新增管理入口回归在修复前得到与用户日志一致的 `Query["eventsOnly"]` / `MismatchedInputException` |
| 后端 | 17 项通过，0 失败、0 错误、0 跳过；严格解析器覆盖缺省、null、false、true，并验证非法布尔文本和空代次仍被拒绝 |
| Chrome | 实际 Agent 页面进入记忆空间、选择共享空间及用户后正确展示；画像刷新、修改及原回执查询、遗忘及返回列表、共享事件筛选共 4 组通过，无页面异常；接口使用本地替身 |
| 构建 | 前端类型检查及生产构建、Java 编译、2 文件 JavaDoc、`checkJavaChanges` 和 `bootJar` 通过 |
| 产物 | JAR 中请求类与启动目录 class 一致；工作区页面包含三处显式 false 请求；`gradle.properties` 摘要未变 |

JAR SHA-256：`6B02C4D9199C57AD5DCA432070567D3F9CCEBDE5AF224F2E2D2FA08FDB6F05B8`。

复现与回归日志在 `temp/verification/4.8.7-l/profile-query-before.log`、`profile-query-before.xml`、`profile-query-fix.log`；浏览器请求记录和截图在 `temp/verification/4.8.7-l/profile-query/`，JavaDoc 在 `temp/javadoc/4.8.7-l/profile-query/`。本次未读取或修改业务数据库，未调用外部模型。

加载新版后端并刷新控制台即可使用，不需要清空或迁移记忆数据。
