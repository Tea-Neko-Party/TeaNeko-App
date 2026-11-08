# 独立 LLM 子模块

本目录提供系统原生的模型协议、供应商接入、消息与工具协议、有限交互、结构化输出、模型配置和管理入口。生产代码仅依赖本目录、App 和 Core，不依赖 Agent 组件、运行域、聊天配置或业务插件。新增实现继续通过既有注解与接口注册，发现、启用、权限和调用生命周期分别执行。

| 目录 | 职责 |
| --- | --- |
| [framework](framework/README.md) | 模型、消息、工具协议、交互预算、异步调用与结构化输出 |
| instance | 内置供应商和兼容协议实现 |
| [file_config](file_config/README.md) | 统一模型文件、配置换代、模型样板和凭据输入目录 |
| configuration | 默认路由、凭据端口和借用 Core 执行资源的 Spring 装配 |
| integration/web | 系统模型表单、公开配置样板和显式连接检测 |

Agent 通过 [`component/llm`](../component/llm/README.md) 适配系统路由与当前聊天私有路由。模型文件仍为 `config/agent/model.yml`，Web 文件区仍为 `agent.models`，模型管理 HTTP 路径保持原值；移动 Java 包不迁移用户凭据或改变文件授权。

内置模型实现不声明插件归属。业务插件扩展的模型、路由和输入方式仍接受既有插件准入与真实调用生命周期检查，插件的启停不会把系统模型表单变成可选功能。
