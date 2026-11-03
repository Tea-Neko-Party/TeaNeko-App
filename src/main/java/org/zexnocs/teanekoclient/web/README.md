# 网页客户端

生产客户端标识为 `web`，机器人平台号为 `web-bot`。客户端、工具箱、真实入站事件、发送器和管理适配器继续通过原 Spring 与 scanner 装配，控制器只负责认证请求映射，不另建 Agent 循环。

- `WebChatStore`：原 EasyData DTO 上的目录、消息、操作与图片事务，不直接操作 Repository。
- `WebChatService`：TeaUser 身份、唯一受理、原消息总线及成员来源撤销。
- `WebMessageData`：原生平台身份和五分钟受限能力，支持 Core `ICommandSourceGuard`。
- `WebMessageSenderTools`：原发送接口、顺序分段及真实持久消息号，无未知结果自动重发。
- `WebMessageFactory` / `WebImages`：认证图片与原生图文映射，不读取任意地址或路径。
- `WebManagementAdapter` / `WebChatFileSettings`：接入原聊天管理和容量文件热更新。

Agent 侧的 `WebAgentTools` 与 `WebExpressionClient` 分别负责当前聊天引用图像查询、共享素材及客户端表情。客户端层不依赖 Agent Store。

具体操作、HTTP、容量、重连、安全与插件边界只维护在[网页聊天说明](../../../../../../../docs/development/web-console-chat.md)。
