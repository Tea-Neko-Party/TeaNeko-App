# 一、Agent 内置客户端

`TeaNekoAgentClient` 是注册 ID 为 `teaneko-agent` 的特殊 `ITeaNekoClient`。它运行在 App 进程内，不建立 WebSocket 连接，也不把 Agent 响应包装成普通消息接收事件。

| 类 | 作用 |
|---|---|
| `TeaNekoAgentClient` | 向客户端扫描器注册内置 Agent 客户端。 |
| `TeaNekoAgentInternalClient` | 执行结构化 Agent send data，并推送 `AgentResponseEvent`。 |
| `TeaNekoAgentToolbox` | 暴露 Agent 专属 sender 和 logger。 |
| `ITeaNekoAgentToolbox` | 定义 Agent 专属工具箱能力。 |

# 二、能力边界

| 能力 | 行为 |
|---|---|
| Agent 对话 | 支持。 |
| 手动记忆写入 | 支持。 |
| 手动人格修正 | 支持。 |
| 普通消息发送器 | 抛出 `UnsupportedOperationException`。 |
| 群成员、平台用户、头像、踢人 | 抛出 `UnsupportedOperationException`。 |
| 原始字符串发送 | 不支持；内置客户端要求结构化 `ISendData`。 |

# 三、推荐阅读顺序

|顺序|导航|说明|
|---|---|---|
|$1$|[../sender/README.md](../sender/README.md)|了解客户端接收的三类结构化发送数据。|
|$2$|[../response/README.md](../response/README.md)|了解客户端产生的唯一响应事件类型。|
|$3$|[../agent/README.md](../agent/README.md)|了解会话操作进入 Runtime 后的处理流程。|
