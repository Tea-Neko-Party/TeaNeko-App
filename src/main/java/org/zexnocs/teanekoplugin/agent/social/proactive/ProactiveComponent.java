package org.zexnocs.teanekoplugin.agent.social.proactive;

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.zexnocs.teanekoagent.component.DeclaredAgentComponent;
import org.zexnocs.teanekoagent.component.TeaNekoAgentComponent;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import java.util.List;
import java.util.Map;

/**
 * 在 ACTIVE 模式下按静默时段、冷却和退避产生自主候选。
 * <br>2026/11/06：声明组件显示优先级，运行依赖和装配行为不变。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * <br>2026/11/07：参数类型复用通用底座，业务约束由本组件独立维护。
 * <br>2026/11/10：独立声明候选总时限，覆盖排队、资料准备和生成交付。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.9.1-l
 */
@TeaNekoAgentComponent(id = "proactive", order = 80, name = "主动活动", description = "在 ACTIVE 模式下按静默时段、冷却和退避产生自主候选。")
@PluginOwned("agent.social")
public final class ProactiveComponent extends DeclaredAgentComponent {
    /** 创建本组件独立参数模板，不启动业务或修改存储。 */
    public ProactiveComponent() {
        super("proactive", Map.ofEntries(
                Map.entry("proactiveTimedEnabled", true),
                Map.entry("proactiveInterjectionEnabled", true),
                Map.entry("activityTimeZone", "UTC"),
                Map.entry("proactiveCooldownSeconds", 900),
                Map.entry("proactiveIntervalSeconds", 900),
                Map.entry("proactiveAttemptCooldownSeconds", 60),
                // 2026/11/10：替代原硬编码三十秒；模型阶段仍受自身更短时限限制。
                Map.entry("proactiveTimeoutSeconds", 90),
                Map.entry("proactiveQuietStartHour", 23),
                Map.entry("proactiveQuietEndHour", 7)), List.of(
                new Field("proactiveTimedEnabled", "定时主动发言", "产生定时自主候选，不补跑。", "BOOLEAN"),
                new Field("proactiveInterjectionEnabled", "允许接话", "允许对真实入站提出接话候选。", "BOOLEAN"),
                new Field("activityTimeZone", "活动时区", "合法 ZoneId，例如 Asia/Shanghai。", "TEXT"),
                new Field("proactiveCooldownSeconds", "发言冷却秒数", "1 至 86400。", "NUMBER"),
                new Field("proactiveIntervalSeconds", "候选间隔秒数", "60 至 86400。", "NUMBER"),
                new Field("proactiveAttemptCooldownSeconds", "退避秒数", "60 至 86400。", "NUMBER"),
                new Field("proactiveTimeoutSeconds", "候选总时限（秒）", "10 至 180，默认 90；包括排队、资料准备和生成交付。"
                        + "模型请求与目标发送队列有独立时限，取先到期者。", "NUMBER"),
                new Field("proactiveQuietStartHour", "静默开始小时", "0 至 23。", "NUMBER"),
                new Field("proactiveQuietEndHour", "静默结束小时", "0 至 23，相同表示不启用静默。", "NUMBER")));
    }

    /**
     * 2026/11/07：在声明类型校验后检查本组件业务范围，不依赖宿主字段表。
     * @param parameters 本次独立参数树
     * @throws IllegalArgumentException 当参数类型或业务范围无效时
     */
    @Override
    public void validate(tools.jackson.databind.JsonNode parameters) {
        super.validate(parameters);
        try {
            //noinspection ResultOfMethodCallIgnored
            java.time.ZoneId.of(parameters.path("activityTimeZone").asString("UTC"));
        } catch (java.time.DateTimeException invalid) {
            throw new IllegalArgumentException("活动时区必须是合法的 ZoneId，例如 UTC 或 Asia/Shanghai。");
        }
        range(parameters, "proactiveCooldownSeconds", 1, 86400);
        range(parameters, "proactiveIntervalSeconds", 60, 86400);
        range(parameters, "proactiveAttemptCooldownSeconds", 60, 86400);
        // 2026/11/10：总时限保持有界，不能通过组件配置无限占用候选槽位。
        range(parameters, "proactiveTimeoutSeconds", 10, 180);
        range(parameters, "proactiveQuietStartHour", 0, 23);
        range(parameters, "proactiveQuietEndHour", 0, 23);
    }

    /**
     * 校验本组件可选整数参数，拒绝溢出和越界值。
     * @param parameters 已通过类型校验的参数
     * @param field 当前字段
     * @param minimum 最小值
     * @param maximum 最大值
     * @throws IllegalArgumentException 当整数超出有效范围时
     */
    private static void range(tools.jackson.databind.JsonNode parameters, String field, int minimum, int maximum) {
        var value = parameters.get(field);
        if (value != null && (!value.canConvertToInt() || value.intValue() < minimum || value.intValue() > maximum)) {
            throw new IllegalArgumentException("主动活动参数 " + field + " 超出允许范围。");
        }
    }
}
