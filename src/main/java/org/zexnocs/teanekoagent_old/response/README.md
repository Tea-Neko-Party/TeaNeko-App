# 一、Agent Response

`AgentResponseEvent` 是内置 Agent 客户端产生的响应事件。它继承 App 标准 `ResponseEvent`，因此 `ResponseService` 可以继续根据 echo 找回 send data、解析具体响应类型并完成对应任务。

| 类型 | 作用 |
|---|---|
| `AgentResponseData` | 保存 success、echo 与 rawData。 |
| `AgentResponseEvent` | 将响应推送给标准 `ResponseService`。 |
| `AgentConversationResponse` | 保存会话 ID、请求消息 ID 和 `AgentOutput`。 |
| `AgentMemoryWriteResponse` | 保存实际写入的记忆记录。 |
| `AgentPersonalityCorrectionResponse` | 保存是否接受及人格修正记录。 |

# 二、事件约束

内置 Agent client 执行 sender 操作时只推送 `AgentResponseEvent`。它不会推送 `TeaNekoMessageReceiveEvent`、命令事件或外部平台 post event，避免手动 Agent 操作进入普通消息处理链。

# 三、推荐阅读顺序

|顺序|导航|说明|
|---|---|---|
|$1$|[../sender/README.md](../sender/README.md)|了解产生响应的 sender 与 echo 注册流程。|
|$2$|[../client/README.md](../client/README.md)|了解响应事件由哪个内置客户端产生。|
|$3$|[../agent/thinking/README.md](../agent/thinking/README.md)|了解对话响应中的 AgentOutput。|
