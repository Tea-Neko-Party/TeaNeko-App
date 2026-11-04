package org.zexnocs.teanekoagent.component.proactive;

import org.zexnocs.teanekoagent.component.BuiltinAgentComponent;
import org.zexnocs.teanekoagent.component.TeaNekoAgentComponent;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import java.util.List;
import java.util.Map;

/**
 * 在 ACTIVE 模式下按静默时段、冷却和退避产生自主候选。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.8.0-l
 */
@TeaNekoAgentComponent(id = "proactive", name = "主动活动", description = "在 ACTIVE 模式下按静默时段、冷却和退避产生自主候选。")
@PluginOwned("teaneko.agent")
public final class ProactiveComponent extends BuiltinAgentComponent {
    /** 创建本组件独立参数模板，不启动业务或修改存储。 */
    public ProactiveComponent() {
        super("proactive", Map.ofEntries(
                Map.entry("proactiveTimedEnabled", true),
                Map.entry("proactiveInterjectionEnabled", true),
                Map.entry("activityTimeZone", "UTC"),
                Map.entry("proactiveCooldownSeconds", 900),
                Map.entry("proactiveIntervalSeconds", 900),
                Map.entry("proactiveAttemptCooldownSeconds", 60),
                Map.entry("proactiveQuietStartHour", 23),
                Map.entry("proactiveQuietEndHour", 7)), List.of(
                new Field("proactiveTimedEnabled", "定时主动发言", "产生定时自主候选，不补跑。", "BOOLEAN"),
                new Field("proactiveInterjectionEnabled", "允许接话", "允许对真实入站提出接话候选。", "BOOLEAN"),
                new Field("activityTimeZone", "活动时区", "合法 ZoneId，例如 Asia/Shanghai。", "TEXT"),
                new Field("proactiveCooldownSeconds", "发言冷却秒数", "1 至 86400。", "NUMBER"),
                new Field("proactiveIntervalSeconds", "候选间隔秒数", "60 至 86400。", "NUMBER"),
                new Field("proactiveAttemptCooldownSeconds", "退避秒数", "60 至 86400。", "NUMBER"),
                new Field("proactiveQuietStartHour", "静默开始小时", "0 至 23。", "NUMBER"),
                new Field("proactiveQuietEndHour", "静默结束小时", "0 至 23，相同表示不启用静默。", "NUMBER")));
    }
}
