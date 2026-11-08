# OneBot 业务插件

官方协议、连接、消息转换及发送工具仍在 `teanekoclient/onebot`。此目录通过客户端接口实现可启停的业务功能。

| 目录 | 插件 ID | 职责 |
| --- | --- | --- |
| `common` | `onebot.common` | 成员管理、点赞、撤回、头衔、互动及平台 Agent 工具 |
| `member_events` | `onebot.member-events` | 入群与退群通知 |
| `requests` | `onebot.requests` | 邀请、审核、自动同意与拒绝 |

三组插件均声明需要 `onebot` 客户端。`onebot.requests` 还依赖 `general.group-management`；`onebot.common` 的好感度联动仅在 `general.engagement` 启用时参与，不影响同插件的其他功能。依赖阻塞由插件总管理页报告，关闭插件不删除审核记录、群配置或其他历史数据。

Agent 工具及来源约束见[平台工具说明](common/agent/README.md)。
