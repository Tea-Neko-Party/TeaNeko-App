package org.zexnocs.teanekoclient.general.agent;

import org.zexnocs.teanekoagent.runtime.delivery.AgentReplyPlan;
import org.zexnocs.teanekoagent.runtime.delivery.AgentReplySender;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.sender.api.sender_box.IEasyMessageSenderBuilder;

import java.util.concurrent.CompletionStage;

/**
 * 客户端表达适配共用的独立素材发送规则，不绑定具体平台协议。
 * 禁止素材附带提及，不重试或后台补发，实际成功由原发送回执确认。
 * <br>2026/11/05：补充图片发送沿用的提及模式拒绝契约。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.0-l
 * @version 4.8.0-l
 */
public final class AgentMediaSender {
    /** 仅提供客户端发送辅助，不创建独立服务或持有运行状态。 */
    private AgentMediaSender() {
    }

    /**
     * 创建独立素材消息，保留当前消息的引用关系。
     * @param data 可信目标与发送来源
     * @param mode 普通或引用消息
     * @return 不含文字和提及的新消息构建器
     * @throws IllegalArgumentException 当要求素材附带提及时
     */
    public static IEasyMessageSenderBuilder sender(ITeaNekoMessageData data, AgentReplyPlan.Mode mode) {
        if (mode == AgentReplyPlan.Mode.AT) {
            throw new IllegalArgumentException("表情资源不可附带提及。");
        }
        var sender = data.getMessageSender().setRetryCount(0).setRecordFailed(false);
        if (mode == AgentReplyPlan.Mode.REPLY) {
            sender.addReplyMessage(data.getMessageId());
        }
        return sender;
    }

    /**
     * 2026/11/05：发送已验证的内联素材，沿用独立消息约束并等待实际回执。
     * @param data 可信来源
     * @param base64 原始图片编码
     * @param mode 独立消息方式
     * @return 实际平台发送完成
     * @throws IllegalArgumentException 当要求图片消息附带提及时
     */
    public static CompletionStage<Void> image(ITeaNekoMessageData data, String base64, AgentReplyPlan.Mode mode) {
        return AgentReplySender.confirm(sender(data, mode).addImageMessage("base64://" + base64).sendWithFuture());
    }
}
