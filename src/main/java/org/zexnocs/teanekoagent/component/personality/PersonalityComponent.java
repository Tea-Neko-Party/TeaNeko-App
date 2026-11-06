package org.zexnocs.teanekoagent.component.personality;

import org.zexnocs.teanekoagent.component.BuiltinAgentComponent;
import org.zexnocs.teanekoagent.component.TeaNekoAgentComponent;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import java.util.List;
import java.util.Map;

/**
 * 独立人格及观察式表达学习的参数声明，不依赖记忆组件是否安装。
 *
 * <br>2026/11/06：声明组件显示优先级，运行依赖和装配行为不变。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.8.4-l
 */
@TeaNekoAgentComponent(id = "personality", order = 30, name = "人格", description = "提供身份、说话风格、人格补正及独立观察学习。")
@PluginOwned("teaneko.agent")
public final class PersonalityComponent extends BuiltinAgentComponent {
    private final PersonalityResolveSystem resolver;
    /**
     * 创建人格参数模板，不在登记时访问人格存储。
     * @param resolver 按当前聊天解析人格的独立系统
     */
    public PersonalityComponent(PersonalityResolveSystem resolver) {
        super("personality", Map.of("personalityId", "", "customPersonalityEnabled", false,
                        "customPersonality", "", "customSpeakingStyle", "", "personalityCorrectionsEnabled", true,
                        "personalityLearningEnabled", true), List.of(
                new Field("personalityId", "基础人格", "留空使用系统默认人格。", "TEXT"),
                new Field("customPersonalityEnabled", "自定义人格", "使用本组件的描述与说话风格。", "BOOLEAN"),
                new Field("customPersonality", "人格描述", "最多 4096 字符，不授予权限。", "LONG_TEXT"),
                new Field("customSpeakingStyle", "说话风格", "最多 4096 字符。", "LONG_TEXT"),
                new Field("personalityCorrectionsEnabled", "应用补正", "应用已经接受的人格补正。", "BOOLEAN"),
                new Field("personalityLearningEnabled", "观察学习", "从真实对话学习表达习惯。", "BOOLEAN")));
        this.resolver = resolver;
    }

    /** @return 人格参数与运行快照的独立数据拥有边界。 */
    @Override
    public java.util.Set<org.zexnocs.teanekocore.framework.composition.ComponentKey<?>> keys() {
        return java.util.Set.of(key(), PersonalityResolveSystem.SNAPSHOT);
    }

    /** @return 仅在本组件安装后执行的实际解析系统。 */
    @Override
    public List<org.zexnocs.teanekoagent.plugin.AgentFeature.SystemContribution> systems() {
        return List.of(new org.zexnocs.teanekoagent.plugin.AgentFeature.SystemContribution(
                org.zexnocs.teanekoagent.plugin.AgentFeature.Phase.BEFORE_PROMPT, resolver));
    }
}
