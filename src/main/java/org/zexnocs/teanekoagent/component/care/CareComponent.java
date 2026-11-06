package org.zexnocs.teanekoagent.component.care;

import org.zexnocs.teanekoagent.component.DeclaredAgentComponent;
import org.zexnocs.teanekoagent.component.TeaNekoAgentComponent;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import tools.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

/**
 * 在原主动活动原则内按亲近程度、离线时长与未回复情况提供关怀候选。
 * <br>2026/11/06：用户长期不回应时有次数上限，不自动创建私聊或跨空间追问。
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.8.6-l
 */
@TeaNekoAgentComponent(id = "care", order = 81, name = "主动关怀",
        description = "亲近用户久未联系时自然问候，未回复时有限关心；仍遵守静默、冷却与 ACTIVE 模式。",
        requires = {"proactive", "affinity"})
@PluginOwned("teaneko.agent")
public final class CareComponent extends DeclaredAgentComponent {
    /** 建立可序列化的主动关怀策略，不启动额外定时任务。 */
    public CareComponent() {
        super("care", Map.of("enabled", true, "minimumAffinity", 70, "missingHours", 48, "unansweredHours", 6,
                        "cooldownHours", 24, "maxReminders", 2), List.of(
                new Field("enabled", "启用主动关怀", "只适用于已经确认过真实入站的私聊。", "BOOLEAN"),
                new Field("minimumAffinity", "亲近度门槛", "0–100；达到门槛才考虑主动联系。", "NUMBER"),
                new Field("missingHours", "久未联系小时", "1–720；用户久未来信时可表达想念。", "NUMBER"),
                new Field("unansweredHours", "等待回复小时", "1–168；已确认发言后用户未回复时可关心。", "NUMBER"),
                new Field("cooldownHours", "关怀间隔小时", "1–720；同时受主动活动的更严格冷却限制。", "NUMBER"),
                new Field("maxReminders", "未回复关怀上限", "1–3；达到后停止，收到真实来信才重新计数。", "NUMBER")));
    }
    /**
     * @param value 参数
     * @throws IllegalArgumentException 关怀阈值越界时
     */
    @Override public void validate(JsonNode value) {
        super.validate(value);
        check(value, "minimumAffinity", 70, 0, 100);
        check(value, "missingHours", 48, 1, 720);
        check(value, "unansweredHours", 6, 1, 168);
        check(value, "cooldownHours", 24, 1, 720);
        check(value, "maxReminders", 2, 1, 3);
    }
    /**
     * @param value 参数
     * @param key 字段
     * @param initial 初值
     * @param min 下界
     * @param max 上界
     */
    private static void check(JsonNode value, String key, int initial, int min, int max) {
        int number = value.path(key).asInt(initial);
        if (number < min || number > max) { throw new IllegalArgumentException("关怀参数 " + key + " 必须在 " + min + " 至 " + max + " 之间。"); }
    }
}
