# Core 插件编写

## 入口与装配

业务插件在 `org.zexnocs.teanekoplugin` 或其子包声明 `@TeaNekoPlugin`。注解自带 Spring 组件语义，`id` 是稳定插件身份，不改变 Bean 名称；提供其他 `@Bean` 时可同时使用 `@Configuration(proxyBeanMethods = false)`。

```java
package org.zexnocs.teanekoplugin.example;

@TeaNekoPlugin(id = "example.tools", name = "示例工具", version = "1.0.0",
        description = "提供示例平台操作。", category = "示例",
        dependencies = {"teaneko.agent"}, clients = {"onebot"},
        switchMode = PluginSwitchMode.RESTART_REQUIRED)
public class ExampleToolsPlugin {
}
```

静态声明统一编译为 `PluginDescriptor`，业务入口不实现元数据接口：

| 字段 | 含义 |
| --- | --- |
| id | 全局稳定插件 ID，格式为小写字母开头的字母、数字、点或短横线，最长 64 位 |
| version | 插件自身实现版本，不参与宿主 SPI 兼容判断 |
| apiVersion | 当前必须为 1，不支持的版本直接拒绝 |
| dependencies | 必需业务插件或宿主能力 ID；缺失业务依赖保留可诊断目录并阻止启用，环及重名拒绝装配 |
| clients | 必需已安装客户端 ID；连接在线状态由具体业务另外复核 |
| name / description / category | 插件管理中展示的中文名称、功能边界和分类 |
| switchMode | HOT 表示资源支持可重复热启停；RESTART_REQUIRED 保存意愿后等待重启 |

Core 的 `teaneko.core` 和 Agent 的 `teaneko.agent` 由 `HostCapability` 单独登记，保留管理资源和能力依赖的稳定身份。宿主能力不出现在插件列表、启停操作及生命周期中；宿主只能依赖其他宿主，业务插件可以依赖宿主。LLM 与原生工具属于系统能力，不通过业务插件开关管理。入口参考 [PluginConfiguration](../../../src/main/java/org/zexnocs/teanekocore/plugin/PluginConfiguration.java)。`IPluginModule` 仅供隔离装配程序化元数据使用，生产容器从 `PluginDeclarationScanner` 取得注解目录。

只有 `org.zexnocs.teanekoplugin` 及子包允许声明插件和 `@PluginOwned`；外部依赖 JAR 也须遵守此命名空间，并由宿主显式导入配置或扫描根。仅添加 JAR 或放入任意目录不会自动发现插件。Bean 导入与类扫描范围分别配置，不能只完成其一。`PluginDeclarationScanner` 读取通过 Spring 条件装配的 Bean 类型，不为发现元数据提前创建业务实例；条件未满足的示例不进入安装目录。运行中改变安装声明需要重启，常规启停仍依据 `switchMode`。

事件监听器、命令、TaskStage 仍使用各自原注解。@EventListener 本身带组件语义，避免同时被默认组件扫描和 @Bean 重复装配；可像示例一样给配置与监听器使用相同 ConditionalOnProperty 条件，或由外部未扫描包的插件配置显式提供 Bean。

## 扫描与查询

`IClassScanner`、`IBeanScanner` 的所有入口，以及 Bean 定义、注解和方法的目录发现，只用于单例创建、初始化或低频显式重扫；指定 Bean 的目标类型也在此阶段解析并缓存。消息处理、权限复核、模型调用和页面轮询读取业务专用 Scanner 的缓存；新扫描器实现 `IScanner`，在 `init()` / `reload()` 中发现并校验完整结果后一次发布，不先清空读者正在使用的目录。失败重扫保留上一完整目录。

优先复用已有目录，例如 `TeaNekoClientScanner`、`FileConfigScanner`、`TimerSourceScanner`。缓存保存类型、注解、Bean 和静态说明；插件启停、权限、连接与任务状态仍在查询或执行时复核。文件配置普通查询使用 `get()` / `requireCurrent()`，同一发布版本的短同步查询使用 `withRuntime()`，原文档编辑才使用 `withDocument()`。

目录在原 `ReloadService` 初始化阶段建立，业务查询不触发首次发现或失败后的自动重试。首次初始化失败时查询明确报告未就绪；重载服务仍保留该对象的登记，修复声明后可显式重扫。直接构造目录的测试或独立宿主须先调用 `init()`，不要借助首个业务请求扫描来完成装配。

## 归属与逻辑生命周期

在命令类、事件监听类或 TaskStage 类添加 @PluginOwned("插件ID")；方法上的归属优先于类声明。插件提供的模型适配器也使用该注解。归属必须是已登记业务插件，宿主身份不得用于 `@PluginOwned`。归属在初始化阶段编译；命令或事件目录先初始化时，后续注入端口会从已有快照补登记，业务查询不扫描或重试发现。未声明归属的原生 Bean 保留宿主行为，第三方不得借此绕过启停。

PluginRegistry.list() 返回静态描述、依赖计算后的有效 enabled 和 revision。`setEnabled` 是底层运行门禁，不能用来替代宿主的持久意愿、依赖预览与资源生命周期协调。Web 启停统一经过 App 的 `PluginManagementService`，固定目录版本与 operationId，经原管理网关授权后提交；相关接口与回执见[插件总管理](management.md#插件总管理与升级)。

v4.9.0-l 的权威启用意愿在 `config/plugins.yml`：`version: 1`、`migrated` 和按插件 ID 保存的 `enabled` 映射。首次启动将旧 `tea-neko.plugins.disabled` 导入尚未明确记录的条目，再持久化迁移标记；之后不再用旧列表覆盖 Web 管理决定。管理操作拒绝未知插件及宿主身份；旧配置中的宿主启用记录可以读取，关闭宿主明确拒绝。未知旧插件的配置意愿继续保留。未启用、依赖阻塞和客户端离线是不同状态，目录不把这些状态写回启用意愿。

需要客户端能力的插件优先通过 `@TeaNekoPlugin(clients = {...})` 集中声明；App 的 `PluginClientDirectory` 从已发布客户端安装目录解析要求，客户端所属插件自动加入依赖计算。程序化 `PluginClientRequirement` 可用于独立装配，同一插件不能同时从两处重复声明。官方维护的实现留在 `teanekoclient`，插件可在自身分类目录贡献客户端。缺少必需客户端或其所属插件停用时拒绝业务准入；在线连接仍在具体业务调用时复核，不把“已安装客户端”当成“已连接”。

拥有后台扫描、定时器或连接的插件实现 `IPluginLifecycle`，用插件内唯一 `id()` 声明资源集合。`start()` 必须完成资源建立后才结束；`stop()` 停止新增工作并等待自有资源实际结束，支持之后再次 start，不能永久关闭再次启用仍需使用的共享对象。宿主按依赖顺序启用、反向停用；先撤销新调用资格再等待清理，失败保留明确状态并由显式操作恢复。没有这些真实保证时声明 RESTART_REQUIRED，旧构造的普通插件也采用此保守模式。

capture(id) 固定插件及传递依赖版本，check(token) 拒绝禁用或换代后的旧调用。禁用再启用不会使旧令牌恢复有效。invoke(id, stageFactory) 在启动及真实异步完成时检查，适合封装自己的调用端口。资源提交前仍应检查原业务控制、授权及当前版本；提交已经发生时不因随后检查失败声称回滚。

事件监听器禁用后不参与新通知；命令在启动及其返回 Stage 完成时复核；TaskStage 在同步进入与退出时复核；模型服务等待实际模型 Future 后复核。禁用不会自动撤回已经提交的外部副作用，也不销毁 Spring Bean。IO 适配器响应借用 ExecutionControl 的终止；可重复停用清理由 IPluginLifecycle 管理，应用退出时仍由资源所有者完成最终销毁，共享执行器不由插件关闭。

## 向已有命令增加插件子命令

插件可用 `@CommandExtension(prefix = "/agent")` 声明向已注册主命令贡献子命令，Spring 管理实际实现。`prefix` 只用于命令路由，不作为 Bean 名，因此多个插件可以扩展同一前缀。注册目录保存每个方法的真实贡献 Bean，主命令和子命令标识冲突会明确报告；帮助、目录与执行都使用该贡献者的 `@PluginOwned`。停用后子命令不可发现且无法执行，不借宿主主命令的身份绕过插件门禁。

`CommandScanner` 和显式 `CommandMapData` 装配在初始化阶段通过 `CommandMethodContract.register` 编译参数、说明、来源类型及管理声明。页面查询、权限复核和发送使用已注册合同，未注册的方法明确拒绝；独立宿主或测试直接使用执行器时须先完成同样的初始化，不能在消息路径补扫描。

## 管理来源命令

4.7.0-l 在原命令链新增 `CommandOrigin`，旧调用默认 NATIVE。管理调用明确为 MANAGEMENT，方法必须声明 `@ManagementCommand` 且使用具体 `CommandData<T>`，其中 T 实现 `IManagedCommandSource`。扫描时校验签名，分发与实际排队启动时复核能力；原权限、客户端范围、事件、参数绑定及 `CompletionStage<?>` 仍然生效。管理登录不自动获得 OWNER 或 DEBUG，也不能构造平台成员。

App 的 `ConsoleInvocation` 提供固定目标的 `print`、`send` 和 `checkActive`，由宿主生成；发送方法还须声明 `sendsMessages = true`。开发方式、白名单和真实完成合同见[客户端指令台](../web-console-commands.md)。原生消息依赖方法须另行提取受限管理用例，不能仅添加注解后伪造原生数据。

可撤销原生平台来源实现 `ICommandSourceGuard`，其 `checkCommandSource` 在原分发和实际排队启动时调用。网页客户端使用该接口复核会话、期限及成员变更；原生来源仍保留自身角色，不转成管理来源或绕过原权限。

## 原同步 TaskStageChain

实现 ITaskStage 并标记 @TaskStage(value = {"业务命名空间"}, priority = 数值)，再声明 PluginOwned。优先级越大越先进入，同优先级按 Bean 名升序；退出顺序与进入相反。也可以通过 TaskConfig 显式阶段列表为单个任务选择步骤；不要无意将业务插件注册到所有 default 任务。

process(chain) 中先做同步前置处理，调用一次 chain.next()，再做同步后置并返回 ITaskResult。整条同步调用栈在同一线程推进，期间允许提交异步任务。没有线程阻塞要求时，异步回调不要继续调用这个 chain.next()，也不要将数据库事务或锁跨远端 IO 保存。

chain.next() 返回代表同步调用结果，后置逻辑不自动等待 Callable 所启动的 CompletionStage。TaskFuture.asStage()/fromStage 可桥接完成链；TaskStages.successful 额外检查 ITaskResult.isSuccess，而仅 asStage 不检查业务成功。Callable 返回 null 的外部完成模式仍可用，但它不会重新执行已经退栈的后置代码。

需要完整异步结果转换时使用下述 OperationPipeline；不必把所有 ITaskStage 改成异步接口。命令仍须返回 CompletionStage<?>，通常为 CompletionStage<Void>，并等待实际业务与反馈。

## 定时展示

4.7.1-l 提供 `@TimerDisplay` 方法/类型声明和注册端元数据，原 Timer 与 Spring 调度保持独立。分类、只读边界、手动注册及观测来源扩展见[定时展示与分类](management.md#定时展示与分类)。

## 类型化异步环绕

OperationPipeline<I,O> 接受 PluginRegistry、有序 Binding<I,O> 列表和 shortCircuit 开关。每个 Binding 声明 pluginId、局部 id、order 和 IAsyncInterceptor；执行时传入 input、借用 ExecutionControl 与实际 IAsyncOperation。

高 order 先进入，随后按 pluginId、id 排序；同一插件内的节点重名拒绝。节点实际语义是 intercept(input, next) → CompletionStage<O>，可以通过 thenCompose 等待异步前置，通过 thenApply/thenCompose 转换最终结果，通过明确的异常分支处理普通业务失败。

- next 至多调用一次，重复调用即使被插件忽略也不能成为成功；节点自身阶段完成后不能迟到启动 next。
- 已启动的下游会被等待，防止插件启动实际工作后提前返回成功；失败传播也等待已启动下游。仍可能存在不响应取消的外部 IO，不把 Future 结束等同物理 IO 已撤销。
- shortCircuit=false 要求执行 next；只有宿主明确允许缓存等只读短路时才使用 true。写操作不能自行增加自动重试。
- 返回的 Stage 包含前置、下游和后置；必须把变换后的 Stage 返回给宿主。只在原 Future 上挂一个未返回的 thenApply 不会替换宿主收到的结果。
- 调用外层仍检查控制和所用插件版本；业务授权、来源及提交校验由宿主操作边界负责，不能依赖可选拦截器执行。

OperationPipeline 不自动拦截任意 Java 方法。宿主在明确操作端口构建它，插件贡献类型匹配的节点；这样可以追踪输入、真实完成及资源拥有关系。

## 事件、存储及配置

Core 事件监听方法继续为 public void，async=true 表示独立任务，不被 pushEventWithFuture 的主任务等待。插件提供可修改决策事件时实现 IPluginScopedEvent，限定接受的插件并令 allowsAsyncListeners 返回 false。总线拒绝对此类事件使用异步监听；修改完成后由宿主冻结、读取取消决定并重新校验。

普通观测事件可以异步，但不能携带凭据或借用控制，也不能通过取消来撤销已发生业务。相同优先级监听器按 Bean 名、方法签名排序。对全局监听仍需自行筛选业务目标；Agent 决策事件额外按实例已安装能力过滤插件归属。

插件自有数据使用对应 EasyData 入口与 DTO；业务 Store 不直接操作 Repository。Agent 权威数据统一走 TeaNekoAgentEasyData。文件配置复用 @FileConfig 与 Core YAML 环境变量解析，敏感值禁止普通导出和日志输出。

错误信息、日志及 JavaDoc 遵守项目中文和版本规范。必要测试覆盖目录冲突、权限、插件版本、真实异步完成和资源关闭，复用既有夹具，不为简单 DTO 或机械分支堆叠测试。
