package org.zexnocs.teanekoagent.component.expression;

import org.zexnocs.teanekocore.logger.IFailureReason;

/**
 * 表达协议的可公开格式错误，只接收解析器内部固定说明，不保存模型原文。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.2-l
 * @version 4.8.2-l
 */
public final class ReplyProtocolException extends IllegalArgumentException implements IFailureReason {
    /**
     * @param reason 解析器固定说明，不允许传入模型或用户正文
     */
    public ReplyProtocolException(String reason) {
        super(reason);
    }

    /** @return 固定格式错误说明 */
    @Override
    public String failureReason() {
        return "模型回复格式错误：" + getMessage();
    }
}
