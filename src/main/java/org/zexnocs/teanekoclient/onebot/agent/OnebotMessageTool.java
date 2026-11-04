package org.zexnocs.teanekoclient.onebot.agent;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.component.llm.framework.tool.LLMToolOutput;
import org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolContext;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentTool;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentToolClass;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.concurrent.CompletionStage;

/**
 * 查询当前 OneBot 会话的消息，图文回填与来源校验委托共享平台服务。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.0-l
 * @version 4.8.0-l
 */
@Component
@TeaNekoAgentToolClass(value = "onebot", toolPackage = "agent.onebot",
        client = OnebotTeaNekoClient.ID, managedByFeature = true)
public final class OnebotMessageTool {
    private final OnebotAgentSupport support;

    /**
     * 借用同一来源和查询服务，不创建平台连接。
     * @param support 共享平台支持
     */
    public OnebotMessageTool(OnebotAgentSupport support) {
        this.support = support;
    }

    /**
     * 验证当前请求仍拥有真实 OneBot 来源。
     * @param context 宿主调用上下文
     * @return 来源仍有效时为真
     */
    public boolean available(TeaNekoAgentToolContext context) {
        return support.available(context);
    }

    /**
     * 2026/11/05：独立注解入口保留原消息 ID、视觉结果和会话读取边界。
     * @param messageId 当前会话原始消息标识
     * @param context 宿主注入的可信上下文
     * @return 有界文字与已验证图像
     */
    @TeaNekoAgentTool(value = OnebotAgentSupport.MESSAGE, condition = "available")
    @Description("按消息 ID 查询当前会话的消息文本、图片、发送者和时间；不能读取其他群或私聊。")
    public CompletionStage<LLMToolOutput> getMessage(
            @Description("当前 scope 内待查询的原始消息 ID。") String messageId,
            TeaNekoAgentToolContext context) {
        return support.getMessage(messageId, context);
    }
}
