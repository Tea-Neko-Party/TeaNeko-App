# Core 插件编写

## 入口与装配

一个插件提供 PluginModule Bean，其 descriptor() 返回 PluginDescriptor：

| 字段 | 含义 |
| --- | --- |
| id | 全局稳定插件 ID，格式为小写字母开头的字母、数字、点或短横线，最长 64 位 |
| version | 插件自身实现版本，不参与宿主 SPI 兼容判断 |
| apiVersion | 当前必须为 1，不支持的版本直接拒绝 |
| dependencies | 必需插件 ID，目录校验拒绝缺失依赖、环及重名 |
| mandatory | 是否为宿主基础模块；普通四参数构造默认 false，基础模块不能通过逻辑开关关闭 |

Core 的身份为 `teaneko.core`，Agent 为 `teaneko.agent`。入口参考 [PluginConfiguration](../../../src/main/java/org/zexnocs/teanekocore/plugin/PluginConfiguration.java)。metadata 回调只返回声明，不连接服务或启动后台任务。

在工程默认扫描包 org.zexnocs 内可以使用配置类。外部依赖 JAR 必须通过显式 @Import 或 Spring Boot 自动配置导入插件配置，提供所需 Bean；仅添加 JAR 或放入任意目录不会自动发现插件。若外部包还包含供 Core 类扫描使用的事件、配置声明等类型，通过 AutoConfigurationPackages 明确登记扫描根，例如在入口配置使用 @AutoConfigurationPackage(basePackageClasses = 插件标记类型.class)。Bean 导入与类扫描范围分别配置，不能只完成其一。

事件监听器、命令、TaskStage 仍使用各自原注解。@EventListener 本身带组件语义，避免同时被默认组件扫描和 @Bean 重复装配；可像示例一样给配置与监听器使用相同 ConditionalOnProperty 条件，或由外部未扫描包的插件配置显式提供 Bean。

## 归属与逻辑生命周期

在命令类、事件监听类或 TaskStage 类添加 @PluginOwned("插件ID")；方法上的归属优先于类声明。模型适配器也使用该注解。归属必须在 PluginRegistry 注册，否则实际调用明确失败。未声明归属的历史 Bean 保留宿主行为，第三方不得借此绕过启停。

PluginRegistry.list() 返回描述、包含依赖的有效 enabled 和 revision；setEnabled(id, enabled) 是供可信宿主使用的逻辑路由入口。普通开关改变只影响相应插件及依赖它的调用，不使无关插件全部失效。该入口不是直接暴露给浏览器的管理命令；未来管理适配仍须做授权、预期版本及审计。

启动禁用列表使用 tea-neko.plugins.disabled，可配置为 YAML 列表或 properties 逗号分隔值。未知 ID 或尝试关闭 mandatory 模块会拒绝启动。运行期 setEnabled 只修改进程内状态，不自动写配置；重启后重新应用启动配置。

capture(id) 固定插件及传递依赖版本，check(token) 拒绝禁用或换代后的旧调用。禁用再启用不会使旧令牌恢复有效。invoke(id, stageFactory) 在启动及真实异步完成时检查，适合封装自己的调用端口。资源提交前仍应检查原业务控制、授权及当前版本；提交已经发生时不因随后检查失败声称回滚。

事件监听器禁用后不参与新通知；命令在启动及其返回 Stage 完成时复核；TaskStage 在同步进入与退出时复核；模型服务等待实际模型 Future 后复核。禁用不会自动中断所有外部 IO，也不销毁 Bean。IO 适配器响应借用 ExecutionControl 的终止，自有连接等由 Bean 销毁回调关闭，共享执行器不由插件关闭。

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

OperationPipeline<I,O> 接受 PluginRegistry、有序 Binding<I,O> 列表和 shortCircuit 开关。每个 Binding 声明 pluginId、局部 id、order 和 AsyncInterceptor；执行时传入 input、借用 ExecutionControl 与实际 AsyncOperation。

高 order 先进入，随后按 pluginId、id 排序；同一插件内的节点重名拒绝。节点实际语义是 intercept(input, next) → CompletionStage<O>，可以通过 thenCompose 等待异步前置，通过 thenApply/thenCompose 转换最终结果，通过明确的异常分支处理普通业务失败。

- next 至多调用一次，重复调用即使被插件忽略也不能成为成功；节点自身阶段完成后不能迟到启动 next。
- 已启动的下游会被等待，防止插件启动实际工作后提前返回成功；失败传播也等待已启动下游。仍可能存在不响应取消的外部 IO，不把 Future 结束等同物理 IO 已撤销。
- shortCircuit=false 要求执行 next；只有宿主明确允许缓存等只读短路时才使用 true。写操作不能自行增加自动重试。
- 返回的 Stage 包含前置、下游和后置；必须把变换后的 Stage 返回给宿主。只在原 Future 上挂一个未返回的 thenApply 不会替换宿主收到的结果。
- 调用外层仍检查控制和所用插件版本；业务授权、来源及提交校验由宿主操作边界负责，不能依赖可选拦截器执行。

OperationPipeline 不自动拦截任意 Java 方法。宿主在明确操作端口构建它，插件贡献类型匹配的节点；这样可以追踪输入、真实完成及资源拥有关系。

## 事件、存储及配置

Core 事件监听方法继续为 public void，async=true 表示独立任务，不被 pushEventWithFuture 的主任务等待。插件提供可修改决策事件时实现 PluginScopedEvent，限定接受的插件并令 allowsAsyncListeners 返回 false。总线拒绝对此类事件使用异步监听；修改完成后由宿主冻结、读取取消决定并重新校验。

普通观测事件可以异步，但不能携带凭据或借用控制，也不能通过取消来撤销已发生业务。相同优先级监听器按 Bean 名、方法签名排序。对全局监听仍需自行筛选业务目标；Agent 决策事件额外按实例已安装能力过滤插件归属。

插件自有数据使用对应 EasyData 入口与 DTO；业务 Store 不直接操作 Repository。Agent 权威数据统一走 TeaNekoAgentEasyData。文件配置复用 @FileConfig 与 Core YAML 环境变量解析，敏感值禁止普通导出和日志输出。

错误信息、日志及 JavaDoc 遵守项目中文和版本规范。必要测试覆盖目录冲突、权限、插件版本、真实异步完成和资源关闭，复用既有夹具，不为简单 DTO 或机械分支堆叠测试。
