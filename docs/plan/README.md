# 开发计划索引

进行中的实施计划放在本目录；完成其约定的实现与验收范围后，主计划及配套需求、规格和历史记录一并移入 old。归档只调整必要的定位链接，保留原始答复、进度证据及当时的验收结论。

## 进行中

当前没有进行中的实施计划。

## 已完成

| 计划 | 完成范围 | 发布说明 |
| --- | --- | --- |
| [4.6.0 注解工具](old/4.6.0-annotated-tools.md) | Spring 自动注册、参数 schema、client、可信上下文、异步图像及十项工具迁移 | [v4.6.0-l](../changelog/4.6.0-l.md) |
| [4.6.0 引用图片工具回填](old/4.6.0-tool-images.md) | 显式图像结果、OneBot 视觉输入、批次配对及历史投影 | [v4.6.0-l](../changelog/4.6.0-l.md) |
| [4.6.0 平台上下文与共享记忆修正](old/4.6.0-agent-context.md) | 私聊触发、命令帮助、namespace 共享召回、client 工具边界、OneBot 查询与模型消息元数据 | [v4.6.0-l](../changelog/4.6.0-l.md) |
| [4.6.0 插件化](old/4.6.0-plugins.md) | 插件底座、Agent 能力、事件与异步拦截、管理 SPI、开发文档及离线验收 | [v4.6.0-l](../changelog/4.6.0-l.md) |
| [4.5.0 Agent](old/4.5.0-agent.md) | S0–S9 实现与离线交付，原始需求、规格和分阶段证据随主计划归档 | [v4.5.0-l](../changelog/4.5.0-l.md) |

## 后续验收与设计

- 真实 MySQL、OneBot、模型、完整启动与性能检查继续维护在 [部署验收清单](../development/agent-deployment.md#实机验收清单)，不计入已完成计划的实机通过结论。
- [Web 管理设计](../design/web-management-api.md) 保留后续业务资源、HTTP 和实时订阅的设计范围，实际已有能力以 [管理扩展指南](../development/plugins/management.md) 为准；当前不将整套 Web 标记为已经开工或完成。
- 历史计划中的“当前版本”“下一步”和阶段百分比表示归档时状态；最新版本与操作方式查阅 [发布日志](../changelog/README.md) 及对应模块文档。
