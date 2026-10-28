# Agent 实例、作用域与就绪检查

本目录是 Agent 的平台无关业务底座，依赖本层、LLM 和 Core。App、数据库和配置系统的连接放在 `adapter` 或 `_app_config`，不在组件中保存 Spring Bean、凭据、Repository 或平台客户端。

当前完成 S4A1：实例定义/工厂/注册表、可信身份、作用域准入和配置就绪检查。实际 `IAgentService.run`、有序轮次 System、模型工具绑定及会话邮箱仍在下一批接入；本底座尚不接收平台消息，也不发送回复。旧 Agent 的平台入口仍待后续迁移，不能认为已由新门控统一接管。

## 定义与实例拥有关系

`AgentDefinition` 保存稳定定义 ID、`LLMModelOptionsSnapshot` 和允许的 `AgentActivity` 集合。参数在构造时冻结，`toOptions()` 每次返回独立参数容器，嵌套数据继续只读；工具执行器不随模型选项保存。

```java
var definition = new AgentDefinition("assistant",
        new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.values()));
var first = agentFactory.create(definition);
var second = agentFactory.create(definition);
```

同一份定义创建两个实例时，各自使用新 Core Entity 和生命周期。工厂只在 `IAgentRegistry` 登记实例拥有关系，不注册 scope、不绑定记忆空间、不读取模型或凭据，也不启动后台任务。

`AgentHandle.snapshot()` 只读，当前使用类型化 `AgentComponents` 保存定义与关闭状态；关闭控制保留在拥有者内部。`onClose` 返回可解除的监听，后续轮次可用它取消各自的执行控制。`close()` 幂等且只影响当前实例。`registry.remove(id)` 先移除拥有关系，再在注册表锁外关闭，回调可安全重入；关闭注册表会关闭其拥有的所有实例。直接关闭句柄不自动删除管理记录，完成生命周期后应由拥有者 remove。

## 身份与统一准入

`AgentScope` 保留 clientId 与已有 scopeId，不自行解析平台编号。`AgentActorContext` 固定 TeaNeko UUID、平台来源、消息/任务来源和目标。系统任务的 userId 必须为空，平台用户来源也为空，不能继承刚才聊天者的权限。数据由宿主适配器构造，不从模型参数读取。

`AgentAccessGate.evaluate` 对 SERVICE、COMMAND、AUTOMATIC_REPLY、COLLECT、SUMMARY、PROACTIVE、SCHEDULED、TOOL 使用同一开关边界：

1. 实例未关闭且仍由当前注册表拥有，定义允许该活动。
2. 交互命令来自用户；自主和定时活动来自系统任务。
3. scope 有真实 `agent` 配置登记且 enabled 为 true。
4. PROACTIVE 需要 ACTIVE 模式，普通 SCHEDULED 任务不等同主动聊天。
5. 本地总开关、运行设置、具体模型和凭据检查均通过。
6. 检查后复核实例及 scope；准备期间观察到关闭、注销或策略变化时拒绝旧结果。

未注册或禁用时不会读取模型、凭据或运行设置。所有结果含固定中文原因，允许时才有运行快照；结果只是当前检查，不是永久授权。后续入口须在开始、工具副作用、提交和出站前复核，且继续调用原命令/业务权限链。该门控不实现 @/引用/续聊触发判定、不提供分布式原子授权，也未替代 S4B/S5 的会话代次和引用 revision。

## `/cfg` 与本地设置

新 `AgentScopeConfigManager` 的名称为 `agent`，沿用原 `/cfg` 权限、注册和异步完成链。示例：

```text
/cfg reg agent
/cfg set agent enabled false
/cfg set agent enabled true
/cfg set agent mode PASSIVE_BASIC
/cfg set agent mode PASSIVE_CONTEXTUAL
/cfg set agent mode ACTIVE
/cfg unreg agent
```

登记默认启用且使用最低被动模式。缺失记录始终返回 unregistered，读取适配器不自动注册，也不回退到旧 `agent-personality` 默认值。当前仅声明 enabled 和 mode，认证/连接字段、correctionKey 均不能从这里写入。模式只是门控数据，自动触发和主动任务尚未实现。

主文件 `config/agent/main-config.yml` 新增 `enabled: true` 作为新 Agent 总开关；交互次数、时限和可选 token 预算继续由 `tea-neko.llm.interaction` 提供。全局关闭后不继续读取模型或凭据。注册、注销和诊断管理入口不经过 Agent 执行门控，避免未开启时无法开通。

`FileAgentRuntimeConfig` 对主文件和模型文件使用 Core `requireCurrent`，最近一次已完成加载失败时不会沿用旧缓存作为有效配置。既有 `get` 保持最后有效值语义。修复后通过项目现有文件重载流程加载，再在下一次检查取得新快照。

## 就绪状态与后续接入

`IAgentReadiness.inspect` 依次检查本地开关/交互设置、模型路由/参数/能力和 Spring 凭据。只使用模型有效参数预检，不发起 HTTP；认证结果不会放进快照。状态区分 READY、LOCAL_DISABLED、LOCAL_CONFIG_UNAVAILABLE、MODEL_UNAVAILABLE 和 CREDENTIALS_UNAVAILABLE。日志仅记录固定中文阶段原因，不传递原始配置值和异常链。失败不缓存成永久全局关闭，配置源显式重载修正后可以重新检查；一个定义失败不影响其他有效定义。

Spring 组合根仅装配空注册表、工厂和检查端口，构造期间不访问配置/数据库，也不创建默认 Agent。配置和 scope 读取可能接触缓存或文件服务，调用方应在异步业务执行器中检查，不放在 Core `whileActive` 临界区。

下一批将接入有序 Turn System 与真实 LLM 完成阶段：固定模型选项及 scope 数据，建立/继承总预算和截止时间，绑定可信工具上下文，并在关闭与配置变化时结束实际执行。会话串行队列、TTL、历史提交及 namespace/personality 生命周期仍由后续批次实现。
