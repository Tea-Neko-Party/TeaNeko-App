# 一. Sender 模块结构介绍

`sender` 模块负责把应用层的发送请求统一转换成事件，再由客户端适配器实际发送到外部平台。它和 `response`、`actuator.task` 配合，通过 `echo` 和 `TaskFuture` 管理异步响应。

| 类或接口 | 作用 |
|:---:|---|
| `ISendData<R>` | 发送数据接口，定义序列化、`echo`、响应类型、目标客户端和可选 object mapper。 |
| `AbstractJsonSendData<R>` | JSON 发送数据基类，自动生成 UUID echo，使用 Jackson `ObjectMapper` 序列化自身。 |
| `ISender<S, R>` | 发送器接口，封装 `sendWithFuture` 和不关心结果的 `send`。 |
| `AbstractSender<S, R>` | `ISender` 的默认实现，委托 `ISenderService` 发送。 |
| `ISenderService` / `SenderService` | 统一发送服务，注册响应 future、推送发送事件、管理重试和超时。 |
| `SentEvent<T>` | 发送事件。事件通知完成后调用 `sendData.getClient().send(sendData)`。 |

# 二. 发送流程

```markdown
1. 调用方构造 ISendData，通常继承 AbstractJsonSendData。
2. 调用 sender 或 ISenderService.send(...)。
3. SenderService 读取 sendData.echo，并优先把 echo 解析为 UUID 作为 task key。
4. SenderService 向 ResponseService 注册 echo -> (task key, sendData)。
5. SenderService 创建一个 TaskConfig，callable 内部推送 SentEvent，随后返回 null 进入间接完成模式。
6. SentEvent._afterNotify() 调用 IClient.send(sendData)，真正把数据发到外部平台。
7. 客户端收到平台响应后推送 ResponseEvent。
8. ResponseService 根据 echo 找回 task key，解析响应并 complete 对应 TaskFuture。
9. future 完成后 SenderService 自动 unregister echo，避免响应注册表泄漏。
```

# 三. 核心 API

| API | 说明 |
|---|---|
| `ISendData.toSendString()` | 将发送数据转换为平台可发送字符串，通常是 JSON。 |
| `ISendData.getEcho()` | 获取响应匹配键；建议为 UUID 字符串。 |
| `ISendData.getResponseType()` | 返回响应元素类型。为 `null` 或 `Void.class` 时，响应结果按空列表处理。 |
| `ISendData.getClient()` | 获取目标客户端。 |
| `ISendData.getObjectMapper()` | 返回响应解析用 mapper；为 `null` 时使用 `ResponseService` 默认 mapper。 |
| `ISenderService.send(S, Duration, int, Duration)` | 使用默认 `SentEvent` 发送。 |
| `ISenderService.send(IEvent<S>, Duration, int, Duration)` | 使用自定义事件发送。 |
| `ISender.sendWithFuture(...)` | 返回 `TaskFuture<ITaskResult<List<R>>>`，用于处理平台响应。 |
| `ISender.send(...)` | 调用 `sendWithFuture(...).finish()`，适合不关心响应的场景。 |

# 四. SenderService 行为

| 行为 | 当前实现 |
|---|---|
| task key | 优先使用 `UUID.fromString(echo)`；如果 echo 不是 UUID，则生成随机 UUID。 |
| 发送触发 | task 的 callable 推送事件，事件后置逻辑负责实际发送。 |
| task 名称 | 包含 `sendData.toSendString()`，方便排查发送内容。 |
| 过期时间 | 固定为 5 分钟。 |
| 重试 | 使用调用方传入的 `maxRetryCount` 和 `retryDelay`。 |
| 清理 | future 无论成功或失败都会 unregister echo。 |

# 五. 发送构建器接口

`sender/api/sender_box` 目录放置平台适配器应实现的高层发送工具接口。

| 接口 | 作用 |
|:---:|---|
| `IEasyMessageSenderBuilder` | 普通消息一次性 builder，支持文本、图片、at、reply、延迟、重试和失败记录开关。 |
| `IForwardMessageSenderBuilder` | 转发消息 builder，支持 bot 节点、用户节点、分段发送、外显文本和摘要。 |
| `IGetMessageSender` | 根据消息 ID 获取消息。 |
| `IGetGroupMemberInfoSender` | 获取单个群成员信息。 |
| `IGetGroupMemberListSender` | 获取群成员列表。 |
| `IPlatformUserGetSender` | 获取平台用户信息。 |

# 六. 注意事项

| 项 | 说明 |
|---|---|
| `echo` 唯一性 | `ResponseService.register` 会拒绝重复 echo，并抛出 `ResponseEchoDuplicateException`。 |
| 间接完成模式 | 发送 task 的 callable 返回 `null`，结果必须由后续 `ResponseEvent` 完成。 |
| 自定义事件 | 如果自定义事件不是 `SentEvent`，不会自动设置 `taskKey` 字段，但仍可通过 echo 完成响应。 |
| `finish()` | 不处理 future 时应调用 `finish()`，否则异常不会进入统一日志处理。 |
