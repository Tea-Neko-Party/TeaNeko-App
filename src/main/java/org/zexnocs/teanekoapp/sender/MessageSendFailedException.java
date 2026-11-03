package org.zexnocs.teanekoapp.sender;

/**
 * 发送任务结束却没有发送回执，无法确认消息已发送。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.5.0-l
 */
public class MessageSendFailedException extends IllegalStateException {
    /**
     * 建立发送链未取得有效回执的明确失败信号，避免空响应被解释为成功。
     */
    public MessageSendFailedException() {
        super("Message sender returned no response");
    }
}
