package org.zexnocs.teanekoplugin.onebot.common.agent;

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import org.zexnocs.teanekocore.plugin.PluginOwned;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolContext;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentTool;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentToolClass;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.concurrent.CompletionStage;

/**
 * 仅查询宿主当前 OneBot 群资料，私聊不会借用其他群身份。
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.0-l
 * @version 4.9.0-l
 */
@Component
@TeaNekoAgentToolClass(value = "onebot", toolPackage = "agent.onebot",
        client = OnebotTeaNekoClient.ID, managedByFeature = true)
@PluginOwned("onebot.common")
public final class OnebotGroupInfoTool {
    private final OnebotAgentSupport support;

    /**
     * 借用平台共享查询，不单独持有会话状态。
     * @param support 共享平台支持
     */
    public OnebotGroupInfoTool(OnebotAgentSupport support) {
        this.support = support;
    }

    /**
     * 检查原请求来源是否仍有效。
     * @param context 当前调用
     * @return 来源有效时为真
     */
    public boolean available(TeaNekoAgentToolContext context) {
        return support.available(context);
    }

    /**
     * 2026/11/05：单独声明群资料工具，查询目标仍仅来自可信消息。
     * @param context 宿主注入的可信上下文
     * @return 有界群资料或私聊状态
     */
    @TeaNekoAgentTool(value = OnebotAgentSupport.GROUP, condition = "available")
    @Description("查询当前群的群号、群名称、成员数与容量；私聊明确返回无当前群。")
    public CompletionStage<String> groupInfo(TeaNekoAgentToolContext context) {
        return support.groupInfo(context);
    }
}
