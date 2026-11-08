# Agent 组件底座

本目录提供组件声明、注册、参数校验、依赖检查和有效装配。`llm` 是必需的模型桥接组件，`tool` 提供系统原生工具适配；二者均不受业务插件开关控制。模型协议、供应商实现和系统模型表单位于同级 `llm` 子模块。

`IAgentComponent` 与 `TeaNekoAgentComponent` 描述扩展，`DeclaredAgentComponent` 处理通用字段类型，`AgentComponentRegistry` 在初始化时发布完整目录。运行资格由当前插件状态和组件依赖共同决定，发现扩展不代表启用扩展。

插件自行声明和校验业务参数，通过 `AgentComponentAccess` 读取有效配置。完整持久装配与运行装配分离，停用期间保留原选择、隐藏参数和秘密；基础组件不罗列业务字段或选择业务实现。

详见[项目分层](../../../../../../../docs/development/project-layers.md)与[组件开发](../../../../../../../docs/development/plugins/agent-components.md)。
