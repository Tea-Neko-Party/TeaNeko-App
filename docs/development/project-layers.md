# 项目分层与 Agent 目录

适用版本：v4.8.0-l。

## 依赖方向

| 层 | 可依赖 | 职责 |
| --- | --- | --- |
| `teanekocore` | 自身 | 扫描、事件、任务、配置、数据库及组合系统底座 |
| `teanekoapp` | Core | 客户端/消息/发送契约、身份、命令接入、Web 管理合同 |
| `teanekoagent` | App、Core | Agent 生命周期、可组合能力、模型调用及通用 App 集成 |
| `teanekoclient` | Agent、App、Core | 具体平台连接、消息适配及面向 Agent 的平台能力 |
| `teanekoplugin` | 前四层 | 面向用户的扩展业务 |

“依赖”包括 import、内联全限定名、反射类名以及必须存在的上层能力标识。扫描范围覆盖多个包不等于下层依赖上层。新增能力由上层实现下层接口并主动注册，不在下层增加平台分支。

OneBot 对 Agent 的工具和表达适配在 `teanekoclient/onebot/agent`；网页客户端对应实现与聊天室 HTTP 控制器在 `teanekoclient/web`。客户端可共享的发送支持放在 `teanekoclient/general/agent`。App 的管理异常边界通过 `WebManagedEndpoint` 与 `WebFailureMapping` 接入业务，不直接引用 Agent 异常或客户端控制器。

## Agent 目录

```text
teanekoagent/
  component/          能力的完整实现，不只是 Component 壳类
    llm/              协议、模型、调用、配置来源及凭据边界
    memory/           记忆、历史、摘要、画像及专属存储适配
    personality/      人格、观察学习、补正及存储适配
    expression/       表达协议、发送计划及 App 发送适配
    sticker/          采集、分析、图库、使用及专属配置
    tool/             注解工具框架、命令合同及知识/代码扩展端口
    skill/            技能目录、参数及接入
    schedule/         调度领域、存储及原任务适配
    proactive/        主动活动与目标发现
  runtime/            实例、轮次、会话生命周期、准入与通用工作流
  integration/        基于 App 通用合同的配置、消息、命令及 Web 集成
  configuration/      共享 Spring 装配及运行设置
  database/           跨能力共用的 Agent 专用 EasyData 底座
  plugin/             Agent 扩展合同及版本守卫，并非顶层业务插件
```

能力的领域服务、DTO、接口、业务存储和专属装配一并归入所属组件。只有实际跨能力共用的运行生命周期与数据库基础设施留在外部。`integration` 只认识 App 的消息和客户端接口，不认识 OneBot/Web 的具体实现。

## 内部客户端为什么保留

原 `agent/client` 是进程内桥接，不是平台网络连接。它提供稳定的 `teaneko-agent` 客户端 ID、Toolbox 以及对话、明确记忆写入、人格补正三类 Sender/Response API。

本版保留这一既有插件集成合同，整体迁入 `teanekoclient/agent`，业务桥在 `service/AgentOperations`，发送与响应分别在 `sender`、`response`。它继续使用真实来源身份、回执防重及原取消控制，不回灌普通消息事件，也不替换 Agent 运行服务。无需这种桥接的调用方可直接使用 Agent 服务端口。

## 扩展与升级

- 一个注解工具类只声明一个工具方法。查询、授权、DTO 转换等逻辑放入服务复用，工具声明保留名称、参数、客户端、组件和插件归属。
- 具体客户端主动提供能力贡献；通用工具组件不要求 OneBot 存在。旧配置、工具 ID、HTTP 路径、Bean 名及持久标识保持不变。
- 本次是 Java 包迁移，依赖旧公开类型的插件需更新 import 并重新编译。没有旧包源码转发壳，也不迁移真实数据库数据。
- 重构后的分层和单工具约束由生产源码边界测试检查；业务行为仍由原核心流程测试验证。

组件实现见[组件开发](plugins/agent-components.md)，装配方式见[使用说明](agent-composition.md)。
