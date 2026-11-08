# 认知插件

插件 ID：`agent.cognition`。提供 `memory`、`personality` 组件，以及记忆、画像、历史与人格管理。跨聊天回忆合并为 `memory.crossChatRecallEnabled`，新安装默认关闭；旧 `shared-memory.enabled` 通过幂等转换保留原值，冲突明确拒绝。

`CognitionPlugin` 声明元数据；`CognitionAssemblyMigration` 声明配置及能力标识迁移；`CognitionConsoleContribution` 提供独立工作区和原管理授权键迁移；`integration/command/CognitionCommands` 向 `/agent` 贡献业务子命令。后台服务通过统一 `IPluginLifecycle` 接入启停，数据和配置由原领域事务保留。

下层只持有稳定接口，具体配置、存储和业务服务在本目录。管理仍通过原网关复核权限，关闭插件不删除记忆空间或人格补正。社交插件依赖认知插件。
