package org.zexnocs.teanekoplugin.agent.social.affinity;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.springframework.beans.factory.ObjectProvider;
import org.zexnocs.teanekoagent.component.TeaNekoAgentComponent;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekoplugin.agent.cognition.memory.runtime.IAgentMemoryReferences;
import org.zexnocs.teanekoplugin.agent.social.social.SocialAccess;
import org.zexnocs.teanekoplugin.agent.social.social.SocialComponent;
import org.zexnocs.teanekoplugin.agent.social.social.SocialState;
import org.zexnocs.teanekoplugin.agent.social.social.SocialStateService;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

/**
 * 独立、可序列化的亲近程度配置；状态按空间及真实用户共享。
 * <br>2026/11/06：通过本轮模型工具评估有效互动，限制单次变化与评估频率。
 * <br>2026/11/06：声明阶段不创建运行服务，避免组件目录与空间策略循环依赖。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.9.0-l
 */
@TeaNekoAgentComponent(id = "affinity", order = 45, name = "好感度", description = "积累与你互动的亲近程度，影响称呼、关心与表达。", requires = {"memory", "tool"})
@PluginOwned("agent.social")
public final class AffinityComponent extends SocialComponent {
    /**
     * 2026/11/06：只保存服务提供者，实际状态处理时再取得对应单例。
     * @param states 事务状态服务提供者
     * @param access 当前配置服务提供者
     * @param references 空间复核服务提供者
     */
    public AffinityComponent(ObjectProvider<SocialStateService> states, ObjectProvider<SocialAccess> access,
                             ObjectProvider<IAgentMemoryReferences> references) {
        super("affinity", Map.of("enabled", true, "initial", 30, "positiveStep", 2, "negativeStep", 3, "cooldownMinutes", 60), List.of(
                new Field("enabled", "启用好感度", "不改变基础人格与操作权限。", "BOOLEAN"),
                new Field("initial", "初始亲近度", "0–100；只影响尚未建立关系的用户。", "NUMBER"),
                new Field("positiveStep", "善意变化上限", "1–5；重复问候不加分。", "NUMBER"),
                new Field("negativeStep", "受伤变化上限", "1–5；不因未回复自动扣分。", "NUMBER"),
                new Field("cooldownMinutes", "评估冷却分钟", "15–1440；同一消息只评估一次。", "NUMBER")), states, access, references);
    }
    /**
     * @param value 组件参数
     * @throws IllegalArgumentException 数值范围不合理时
     */
    @Override public void validate(JsonNode value) {
        super.validate(value);
        range(value, "initial", 30, 0, 100);
        range(value, "positiveStep", 2, 1, 5);
        range(value, "negativeStep", 3, 1, 5);
        range(value, "cooldownMinutes", 60, 15, 1440);
    }
    /**
     * @param state 当前关系
     * @param settings 当前参数
     * @return 可读的亲近程度
     */
    @Override protected String describe(SocialState state, JsonNode settings) {
        String closeness = state.affinity() < 20 ? "谨慎疏远" : state.affinity() < 45 ? "初识友好" : state.affinity() < 70 ? "熟悉亲近" : "非常亲近";
        return "当前亲近程度：" + closeness + "（" + state.affinity() + "/100）。根据原人格自然表现，不向用户机械报告分数。";
    }
}
