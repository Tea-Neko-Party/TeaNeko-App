# 社交插件

插件 ID：`agent.social`，依赖 `agent.cognition`。提供 `affinity`、`mood`、`proactive`、`care` 与 `private-contact` 组件。各项能力仍须显式装配，并在实际调用时复核当前聊天的访问、发送和预算边界。

主动协调与发送服务通过 `IPluginLifecycle` 管理后台任务。插件停用或依赖不可用时停止接收新工作；原参数和持久数据保留。私聊发送只使用真实来源的既有客户端映射，`contact` 下的 `IPrivateContactTransport` 契约与适配器均由本插件维护，复用官方客户端的通用发送能力。

社交资格组合 Agent 的通用组件配置查询和认知插件的记忆读取规则；跨聊天回忆直接使用认知读取入口，不反向依赖社交插件。
