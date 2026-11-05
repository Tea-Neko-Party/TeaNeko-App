package org.zexnocs.teanekoagent.integration.message;

import org.zexnocs.teanekocore.logger.IFailureReason;

import java.io.Serial;

/**
 * 原生图文拒绝的安全原因，只报告容量与引用格式，不公开原文或带凭据的图片地址。
 * <br>2026/11/06：替代合并多种拒绝原因的通用异常。
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.3-l
 * @version 4.8.3-l
 */
public final class NativeMessageInputException extends IllegalArgumentException implements IFailureReason {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * @param detail 宿主固定的安全说明，仅允许数值与固定标签
     */
    NativeMessageInputException(String detail) { super("原生图文输入未接受：" + detail); }

    /** @return 可公开的确切拒绝原因 */
    @Override public String failureReason() { return getMessage(); }
}
