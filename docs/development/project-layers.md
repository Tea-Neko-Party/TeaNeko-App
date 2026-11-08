# 项目分层与 Agent 目录

适用版本：v4.9.0-l。

## 依赖方向

| 层 | 可依赖 | 职责 |
| --- | --- | --- |
| `teanekocore` | 自身 | 扫描、事件、任务、配置、数据库及组合系统底座 |
| `teanekoapp` | Core | 客户端/消息/发送契约、身份、命令接入、插件运行管理与 Web 管理合同 |
| `teanekoagent` | App、Core | Agent 生命周期、组件与能力契约、模型调用及通用 App 集成 |
| `teanekoclient` | Agent、App、Core | 官方平台连接、消息适配及面向 Agent 的协议适配 |
| `teanekoplugin` | 前四层 | 可独立启停的业务实现，也可通过下层契约扩展客户端 |

“依赖”包括 import、内联全限定名、反射类名以及必须存在的上层能力标识。扫描范围覆盖多个包不等于下层依赖上层。新增能力由上层实现下层接口并主动注册，不在下层增加平台分支。

OneBot 连接与协议实现位于 `teanekoclient/onebot`，表达业务适配及平台 Agent 工具位于 `teanekoplugin/onebot/common/agent`；网页客户端对应实现与聊天室 HTTP 控制器在 `teanekoclient/web`。客户端可共享的发送支持放在 `teanekoclient/general/agent`。App 的管理异常边界通过 `WebManagedEndpoint` 与 `WebFailureMapping` 接入业务，不直接引用 Agent 异常或客户端控制器。

## Agent 目录

```text
teanekoagent/
  llm/                独立模型能力，不依赖 Agent 组件、运行域或聊天配置
    framework/        模型、消息、工具协议、有限交互与结构化输出
    instance/         供应商及兼容协议实现
    file_config/      系统模型配置、凭据目录与样板
    integration/web/  系统模型表单及连接检测
  component/          组件声明、参数底座、注册目录及有效装配
    llm/              LLM 与 Agent 的桥接、聊天私有模型路由
    tool/             原生工具组件、注解工具声明及执行框架
  runtime/            实例、轮次、会话生命周期、准入、通用交付与工作流
  integration/        基于 App 通用合同的配置、消息、命令及 Web 集成
  configuration/      共享 Spring 装配及运行设置
  database/           跨能力共用的 Agent 专用 EasyData 底座
  plugin/             Agent 扩展合同及版本守卫，并非顶层业务插件
```

具体业务契约、服务、存储、可选组件与专属配置归入 Plugin；Agent 只保留下层消费者确实使用的类型化端口、不可变数据及通用骨架。宿主配置不能集中列举插件字段、访问器或业务标识分支，插件通过组件声明自行校验、迁移和读取参数。`integration` 只认识 App 的消息和客户端接口，不认识 OneBot/Web 的具体实现。

```text
teanekoplugin/
  agent/
    cognition/        记忆、跨聊天回忆、人格及学习维护
    expression/       拟人表达、图库及素材业务
    social/           主动活动、私聊、好感度、心情及关怀
    automation/       工具组件、命令工具、技能与提醒业务
  general/            跨客户端的通用业务插件
  onebot/             OneBot 常规业务、成员活动等插件
  web/                网页专属业务插件
```

每个插件通过 `@TeaNekoPlugin` 声明唯一 ID、插件依赖与客户端需求，贡献通过 `@PluginOwned` 或所属扩展的归属字段标识。只有 `teanekoplugin` 及子包允许声明业务插件归属；Core、Agent 使用独立宿主能力身份。插件总管理保留全部插件及停用原因，其他目录只展示当前启用且满足依赖的贡献。具体合同见[插件底座指南](plugins/core.md)。

## 内部客户端为什么保留

原 `agent/client` 是进程内桥接，不是平台网络连接。它提供稳定的 `teaneko-agent` 客户端 ID、Toolbox 以及对话、明确记忆写入、人格补正三类 Sender/Response API。

本版保留这一既有插件集成合同，整体迁入 `teanekoclient/agent`，通用执行边界在 `service/AgentOperations`，发送与响应分别在 `sender`、`response`。具体操作通过 `IAgentClientOperation` 注册：普通聊天由客户端提供，记忆写入和人格补正由认知插件提供。它继续使用真实来源身份、回执防重及原取消控制，不回灌普通消息事件，也不替换 Agent 运行服务。无需这种桥接的调用方可直接使用 Agent 服务端口。

## 扩展与升级

- 一个注解工具类只声明一个工具方法。查询、授权、DTO 转换等逻辑放入服务复用，工具声明保留名称、参数、客户端、组件和插件归属。
- 官方客户端主动提供协议能力，业务插件声明需要的客户端；通用工具组件不要求 OneBot 存在。
- 迁移的业务类型需更新 import 并重新编译；原配置路径与业务持久标识保留。旧能力 ID、管理资源授权和显式 `shared-memory` 装配按精确映射升级，跨聊天回忆合入 `memory.crossChatRecallEnabled`，不因升级扩大权限。
- 重构后的分层和单工具约束由生产源码边界测试检查；业务行为仍由原核心流程测试验证。

组件实现见[组件开发](plugins/agent-components.md)，装配方式见[使用说明](agent-composition.md)。
