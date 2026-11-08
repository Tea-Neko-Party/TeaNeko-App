package org.zexnocs.teanekoplugin.agent.automation.integration.command;

import org.zexnocs.teanekoagent.integration.command.AgentChatCommandEvent;
import org.zexnocs.teanekoagent.integration.command.AgentCommandSupport;
import org.zexnocs.teanekocore.event.core.EventHandler;
import org.zexnocs.teanekocore.event.core.EventListener;
import org.zexnocs.teanekocore.plugin.PluginAccess;
import org.zexnocs.teanekocore.plugin.PluginOwned;

/**
 * 在原生聊天扩展入口识别提醒语句，业务及实际反馈共同参与插件在途排空。
 * <br>2026/11/07：自然语言任务归自动化插件，停启期间不发送旧代次的迟到结果。
 *
 * <br>2026/11/07：业务归属与通用底座按职责分离，保留持久身份及完成边界。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@EventListener
@PluginOwned("agent.automation")
public final class NaturalTaskCommandListener {
    private final IAgentTaskCommands tasks;
    private final PluginAccess plugins;

    /**
     * 借用插件业务命令与实时资格入口。
     * @param tasks 提醒任务解释和持久化服务
     * @param plugins 插件调用资格
     */
    public NaturalTaskCommandListener(IAgentTaskCommands tasks, PluginAccess plugins) {
        this.tasks = tasks;
        this.plugins = plugins;
    }

    /**
     * 同步决定是否认领，识别后的阶段包含反馈发送前的插件代次复核。
     * @param event 可信原生聊天正文
     */
    @EventHandler
    public void onChat(AgentChatCommandEvent event) {
        event.tryHandle(() -> {
            var token = plugins.captureOwner("agent.automation");
            return tasks.natural(event.getData().getRawData(), event.text())
                    .map(work -> AgentCommandSupport.respond(event.getData(), () -> work, () -> plugins.check(token)));
        });
    }
}
