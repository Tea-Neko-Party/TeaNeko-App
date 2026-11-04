package org.zexnocs.teanekoagent.component.tool;

import org.zexnocs.teanekoagent.component.BuiltinAgentComponent;
import org.zexnocs.teanekoagent.component.TeaNekoAgentComponent;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import java.util.List;
import java.util.Map;

/**
 * 控制模型工具的显式装配和名称选择；不能绕过来源、客户端、组件及业务权限。
 *
 * 修改记录：<br>
 * 2026/11/04：工具组件只依赖自身内置能力，平台工具由客户端主动声明。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.8.0-l
 */
@TeaNekoAgentComponent(id = "tool", name = "工具", description = "允许模型调用已注册且当前客户端有权使用的工具。")
@PluginOwned("teaneko.agent")
public final class ToolComponent extends BuiltinAgentComponent {
    /** @return 2026/11/04：仅依赖本层内置工具能力，不要求安装任何具体客户端。 */
    @Override
    public java.util.Set<String> features() {
        return java.util.Set.of("teaneko.agent:builtin-tools");
    }
    /** 创建工具选择参数，不默认开放命令代执行。 */
    public ToolComponent() {
        super("tool", Map.of("allowedTools", "", "commandToolsEnabled", false), List.of(
                new Field("allowedTools", "工具名称", "英文逗号分隔；留空选择当前装配和客户端允许的全部工具。", "TEXT"),
                new Field("commandToolsEnabled", "命令工具", "仍按真实发送者权限执行命令。", "BOOLEAN")));
    }
}
