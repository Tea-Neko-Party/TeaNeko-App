package org.zexnocs.teanekoagent.adapter.platform;

import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import java.util.concurrent.CompletionStage;

/**
 * 将已授权计划的一段投影到原发送器，不从模型读取用户或消息 ID。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class AgentReplySender {
    /** 禁止创建无状态工具实例。 */
    private AgentReplySender() { }
    /**
     * 发送前由宿主复核原结果、目标与取消，本方法保留原平台状态和实际回执。
     * @param data 原始真实来源及固定目标
     * @param part 已完整验证的当前段
     * @return 实际平台回执，不自动重试或补发
     */
    public static CompletionStage<Void> send(ITeaNekoMessageData data, AgentReplyPlan.Part part) {
        var sender = data.getMessageSender().setRetryCount(0).setRecordFailed(false);
        switch (part.mode()) {
            case REPLY -> sender.addReplyMessage(data.getMessageId());
            case AT -> sender.addAtMessage();
            case PLAIN -> { }
        }
        switch (part) {
            case AgentReplyPlan.Text text -> sender.addTextMessage(text.value());
            case AgentReplyPlan.Image image -> sender.addImageMessage(image.platformReference());
        }
        return sender.sendAsync();
    }
}
