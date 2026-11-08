package org.zexnocs.teanekoagent.component.tool;

import org.zexnocs.teanekoagent.component.DeclaredAgentComponent;
import org.zexnocs.teanekoagent.component.TeaNekoAgentComponent;
import java.util.List;
import java.util.Map;

/**
 * 控制模型工具的显式装配和名称选择；不能绕过来源、客户端、组件及业务权限。
 *
 * <br>2026/11/04：工具组件只依赖自身内置能力，平台工具由客户端主动声明。
 *
 * <br>2026/11/06：声明组件显示优先级，运行依赖和装配行为不变。
 *
 * <br>2026/11/07：参数类型复用通用底座，业务约束由本组件独立维护。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.9.0-l
 */
@TeaNekoAgentComponent(id = "tool", order = 10, name = "工具", description = "允许模型调用已注册且当前客户端有权使用的工具。")
// 2026/11/07：原生系统能力不作为业务插件启停。
public final class ToolComponent extends DeclaredAgentComponent {
    /** @return 2026/11/04：仅依赖本层内置工具能力，不要求安装任何具体客户端。 */
    @Override
    public java.util.Set<String> features() {
        return java.util.Set.of();
    }
    /** 创建工具选择参数，不默认开放命令代执行。 */
    public ToolComponent() {
        super("tool", Map.of("allowedTools", ""), List.of(
                new Field("allowedTools", "工具名称", "英文逗号分隔；留空选择当前装配和客户端允许的全部工具。", "TEXT")));
    }

    /**
     * 2026/11/07：在声明类型校验后检查本组件业务范围，不依赖宿主字段表。
     * @param parameters 本次独立参数树
     * @throws IllegalArgumentException 当参数类型或业务范围无效时
     */
    @Override
    public void validate(tools.jackson.databind.JsonNode parameters) {
        super.validate(parameters);
        var value = parameters.path("allowedTools").asString("");
        if (!value.isEmpty() && (!value.matches("[a-zA-Z0-9_-]{1,64}(,[a-zA-Z0-9_-]{1,64})*")
                || value.split(",").length > 128)) {
            throw new IllegalArgumentException("允许的工具须为最多128项工具名称列表。");
        }
    }
}
