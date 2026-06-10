# 一. Agent Event 模块结构介绍

`agent.event` 模块把 Agent 运行时的必要节点接入 `teanekocore.event`。监听器可以在这些节点上读取或修改事件 data，也可以取消事件来跳过默认动作。

| 类 | 作用 |
|:---:|---|
| `AgentTurnEvent` / `AgentTurnData` | 整轮 Agent 对话事件，默认执行 `AgentRuntimeService.__handleForEvent(...)`。 |
| `AgentModelCallEvent` / `AgentModelCallData` | 单次模型调用事件，默认执行 `LLMModelService.call(prompt)`。 |
| `AgentToolCallEvent` / `AgentToolCallData` | 单次工具调用事件，默认执行 `AgentToolRegistryService.call(toolCall)`。 |
| `AgentOutboundMessageEvent` / `AgentOutboundMessageData` | 出站消息生成事件，默认不执行额外动作。 |

# 二. 事件处理流程

```markdown
1. AgentRuntimeService.handle(...) 创建 AgentTurnData。
2. Runtime 推送 AgentTurnEvent 并等待事件处理完成。
3. AgentTurnEvent 的监听器可以修改 data 或取消事件。
4. 未取消时，AgentTurnEvent._afterNotify() 执行默认运行时逻辑。
5. 默认运行时逻辑在每轮模型调用前推送 AgentModelCallEvent。
6. 模型返回 tool_calls 时，逐个推送 AgentToolCallEvent。
7. 生成可见回复后，推送 AgentOutboundMessageEvent。
8. Runtime 从 AgentTurnData 中读取最终出站消息并返回。
```

# 三. 事件 Key

| 事件 | key | namespace |
|---|---|---|
| `AgentTurnEvent` | `teaneko-agent-turn` | `teaneko-agent-runtime` |
| `AgentModelCallEvent` | `teaneko-agent-model-call` | `teaneko-agent-runtime` |
| `AgentToolCallEvent` | `teaneko-agent-tool-call` | `teaneko-agent-runtime` |
| `AgentOutboundMessageEvent` | `teaneko-agent-outbound-message` | `teaneko-agent-runtime` |

# 四. 监听示例

```java
@EventListener
public class AgentAuditListener {
    @EventHandler(priority = 100)
    public void beforeModelCall(AgentModelCallEvent event) {
        var data = event.getData();
        // 可读取或替换 data.getPrompt()
    }
}
```

# 五. 取消规则

| 事件 | 取消后的行为 |
|---|---|
| `AgentTurnEvent` | 跳过默认对话处理；如果 data 中已有 outboundMessage，Runtime 仍会返回它。 |
| `AgentModelCallEvent` | 跳过默认模型调用；如果 data 中没有 result，本轮运行会停止。 |
| `AgentToolCallEvent` | 跳过默认工具调用；如果 data 中没有 result，会写入 `Tool execution cancelled.`。 |
| `AgentOutboundMessageEvent` | Runtime 会读取 data 中的 outboundMessage；监听器可设为 `null` 来取消回复。 |

# 六. 注意事项

| 项 | 说明 |
|---|---|
| 同步等待 | Runtime 使用 `pushEventWithFuture(...).finish().join()` 等待事件完成，保持 `handle(...)` 同步返回。 |
| 异步监听器 | core 事件系统允许异步监听器，但异步修改 data 可能晚于默认动作，不适合修改 Prompt 或工具结果。 |
| 默认动作位置 | 默认模型调用和工具调用放在事件 `_afterNotify()`，高优先级同步监听器可以在默认动作前修改 data。 |
| 结果替换 | 要替换模型或工具结果，监听器应先写入 data 结果，再取消事件。 |
