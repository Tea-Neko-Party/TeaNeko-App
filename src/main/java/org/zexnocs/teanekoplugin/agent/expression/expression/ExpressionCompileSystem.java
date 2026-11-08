package org.zexnocs.teanekoplugin.agent.expression.expression;

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.zexnocs.teanekoplugin.agent.expression.expression.ExpressionRuntime;
import org.zexnocs.teanekoplugin.agent.expression.expression.ReplyPlans;
import org.zexnocs.teanekocore.framework.composition.ComponentKey;
import org.zexnocs.teanekocore.framework.composition.ComponentPatch;
import org.zexnocs.teanekocore.framework.composition.IEntitySystem;
import org.zexnocs.teanekocore.framework.composition.SystemContext;
import org.zexnocs.teanekocore.framework.composition.SystemDescriptor;
import org.zexnocs.teanekocore.framework.composition.SystemResult;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.component.AgentComponents;
import org.zexnocs.teanekocore.framework.composition.*;

import java.util.Set;
import java.util.concurrent.CompletionStage;

/**
 * 仅为已安装表达组件的轮次编译发送计划；不发送消息、不写入其他组件的数据。
 * <br>2026/11/04：更新分层重构后的类型引用，保持原调用与生命周期行为。
 * <br>2026/11/05：移除包迁移后重复或未使用的类型导入。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.9.0-l
 */
@Component
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.expression")
public final class ExpressionCompileSystem implements IEntitySystem {
    public static final ComponentKey<ReplyPlans.Approved> PLAN =
            ComponentKey.of("agent.component.expression.plan", ReplyPlans.Approved.class);
    private final ObjectProvider<ExpressionRuntime> runtime;

    /**
     * @param runtime 原协议编译服务，延迟取得以避免组件目录与宿主构造循环
     */
    public ExpressionCompileSystem(ObjectProvider<ExpressionRuntime> runtime) {
        this.runtime = runtime;
    }

    /** @return 模型结果到本组件独立计划的输入与写入声明。 */
    @Override
    public SystemDescriptor descriptor() {
        return new SystemDescriptor("agent.component.expression.compile", Set.of(AgentComponents.REQUEST,
                AgentComponents.PROMPT_INPUT, AgentComponents.INTERACTION), Set.of(PLAN), Set.of());
    }

    /**
     * @param context 原轮次快照与取消控制
     * @return 已验证完整计划的补丁；原普通回答不创建表达计划
     */
    @Override
    public CompletionStage<SystemResult> execute(SystemContext context) {
        var snapshot = context.snapshot();
        var request = snapshot.require(AgentComponents.REQUEST)
                .withMemoryReference(snapshot.find(AgentComponents.CONTEXT_REFERENCE).orElse(null));
        return runtime.getObject().compile(request, snapshot.require(AgentComponents.PROMPT_INPUT),
                snapshot.require(AgentComponents.INTERACTION), context.control()).thenApply(plan -> {
            var patch = plan == null ? ComponentPatch.empty() : ComponentPatch.builder().put(PLAN, plan).build();
            return SystemResult.continueWith(patch);
        });
    }
}
