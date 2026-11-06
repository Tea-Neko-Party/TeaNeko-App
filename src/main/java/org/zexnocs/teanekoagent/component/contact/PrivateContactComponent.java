package org.zexnocs.teanekoagent.component.contact;

import org.zexnocs.teanekoagent.component.DeclaredAgentComponent;
import org.zexnocs.teanekoagent.component.TeaNekoAgentComponent;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import java.util.List;
import java.util.Map;

/**
 * 允许回应当前用户明确提出的私聊请求，不赋予任意收件人发送权。
 * <br>2026/11/06：目标必须已启用 Agent 并绑定同一空间。
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.8.6-l
 */
@TeaNekoAgentComponent(id = "private-contact", order = 65, name = "受控私聊",
        description = "用户在群内要求私聊时，检查目标 Agent 和空间后发送。", requires = {"memory", "tool"})
@PluginOwned("teaneko.agent")
public final class PrivateContactComponent extends DeclaredAgentComponent {
    /** 建立独立开关，目标资格不由模型参数指定。 */
    public PrivateContactComponent() {
        super("private-contact", Map.of("enabled", true), List.of(
                new Field("enabled", "允许受邀私聊", "仅向本轮真实用户发送；需其明确邀请、已启用私聊 Agent 且处于同空间。", "BOOLEAN")));
    }
}
