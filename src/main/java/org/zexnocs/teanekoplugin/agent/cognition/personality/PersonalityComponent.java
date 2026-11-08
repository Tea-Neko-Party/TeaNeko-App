package org.zexnocs.teanekoplugin.agent.cognition.personality;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.zexnocs.teanekoagent.component.DeclaredAgentComponent;
import org.zexnocs.teanekoagent.component.TeaNekoAgentComponent;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import java.util.List;
import java.util.Map;

/**
 * 独立人格及观察式表达学习的参数声明，不依赖记忆组件是否安装。
 * <br>2026/11/06：声明组件显示优先级，运行依赖和装配行为不变。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * <br>2026/11/07：参数类型复用通用底座，业务约束由本组件独立维护。
 *
 * <br>2026/11/08：人格由类型化上下文端口解析，组件不再越权写入宿主数据键。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.9.0-l
 */
@TeaNekoAgentComponent(id = "personality", order = 30, name = "人格", description = "提供身份、说话风格、人格补正及独立观察学习。")
@PluginOwned("agent.cognition")
public final class PersonalityComponent extends DeclaredAgentComponent {
    /**
     * 创建人格参数模板，不在登记时访问人格存储。
     */
    public PersonalityComponent() {
        super("personality", Map.of("personalityId", "", "customPersonalityEnabled", false,
                        "customPersonality", "", "customSpeakingStyle", "", "personalityCorrectionsEnabled", true,
                        "personalityLearningEnabled", true), List.of(
                new Field("personalityId", "基础人格", "留空使用系统默认人格。", "TEXT"),
                new Field("customPersonalityEnabled", "自定义人格", "使用本组件的描述与说话风格。", "BOOLEAN"),
                new Field("customPersonality", "人格描述", "最多 4096 字符，不授予权限。", "LONG_TEXT"),
                new Field("customSpeakingStyle", "说话风格", "最多 4096 字符。", "LONG_TEXT"),
                new Field("personalityCorrectionsEnabled", "应用补正", "应用已经接受的人格补正。", "BOOLEAN"),
                new Field("personalityLearningEnabled", "观察学习", "从真实对话学习表达习惯。", "BOOLEAN")));
    }

    /**
     * 2026/11/07：在声明类型校验后检查本组件业务范围，不依赖宿主字段表。
     * @param parameters 本次独立参数树
     * @throws IllegalArgumentException 当参数类型或业务范围无效时
     */
    @Override
    public void validate(tools.jackson.databind.JsonNode parameters) {
        super.validate(parameters);
        if (parameters.path("personalityId").asString("").length() > 128) {
            throw new IllegalArgumentException("人格 ID 不能超过 128 字符。");
        }
        if (parameters.path("customPersonality").asString("").length() > 4096
                || parameters.path("customSpeakingStyle").asString("").length() > 4096) {
            throw new IllegalArgumentException("自定义人格文本不能超过 4096 字符。");
        }
    }
}
