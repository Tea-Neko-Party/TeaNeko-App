# 工具定义、可信绑定与异步执行

注册继续使用 `ILLMTool` Bean、`@LLMToolProvider` / `ILLMToolProvider` 与 `@LLMToolMapping`，参数继续使用 `ILLMFunctionParameter` 和现有 schema mapper。`LLMToolService` 是全局发现目录；一次交互需要显式选择工具，再构造 `LLMToolSet`。

`LLMToolBinding` 固定定义和执行器引用，递归复制 schema 及其集合，拒绝递归 schema、缺少定义的 required 字段、未知参数类型及非 object 根参数。模型可见的 `definition()` 没有执行器，不能直接执行。`LLMToolSet.definitions()` 的顺序与选入顺序相同，同名工具拒绝装配。执行只匹配选入绑定，没有全局查名回退。

```java
var selected = toolService.getTools("memory-query").values().stream()
        .map(LLMToolBinding::from).toList();
var tools = new LLMToolSet(objectMapper, selected);
// 把 tools.definitions() 放入本轮模型选项；宿主创建可信上下文。
var context = new ToolInvocationContext(requestId, trustedSnapshot, executionControl);
CompletionStage<ToolInvocationResult> result = tools.execute(toolCall, context);
```

手写异步工具可设置 `LLMTool.builder().contextualExecutor(...)`，或显式 `new LLMToolBinding(definition, executor)`；自定义 `ILLMTool` Bean 也可同时实现 `IContextualToolExecutor`。旧同步执行器经 `IContextualToolExecutor.synchronous(...)` 适配，在调用线程运行，适合短小同步逻辑。需要 I/O 的工具应提供真正的异步适配器；这里不隐含创建线程池或阻塞等待。

对普通 `LLMTool`，绑定会捕获当前 executor，而非稍后从可变 Bean 重取。反射执行器固定当前 provider 实例与方法，因此注册表重载不会替换已绑定的执行目标。自定义执行器仍须管理好自己引用的状态；定义快照不深拷贝任意业务服务。

## 参数与可信上下文

`ToolInvocationContext` 仅由宿主创建，包含 requestId、Core `EntitySnapshot` 及借用的 `ExecutionControl`。业务层用类型化 `ComponentKey<T>` 定义自己的身份/namespace/授权快照，LLM 层不导入这些业务类型。组件必须是不可变数据；凭据、Bean、Repository 不属于此上下文。上下文的 JSON 和 toString 只给出脱敏描述，不能放入 Prompt、模型参数或调试内容快照。

注解方法可以这样声明：

```java
@LLMToolMapping("remember")
public CompletionStage<WriteReceipt> remember(String fact, ToolInvocationContext context) {
    // 从 context.bindings() 获取宿主绑定；业务服务继续验证授权和写入条件。
    return memoryPort.append(fact, context);
}
```

schema 仅包含 fact；`ToolInvocationContext` 从参数 schema 中排除，由反射执行器注入。模型输入不会被反序列化为可信上下文，也不使用 ThreadLocal。执行前按本轮 schema 检查根 object、required、类型、枚举、嵌套 object/array 和 additionalProperties；禁止重复 JSON 键、尾随 JSON、超过 65536 字符的参数和过深结构。可选参数可以省略，显式 null 需有相应协议支持，当前 schema 不声明 nullable。

参数验证不代表业务授权。绑定执行器必须使用可信身份并调用原业务权限链；允许模型提供 subject、查询条件等普通业务参数，也不因此授予修改宿主绑定的权限。

宿主可用 `binding.guarded((call, context) -> ...)` 在实际执行器前加入动态检查；返回新绑定复用原冻结定义，不公开执行器，也不改变 schema。检查在生命周期锁外执行，前后复核阶段控制。宿主拒绝可抛 `LLMOperationRejectedException`；同步、异步以及完成包装中的该异常均向交互层传播以停止余下动作，不转换成普通可恢复错误或成功输出。业务授权守卫不可将任意原始异常信息复制到模型结果。

## 完成、错误和资源归属

注解方法返回 Stage 时先展开再序列化结果：String 保持原文，其他对象转 JSON，Void/null 业务值转为空字符串；声明 Stage 却返回 null 属于失败。同步的 `LLMToolService.call` 保持原便捷语义，但异步/上下文工具会在业务启动前拒绝此入口。

`ToolInvocationResult` 保留不可变的调用 ID、名称、类型和原始参数快照，可通过 `toMessage()` 生成原 ID 的 tool 消息。状态区分成功、非法调用、未暴露、非法参数、失败、取消和超时。错误仅输出固定 JSON 错误码，不包含原始异常消息、堆栈、参数或可信绑定。非法调用不能转成正常历史消息，上层交互须终止或明确处理，避免破坏协议配对。

成功结果默认最多 8000 个 Java 字符，可在 `LLMToolSet` 构造时指定上限（至少 128，以保留完整错误信封）。超长结果裁剪时避免拆开 Unicode 代理对，通过 `truncated` 标明；完成状态仍保留业务的真实完成含义。

执行等待使用 `ExecutionControl.guard`：取消/截止时间会结束等待，迟到结果不会改写已返回结果。工具资源适配器应通过 `context.control().onTermination(...)` 释放订阅/连接，并在资源完成时解除监听。工具不拥有或关闭传入 control；阶段子控制可经 `context.withControl(child)` 传入，继续共享 requestId、绑定与父预算。等待结束不等于外部副作用回滚，也不强制中断任意同步方法。

本入口提供可信异步工具执行。其上的 [交互服务](../interaction/README.md) 已接入有限自动往返、响应校验、完整 transcript、独立阶段时限和共享次数；`LLMToolSet.execute` 本身只响应传入控制。跨请求的持久化重放/幂等由后续 Agent 业务层实现；本入口不会自动重试工具或修改业务历史。

离线使用方为 `LLMContextualToolRegistrationTest`，从真实工具扫描器取得定义，注入宿主上下文，等待受控业务 Stage 并生成结果消息；`LLMToolSetTest` 验证边界、冻结、取消、deadline、错误和截断。两者均不启动 Spring、数据库、供应商模型或聊天平台。
