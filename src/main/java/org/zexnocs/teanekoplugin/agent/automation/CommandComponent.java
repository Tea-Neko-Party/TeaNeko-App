package org.zexnocs.teanekoplugin.agent.automation;

import org.zexnocs.teanekoagent.component.DeclaredAgentComponent;
import org.zexnocs.teanekoagent.component.TeaNekoAgentComponent;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 命令代执行的独立业务装配；工具框架不默认开启真实命令权限。
 * <br>2026/11/07：从通用工具参数中分离命令开关。
 *
 * <br>2026/11/07：参数类型复用通用底座，业务约束由本组件独立维护。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@TeaNekoAgentComponent(id = "command", name = "命令工具", description = "按真实用户权限执行已允许的原生命令。", requires = "tool", order = 15)
@PluginOwned("agent.automation")
public final class CommandComponent extends DeclaredAgentComponent {
    /** 创建默认关闭的原命令开关，安装本身不增加身份权限。 */
    public CommandComponent() {
        super("command", Map.of("commandToolsEnabled", false), List.of(
                new Field("commandToolsEnabled", "允许命令工具", "继续检查真实来源、命令权限和实际回执。", "BOOLEAN")));
    }

    /** @return 仅本组件拥有的命令能力。 */
    @Override public Set<String> features() {
        return Set.of("agent.automation:command-tools");
    }
}
