package org.zexnocs.teanekoagent.component.personality;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.component.AgentComponents;
import org.zexnocs.teanekocore.framework.composition.*;

import java.util.Set;
import java.util.concurrent.CompletionStage;

/**
 * 只为已安装人格组件的轮次解析人格，在独立数据键提交快照，不修改其他组件。
 *
 * <br>2026/11/04：更新分层重构后的类型引用，保持原调用与生命周期行为。
 *
 * <br>2026/11/05：移除包迁移后重复或未使用的类型导入。
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.8.0-l
 */
@Component
public final class PersonalityResolveSystem implements IEntitySystem {
    public static final ComponentKey<AgentPersonalitySnapshot> SNAPSHOT =
            ComponentKey.of("agent.component.personality.snapshot", AgentPersonalitySnapshot.class);
    private final ObjectProvider<IAgentPersonalitySource> sources;

    /** @param sources 原人格服务，执行时取得以避免宿主构造循环 */
    public PersonalityResolveSystem(ObjectProvider<IAgentPersonalitySource> sources) {
        this.sources = sources;
    }

    /** @return 数据读取、独立写入及稳定系统标识 */
    @Override
    public SystemDescriptor descriptor() {
        return new SystemDescriptor("agent.component.personality.resolve", Set.of(AgentComponents.REQUEST),
                Set.of(SNAPSHOT), Set.of());
    }

    /**
     * @param context 固定轮次与借用控制
     * @return 完成后提交的人格数据补丁
     */
    @Override
    public CompletionStage<SystemResult> execute(SystemContext context) {
        var scope = context.snapshot().require(AgentComponents.REQUEST).actor().scope();
        return context.control().guard(sources.getObject().resolve(scope, context.control()))
                .thenApply(value -> SystemResult.continueWith(ComponentPatch.builder().put(SNAPSHOT, value).build()));
    }
}
