package org.zexnocs.teanekoclient.onebot.agent;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolContext;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentTool;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentToolClass;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentToolParam;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekocore.command.api.DefaultValue;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.concurrent.CompletionStage;

/**
 * 读取当前或引用消息的发送者资料，不允许模型任意指定查询身份。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.0-l
 * @version 4.8.0-l
 */
@Component
@TeaNekoAgentToolClass(value = "onebot", toolPackage = "agent.onebot",
        client = OnebotTeaNekoClient.ID, managedByFeature = true)
public final class OnebotSenderInfoTool {
    private final OnebotAgentSupport support;

    /**
     * 使用平台共享服务复用身份、来源和结果限制。
     * @param support 共享平台支持
     */
    public OnebotSenderInfoTool(OnebotAgentSupport support) {
        this.support = support;
    }

    /**
     * 检查真实来源租约。
     * @param context 当前调用
     * @return 来源有效时为真
     */
    public boolean available(TeaNekoAgentToolContext context) {
        return support.available(context);
    }

    /**
     * 2026/11/05：单独声明发送者工具，保留原参数默认值及允许值。
     * @param target sender 或 replied
     * @param context 宿主注入的可信上下文
     * @return 有界用户资料
     */
    @TeaNekoAgentTool(value = OnebotAgentSupport.SENDER, condition = "available")
    @Description("查询当前发送者或被引用消息发送者的 TeaNeko UUID、QQ、昵称、群名片及头像地址。")
    public CompletionStage<String> senderInfo(
            @DefaultValue("sender") @TeaNekoAgentToolParam(allowedValues = {"sender", "replied"})
            @Description("sender 查询当前发送者；replied 查询原生引用消息的发送者。") String target,
            TeaNekoAgentToolContext context) {
        return support.senderInfo(target, context);
    }
}
