package org.zexnocs.teanekoagent.llm.instance.antigravity;

import org.zexnocs.teanekocore.logger.IFailureReason;

import java.io.Serial;

/**
 * 原生响应流的安全协议诊断，不携带远端正文、签名或地址。
 * <br>2026/11/10：区分提前结束、非法顺序和容量拒绝，禁止半成品进入工具或回复流程。
 *
 * @author zExNocs
 * @date 2026/11/10
 * @since 4.9.1-l
 * @version 4.9.1-l
 */
public final class AntigravityStreamException extends IllegalArgumentException implements IFailureReason {
    @Serial
    private static final long serialVersionUID = 1L;
    private final Reason reason;

    /**
     * @param reason 代码固定的诊断类别
     */
    public AntigravityStreamException(Reason reason) {
        super(reason.message);
        this.reason = reason;
    }

    /** @return 可以直接公开的固定协议说明。 */
    @Override
    public String failureReason() {
        return reason.message;
    }

    /**
     * 协议拒绝分类，仅包含本地固定文本。
     * @author zExNocs
     * @date 2026/11/10
     * @since 4.9.1-l
     * @version 4.9.1-l
     */
    public enum Reason {
        INCOMPLETE("原生响应流提前结束：要求每个候选具有 finishReason，实际未取得完整终态；不执行部分工具或回复。"),
        INVALID("原生响应流结构或顺序无效：候选、响应身份或终态后内容不符合协议。"),
        CAPACITY("原生响应流超过本地聚合上限（8 Mi 字符、8192 个事件、16384 个部件或 8 个候选），已停止接收。");

        private final String message;

        /** @param message 可公开的固定文本 */
        Reason(String message) {
            this.message = message;
        }
    }
}
