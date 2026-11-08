# 业务插件目录

v4.9.0-l 的可选业务按用途分组，插件内可以同时提供命令、事件、Agent 能力、配置、任务和管理页面。一个类或一个命令不必单独成为插件；依赖、客户端要求和热启停以 `@TeaNekoPlugin` 声明为边界。只有本包及子包允许插件声明和 `@PluginOwned`，Core、Agent 的宿主能力独立登记。

| 分类 | 插件与入口 |
| --- | --- |
| Agent 认知 | [agent.cognition](agent/cognition/README.md)：记忆、跨聊天回忆、人格与学习 |
| Agent 表达 | [agent.expression](agent/expression/README.md)：拟人表达与图库 |
| Agent 社交 | [agent.social](agent/social/README.md)：主动活动、私聊、好感度、心情及关怀 |
| Agent 自动化 | [agent.automation](agent/automation/README.md)：工具、技能、命令与提醒 |
| 通用业务 | [general](general/README.md)：系统、公开资料、签到互动、游戏、群管理及搜图 |
| OneBot 业务 | [onebot](onebot/README.md)：常规操作、成员活动及申请审核 |
| 网页业务 | [web](web/README.md)：网页工具与私聊初值 |

官方连接、协议与消息适配继续位于 `teanekoclient`。插件可通过下层契约提供新客户端；业务中使用平台能力时，声明客户端需求并保留实际调用权限复核。

插件总管理展示完整插件目录、依赖、状态及贡献。其他页面仅展示启用且满足依赖的贡献；关闭不删除持久数据。开发合同统一见[插件开发入口](../../../../../../docs/development/plugins/README.md)，分层见[项目分层](../../../../../../docs/development/project-layers.md)。
