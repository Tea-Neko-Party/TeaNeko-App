package org.zexnocs.teanekoplugin.agent.cognition.memory.access;

import org.zexnocs.teanekoplugin.agent.cognition.CognitionContext;
// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。


// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoplugin.agent.cognition.memory.fact.MemoryEventTime;
import org.zexnocs.teanekoplugin.agent.cognition.memory.fact.MemoryFactReceipt;
import org.zexnocs.teanekoplugin.agent.cognition.memory.fact.MemoryFactValue;
import org.zexnocs.teanekoagent.runtime.AgentActorContext;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 模型调用前保存用户明确要求的原文，普通聊天和原生入口共用同一可信访问边界。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.9.0-l
 */
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.cognition")
public final class ExplicitMemoryCapture {
    private final MemoryAccessService access;
    private final IMemoryAccessPolicy policies;
    /**
     * 保存借用端口，不创建模型或读取数据。
     * @param access 原可信追加边界
     * @param policies 实际记忆策略
     */
    public ExplicitMemoryCapture(MemoryAccessService access, IMemoryAccessPolicy policies) { this.access = access; this.policies = policies; }
    /**
     * 仅真实当前用户原文和明确保存开关可触发，不扫描历史或模型文字。
     * @param request 当前可信请求
     * @return 本次由前置保存负责时为真
     */
    public boolean handles(AgentRunRequest request) {
        if (CognitionContext.reference(request.memoryReference()) == null || request.userText() == null || request.actor().origin() != AgentActorContext.Origin.USER) {
            return false;
        }
        var policy = policies.read(request.actor().scope());
        return policy.enabled() && policy.addEnabled() && MemoryAddIntent.fromUserText(context(request), request.userText().text()).isPresent();
    }
    /**
     * 保存成功才允许继续模型；冲突或失败不伪装成已保存，也不声称取消撤销原事务。
     * @param request 当前原文与身份
     * @param control 原控制
     * @return 真实提交或明确没有保存意图的阶段
     */
    public CompletionStage<Void> capture(AgentRunRequest request, ExecutionControl control) {
        if (!handles(request)) { return CompletableFuture.completedStage(null); }
        var context = context(request); var intent = MemoryAddIntent.fromUserText(context, Objects.requireNonNull(request.userText()).text()).orElseThrow();
        var value = new MemoryFactValue(MemoryFactValue.Kind.FACT, intent.text(), List.of(), 1, MemoryFactValue.Importance.NORMAL, MemoryEventTime.unknown(), null);
        return access.append(context, "explicit-user-request", intent, value, control).thenAccept(result -> {
            if (result.status() == MemoryFactReceipt.Status.CONFLICT) { throw new IllegalStateException("明确保存与原记录保护冲突，本次没有新增记忆。"); }
        });
    }
    /**
     * 主体、请求和空间只来自原宿主请求。
     * @param request 原请求
     * @return 固定访问上下文
     */
    private static MemoryAccessContext context(AgentRunRequest request) {
        return new MemoryAccessContext(request.requestId(), request.actor(), Objects.requireNonNull(CognitionContext.reference(request.memoryReference())));
    }
}
