# 一. Client 模块结构介绍

`client` 模块定义 TeaNeko 与外部聊天平台之间的适配器协议。它不直接实现某个平台，而是规定平台客户端如何发送消息、接收消息、暴露工具箱，并通过扫描器注册到应用中。

| 类或接口 | 作用 |
|:---:|---|
| `IClient` | 原始客户端接口，提供 `send(String)`、`send(ISendData<?>)` 和 `handle(String)`。 |
| `ITeaNekoClient` | TeaNeko 级客户端接口，继承 core 的 `ICommandClient`，提供 `getClientId()`、`getClient()`、`getTeaNekoToolbox()`。 |
| `@TeaNekoClient` | 客户端 Bean 注解，同时是 `@Component`。`value` 是客户端唯一 ID，`enabled` 控制是否注册。 |
| `AbstractWebsocketClient` | WebSocket 客户端基类，继承 `TextWebSocketHandler`，提供 `_handle(String)` 把解析后的事件推入事件系统。 |
| `TeaNekoClientScanner` | 扫描 `@TeaNekoClient` 且实现 `ITeaNekoClient` 的 Bean，维护 `clientId -> (annotation, client)` 映射。 |

# 二. 客户端接入流程

```markdown
1. 实现 IClient，负责把平台原始字符串转换为 TeaNeko 事件，并把 TeaNeko 发送数据写回平台。
2. 实现 ITeaNekoClient，返回稳定的 clientId、原始 IClient 和 ITeaNekoToolbox。
3. 在 ITeaNekoClient 实现类上标注 @TeaNekoClient("clientId")。
4. TeaNekoClientScanner 启动或 reload 时扫描并注册 enabled = true 的客户端。
5. 收到平台消息时，IClient.handle(...) 返回 TeaNekoMessageReceiveEvent 或 ResponseEvent。
6. AbstractWebsocketClient._handle(...) 可把事件直接推入 IEventService。
```

# 三. 核心 API

| API | 说明 |
|---|---|
| `IClient.send(String message)` | 向外部平台发送原始字符串。 |
| `IClient.send(ISendData<?> data)` | 默认调用 `data.toSendString()` 后发送。 |
| `IClient.handle(String message)` | 解析外部平台消息，返回要推送到事件系统的 `IEvent<?>`。 |
| `ITeaNekoClient.getClientId()` | 获取客户端稳定 ID，用于客户端注册、group scope 前缀和配置隔离。 |
| `ITeaNekoClient.getClient()` | 获取原始客户端实例。 |
| `ITeaNekoClient.getTeaNekoToolbox()` | 获取平台发送器、查询器、头像、踢人等工具集合。 |
| `TeaNekoClientScanner.getPair(String key)` | 按 clientId 获取注解和客户端实例。 |

# 四. 工具箱接口

| 接口 | 作用 |
|:---:|---|
| `ITeaNekoToolbox` | 聚合平台能力。必须实现 `getLogger()`，其他工具默认抛出 `UnsupportedOperationException`。 |
| `IMessageSenderTools` | 提供文本、图片、回复、转发等消息发送构建器入口。 |
| `IGetMessageSender` | 根据消息 ID 获取历史消息。 |
| `IGetGroupMemberInfoSender` | 获取指定群成员信息。 |
| `IGetGroupMemberListSender` | 获取指定群的成员列表。 |
| `IPlatformUserGetSender` | 获取平台用户信息。 |
| `IPlatformUserInfoConstructor` | 把平台用户 ID 构造成 TeaNeko 消息内容列表。 |
| `IAvatarGetter` | 根据平台用户 ID 获取头像 URL。 |
| `IGroupKickSender` | 执行群成员踢出操作。 |

# 五. 接入示例

```java
@TeaNekoClient(value = "onebot", description = "OneBot adapter")
public class OneBotTeaNekoClient implements ITeaNekoClient {
    @Override
    public String getClientId() {
        return "onebot";
    }

    @Override
    public IClient getClient() {
        return websocketClient;
    }

    @Override
    public ITeaNekoToolbox getTeaNekoToolbox() {
        return toolbox;
    }
}
```

# 六. 注意事项

| 项 | 说明 |
|---|---|
| Client ID 稳定性 | `clientId` 会写入作用域 ID 和配置 key。修改它会导致旧群配置、权限范围和用户映射无法按原 key 命中。 |
| `enabled` | `@TeaNekoClient(enabled = false)` 的 Bean 会存在于 Spring 中，但不会进入 `TeaNekoClientScanner` 的注册表。 |
| 工具箱能力 | 平台不支持的能力可以保持默认实现；调用方需要预期 `UnsupportedOperationException`。 |
| 事件推送 | `IClient.handle` 只负责解析并返回事件；是否推送由调用方或 `AbstractWebsocketClient._handle` 完成。 |
