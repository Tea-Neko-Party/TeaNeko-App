package org.zexnocs.teanekoagent.component.expression.adapter;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.component.expression.AgentReplyPlan;

import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import java.util.concurrent.CompletionStage;

/**
 * 将已授权计划的一段投影到原发送器，不从模型读取用户或消息 ID。
 * <br>2026/11/03：只有包含实际平台消息 ID 的回执才确认发送。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.8.0-l
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
        // 2026/11/03：通用完成信号不额外证明平台接受，本边界要求有效消息回执。
        return confirm(sender.sendWithFuture());
    }
    /**
     * 2026/11/03：确认原发送返回实际消息标识，不将非空错误对象当作送达。
     * @param task 原平台发送任务
     * @return 带有效消息标识的真实回执阶段
     */
    public static CompletionStage<Void> confirm(org.zexnocs.teanekocore.actuator.task.TaskFuture<? extends org.zexnocs.teanekoapp.response.api.IMessageSendResponseData> task) {
        return task.asStage().thenAccept(response -> {
            if (response == null || response.getMessageId() == null || response.getMessageId().isBlank()) { throw new IllegalStateException("平台没有返回有效消息标识，发送结果未确认。"); }
        });
    }
}
