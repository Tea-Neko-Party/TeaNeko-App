package org.zexnocs.teanekoplugin.agent_example;

import org.zexnocs.teanekoagent.plugin.event.AgentPromptPreparingEvent;
import org.zexnocs.teanekocore.event.core.*;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.zexnocs.teanekoagent.agent.prompt.AgentPromptFragment;

/**
 * 展示可修改事件的有序同步监听，仅对安装示例能力的实例生效。
 * 与示例配置使用同一启用条件，避免配置关闭后监听器仍进入扫描。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.6.0-l
 */
@PluginOwned("example.clock")
@EventListener
@ConditionalOnProperty(name = "tea-neko.plugins.example.enabled", havingValue = "true")
public final class ExampleClockListener {
    /**
     * 为时间参考增加时效说明，不修改其他来源的完整证据。
     * @param event 可信定位及可修改资料
     */
    @EventHandler(priority = 10)
    public void preparing(AgentPromptPreparingEvent event) {
        if (event.getData().features().contains(ExampleClockFeature.KEY)) {
            event.replace(event.values().stream().map(value -> value.name().equals("example_clock")
                    ? new AgentPromptFragment(value.name(), value.text() + "。这是本轮准备时刻，实时值由时间工具提供。", value.expiresAt(), value.transientToolResults())
                    : value).toList());
        }
    }
}
