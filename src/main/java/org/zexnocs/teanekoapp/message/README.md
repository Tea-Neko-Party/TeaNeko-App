# 一. Message 模块结构介绍

`message` 模块定义 TeaNeko 内部统一消息模型。外部平台适配器应把平台消息解析为这里的 `ITeaNekoMessageData`、`ITeaNekoUserData`、`ITeaNekoContent` 和 `ITeaNekoContentPart`，再通过事件系统交给命令、插件或业务服务处理。

| 类或接口 | 作用 |
|:---:|---|
| `ITeaNekoMessageData` / `TeaNekoMessageData` | 一条消息的完整数据，包含时间、消息 ID、scope ID、内容列表、消息类型、发送者和客户端。 |
| `ITeaNekoUserData` / `TeaNekoUserData` | 发送者元数据，包含 TeaNeko UUID、平台用户 ID、昵称、群角色和群 ID。 |
| `ITeaNekoContent` / `TeaNekoContent` | 单个消息段，包含 `type` 和 `contentPart`。 |
| `ITeaNekoContentPart` | 消息段的实际内容，提供命令参数转换和原始文本转换。 |
| `ITeaNekoContentListBuilder` / `DefaultTeaNekoContentListBuilder` | 构造 `List<ITeaNekoContent>` 的一次性 builder。 |
| `TeaNekoMessageReceiveEvent<T>` | 客户端收到用户消息后推送的事件。 |
| `@TeaNekoContentPart` | 标记内容片段实现类，供 `ContentPartScanner` 注册。 |
| `ContentPartScanner` | 扫描内容片段类型，维护 `contentType -> contentPartClass` 映射。 |
| `TeaNekoMessageType` | 消息类型枚举：`PRIVATE`、`GROUP`、`PRIVATE_TEMP`、`OTHER`。 |

# 二. 消息模型

| 字段 | 来源 | 说明 |
|---|---|---|
| `time` | `ITeaNekoMessageData` | 消息发送时间。 |
| `scopeId` | `ITeaNekoMessageData` | 命令、配置、权限使用的作用域 ID。 |
| `messageId` | `ITeaNekoMessageData` | 平台消息唯一 ID。 |
| `messages` | `ITeaNekoMessageData` | 消息段列表。 |
| `messageType` | `ITeaNekoMessageData` | 私聊、群聊、群临时会话或其他。 |
| `userData` | `ITeaNekoMessageData` | 发送者信息。 |
| `client` | `ITeaNekoMessageData` | 消息来源客户端，后续回复会通过它的 toolbox 发送。 |

# 三. 内容片段

默认内容片段都使用 `TeaNekoContent.PREFIX`，即 `TeaNeko-` 作为注册前缀。

| 类型 | 实现类 | 注册 key | 主要字段 | 命令参数行为 |
|:---:|---|---|---|---|
| text | `TextTeaNekoContentPart` | `TeaNeko-text` | `text` | 按连续空格拆分，空文本返回空数组。 |
| image | `ImageTeaNekoContentPart` | `TeaNeko-image` | `url` / `file` | 返回图片 URL 或 file。 |
| at | `AtTeaNekoContentPart` | `TeaNeko-at` | `id`、`name` | 返回被 at 用户 ID。 |
| reply | `ReplyTeaNekoContentPart` | `TeaNeko-reply` | `id` | 默认不参与命令解析。 |
| node | `NodeTeaNekoContentPart` | `TeaNeko-node` | `userId`、`nickname`、`contents` | 默认不参与命令解析，raw string 会拼接子内容。 |
| fallback | `DefaultTeaNekoContentPart` | 无注解 | `dataMap` | 找不到合适类型时承载原始 map，不参与命令解析。 |

# 四. Builder API

| API | 说明 |
|---|---|
| `DefaultTeaNekoContentListBuilder.builder()` | 创建一个默认消息列表 builder。 |
| `build()` | 返回内部消息列表。该 builder 是一次性的。 |
| `addContent(ITeaNekoContent)` | 添加已构造好的消息段；如果是文本，会走文本合并逻辑。 |
| `addContents(List<ITeaNekoContent>)` | 批量添加消息段。 |
| `addText(String)` | 添加文本。相邻文本会合并成一个 `TextTeaNekoContentPart`。 |
| `addImage(String)` | 添加图片，支持 `file://`、`http(s)://`、`base64://` 等由平台实现支持的格式。 |
| `addAt(String)` | 添加 at 消息。 |
| `addReply(String)` | 添加 reply 消息。 |

# 五. 快捷发送 API

`ITeaNekoMessageData` 提供两个快捷入口：

| API | 说明 |
|---|---|
| `getMessageSender(String token)` | 从当前消息的客户端 toolbox 获取普通消息发送 builder。 |
| `getMessageSender()` | 使用默认 token 获取普通消息发送 builder。 |
| `getForwardMessageSender(String token)` | 从当前消息上下文获取转发消息 builder。 |
| `getForwardMessageSender()` | 使用默认 token 获取转发消息 builder。 |

# 六. 事件使用建议

| 场景 | 建议 |
|---|---|
| 监听所有平台消息 | 监听 `TeaNekoMessageReceiveEvent<?>`。 |
| 监听某平台的特殊消息 | 在平台适配器中继承 `TeaNekoMessageReceiveEvent<SpecificData>`，监听该具体事件类。 |
| 避免泛型误判 | 事件系统按事件类分发，不按泛型实参分发，因此不要只依赖 `TeaNekoMessageReceiveEvent<SpecificData>` 区分平台。 |
| 命令解析 | `command` 模块会调用每个 content part 的 `toCommandArgs()`，因此自定义内容片段应明确它是否参与命令参数解析。 |
