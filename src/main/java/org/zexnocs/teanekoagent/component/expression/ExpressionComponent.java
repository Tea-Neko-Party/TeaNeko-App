package org.zexnocs.teanekoagent.component.expression;

import org.zexnocs.teanekoagent.component.BuiltinAgentComponent;
import org.zexnocs.teanekoagent.component.TeaNekoAgentComponent;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 使用分段协议、发送节奏和客户端原生表情。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.8.0-l
 */
@TeaNekoAgentComponent(id = "expression", name = "拟人表达", description = "使用分段协议、发送节奏和客户端原生表情。")
@PluginOwned("teaneko.agent")
public final class ExpressionComponent extends BuiltinAgentComponent {
    private final ExpressionCompileSystem compiler;

    /**
     * 创建参数模板，不启动业务或修改存储。
     * @param compiler 原模型结果后的表达编译系统
     */
    public ExpressionComponent(ExpressionCompileSystem compiler) {
        super("expression", Map.ofEntries(
                Map.entry("expressionEnabled", true),
                Map.entry("nativeExpressionsEnabled", true)), List.of(
                new Field("expressionEnabled", "拟人表达", "允许有序分段和发送节奏。", "BOOLEAN"),
                new Field("nativeExpressionsEnabled", "平台表情", "使用当前客户端提供的原生表情。", "BOOLEAN")));
        this.compiler = compiler;
    }

    /** @return 本组件装配的既有业务系统来源。 */
    @Override
    public Set<String> features() {
        return Set.of("teaneko.agent:expression");
    }

    /** @return 本组件参数与表达计划，不写入宿主交互或授权。 */
    @Override
    public Set<org.zexnocs.teanekocore.framework.composition.ComponentKey<?>> keys() {
        return Set.of(key(), ExpressionCompileSystem.PLAN);
    }

    /** @return 模型完成后执行的独立表达系统。 */
    @Override
    public List<org.zexnocs.teanekoagent.plugin.AgentFeature.SystemContribution> systems() {
        return List.of(new org.zexnocs.teanekoagent.plugin.AgentFeature.SystemContribution(
                org.zexnocs.teanekoagent.plugin.AgentFeature.Phase.AFTER_INTERACTION, compiler));
    }
}
