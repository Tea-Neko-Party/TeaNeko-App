package org.zexnocs.teanekoagent.component.personality.adapter;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.component.personality.IAgentPersonalitySource;
import org.zexnocs.teanekoagent.component.personality.IStyleObservationStore;
import org.zexnocs.teanekoagent.component.personality.StyleObservations;
import org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolContext;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentTool;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentToolClass;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekocore.framework.description.Description;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * 以普通注解工具查看当前 scope 的表达学习进展，不输出原话或跨群样本。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：观察资料工具仅对安装人格组件的轮次开放。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.1-l
 * @version 4.8.0-l
 */
@TeaNekoAgentToolClass(value = "style-observations", toolPackage = "agent.personality", requires = "personality")
public final class StyleObservationTools {
    private final IStyleObservationStore store;
    private final IAgentPersonalitySource personalities;
    private final Function<AgentScope, Boolean> reads;
    private final Executor executor;
    private final Clock clock;
    /**
     * 注入当前读取策略，关闭学习不隐式关闭查看已有观察。
     * @param store 观察存储
     * @param personalities 原人格源
     * @param reads 当前真实读取权限
     * @param executor 原业务域
     * @param clock 当前时钟
     */
    public StyleObservationTools(IStyleObservationStore store, IAgentPersonalitySource personalities, Function<AgentScope, Boolean> reads, Executor executor, Clock clock) {
        this.store = store; this.personalities = personalities; this.reads = reads; this.executor = executor; this.clock = clock;
    }
    /**
     * 判断当前 scope 的真实读取资格。
     * @param context 原宿主上下文
     * @return 当前允许读取时为真
     */
    public boolean canRead(TeaNekoAgentToolContext context) { return Boolean.TRUE.equals(reads.apply(context.request().actor().scope())); }
    /**
     * 查询前后复核同一人格，模型不能指定其他 scope 或补正空间。
     * @param context 原工具上下文
     * @return 当前人格的有限观察摘要
     */
    @TeaNekoAgentTool(value = "personality_observations", condition = "canRead")
    @Description("查看当前群/私聊人格从真实人类发言中观察到的表达模式、适用场景、独立消息/片段数、样本作者 UUID 和是否已采用。最多16项，不导出原话，不查询其他群，不代表完整观察历史。用户画像请用 memory_user_profile。")
    public CompletionStage<List<StyleObservations.View>> inspect(TeaNekoAgentToolContext context) {
        return CompletableFuture.supplyAsync(() -> {
            var control = context.requireInvocation().control(); control.checkActive();
            if (!canRead(context)) { throw new IllegalStateException("当前作用域不能查看表达观察。"); }
            var scope = context.request().actor().scope();
            return personalities.resolve(scope, control).thenComposeAsync(basis -> control.guard(store.inspect(scope, basis, clock.instant()))
                    .thenComposeAsync(values -> personalities.verify(scope, basis, control).thenApply(ignored -> {
                        control.checkActive(); if (!canRead(context)) { throw new IllegalStateException("表达观察读取资格已失效。"); } return values;
                    }), executor), executor);
        }, executor).thenCompose(stage -> stage);
    }
}
