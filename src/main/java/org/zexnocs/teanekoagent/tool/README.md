# 一. Tool 模块结构介绍

`tool` 模块提供 Agent 视角的工具注册表。它不重新定义工具协议，而是复用 LLM framework 的 `ILLMTool`、`ILLMToolCall` 和 `ILLMToolService`，再合并外部 Agent 工具 provider。

| 类或接口 | 作用 |
|:---:|---|
| `AgentToolRegistryService` | 合并、筛选、查询并执行 Agent 可见工具。 |
| `IAgentToolProvider` | 外部模块注入 Agent 工具的最小接口。 |

# 二. 工具合并流程

```markdown
1. LLMToolService 扫描 Spring 中的 ILLMTool Bean 和 @LLMToolProvider 方法。
2. 外部模块可通过 IAgentToolProvider 返回额外 ILLMTool。
3. AgentToolRegistryService.getTools() 先合并 LLMToolService 工具。
4. 再合并所有 IAgentToolProvider 工具。
5. 同名工具使用先注册优先策略，避免运行时覆盖不确定。
6. AgentRuntimeService 把筛选后的工具列表写入 LLMModelOptions.tools。
7. 模型返回 ILLMToolCall 后，由 AgentToolRegistryService.call(...) 执行。
```

# 三. 核心 API

| API | 说明 |
|---|---|
| `AgentToolRegistryService.getTools()` | 获取全部 Agent 可见工具。 |
| `AgentToolRegistryService.getTools(String toolPackage)` | 按工具包筛选可见工具。 |
| `AgentToolRegistryService.getToolList(String toolPackage)` | 返回工具列表，适合写入 `LLMModelOptions.tools`。 |
| `AgentToolRegistryService.hasTool(String)` | 判断工具是否存在。 |
| `AgentToolRegistryService.getTool(String)` | 根据工具名获取 `ILLMTool`，不存在时抛出异常。 |
| `AgentToolRegistryService.call(ILLMToolCall)` | 执行模型返回的工具调用。 |
| `IAgentToolProvider.getTools()` | 外部工具 provider 返回全部工具。 |
| `IAgentToolProvider.getTools(String)` | 外部工具 provider 按工具包筛选工具。 |

# 四. 工具包规则

| toolPackage | 行为 |
|---|---|
| `all` | 返回所有已注册工具。 |
| 空字符串或 `null` | 不暴露工具。 |
| 其他字符串 | 只返回 `ILLMTool.getToolPackage()` 相等的工具。 |

# 五. 与 LLM framework 的关系

| LLM framework 能力 | Tool 模块使用方式 |
|---|---|
| `ILLMTool` | 作为唯一工具定义类型。 |
| `ILLMToolCall` | 作为模型请求执行工具的唯一输入类型。 |
| `ILLMToolService` | 提供框架扫描到的工具和基础查询能力。 |
| `@LLMToolProvider` | 继续用于注解式工具注册。 |
| `@LLMToolMapping` | 继续用于方法级工具 schema 生成。 |

# 六. 注意事项

| 项 | 说明 |
|---|---|
| 不重复定义 schema | Agent 工具必须包装为 `ILLMTool`，不要创建平行 ToolSpec。 |
| 同名工具 | 当前合并策略保留先出现的工具。需要替换工具时应调整注册来源或名称。 |
| 工具异常 | Runtime 会把异常转换成 tool result 文本回填给模型。 |
| 工具列表长度 | 默认运行时可暴露全部工具，上层可传入更小的 toolPackage 降低干扰。 |
| 外部工具 | 外部 provider 应保持工具名稳定，否则历史 tool call 无法可靠复现。 |
