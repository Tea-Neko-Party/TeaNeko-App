# TeaNeko Agent

本模块提供 Agent 生命周期、模型能力及可组合业务的公共契约，复用 Core 执行控制、Task、Event、Timer 和 EasyData。v4.9.0-l 将独立模型能力放在 `llm`，`component` 只保留组件底座及 LLM、Tool 适配，具体记忆、人格、表达、社交及自动化业务分别归入 `teanekoplugin/agent` 下的业务插件。生命周期归 `runtime`，通用 App 接入归 `integration`；官方客户端适配与内部客户端位于 Client 层，见[项目分层](../../../../../../docs/development/project-layers.md)。新登记 Agent 默认只有 LLM，已有登记保留原装配行为。

v4.6.0-l 增加插件能力装配，开发入口见 [Agent 插件指南](../../../../../../docs/development/plugins/agent.md)；组件、工具、资料和 System 按实例声明接入，默认记忆与命令能力继续保留。

v4.6.2-l 交付拟人分段、发送节奏、共享表情包采集/选图/管理、客户端表情及受限事件，见[版本日志](../../../../../../docs/changelog/4.6.2-l.md)。v4.7.0-l 已接通基础控制台、聊天配置和 [Agent 工作区](../../../../../../docs/development/web-console-agent.md)，当前插件总管理与独立插件页面见[插件开发入口](../../../../../../docs/development/plugins/README.md)。

| 使用目标 | 权威入口 |
| --- | --- |
| 首次部署、配置及实机检查 | [部署说明](../../../../../../docs/development/agent-deployment.md) |
| 显式聊天、管理与命令工具 | [命令说明](integration/command/README.md) |
| 自动图文入站、回复形式、定时/接话 | [平台说明](integration/message/README.md) |
| 拟人分段、发送节奏与注解协议 | [表达与交付](../teanekoplugin/agent/expression/expression/README.md) |
| 表情包采集、视觉分类、共享素材及容量 | [素材库](../teanekoplugin/agent/expression/sticker/README.md) |
| 内部客户端、三类 Sender、直接服务 | [内部集成](../teanekoclient/agent/README.md) |
| 实例、共享会话、预算与取消 | [运行领域](runtime/README.md) |
| 数据库入口 | [专用 EasyData](database/easydata/README.md) |
| 空间、本人事实和画像 | [空间](../teanekoplugin/agent/cognition/memory/namespace/README.md)、[事实](../teanekoplugin/agent/cognition/memory/fact/README.md)、[画像](../teanekoplugin/agent/cognition/memory/profile/README.md) |
| 人格与补正 | [人格说明](../teanekoplugin/agent/cognition/personality/README.md) |
| 跨聊天回忆、私聊、好感度、心情及主动关怀 | [社交组件](../../../../../../docs/development/agent-social-memory.md) |
| 历史与后台摘要 | [历史](../teanekoplugin/agent/cognition/memory/history/README.md)、[摘要](../teanekoplugin/agent/cognition/memory/summary/README.md) |
| Skill、显式协作与扩展 | [Skill](../teanekoplugin/agent/automation/skill/README.md)、[协作](runtime/workflow/README.md)、[扩展](../teanekoplugin/agent/automation/extension/README.md) |
| 提醒与自然语言任务 | [任务说明](../teanekoplugin/agent/automation/schedule/README.md) |
| 用量、告警和运行状态 | [监控](runtime/monitor/README.md) |
| 模型、凭据与图像能力 | [独立 LLM](llm/README.md)、[模型配置](llm/file_config/README.md) |
| 规格与实施证据 | [计划入口](../../../../../../docs/plan/old/4.5.0-agent.md) |

默认最低被动模式，必须先登记并启用当前 scope；命令工具默认关闭。模型默认路由、参数、密钥、连接及 metadata 统一在 model.yml，可使用 Core YAML 环境占位符。凭据只供可信配置和请求认证边界使用，不从聊天、Skill 或模型内容取得，不进入业务组件及响应 DTO；原生来源由适配边界保持，系统任务不继承人类身份。
