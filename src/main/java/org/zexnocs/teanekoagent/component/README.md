# Agent 能力实现

本目录包含各能力的完整底层实现：参数、系统、领域服务、专属存储与 App 适配、配置和装配。组件可独立安装，模型为必要组件，未安装的业务能力不隐式开启。

共享生命周期在 `runtime`，跨能力 EasyData 基础设施在 `database`；具体 OneBot/Web 适配在 Client 层，不能由组件反向引用。

扩展合同为 `IAgentComponent`、`TeaNekoAgentComponent` 与 `AgentComponentRegistry`；每个业务组件按职责使用子包，不增加新的中央业务分支。

详见[项目分层](../../../../../../../docs/development/project-layers.md)与[组件开发](../../../../../../../docs/development/plugins/agent-components.md)。
