package org.zexnocs.teanekoagent.llm.framework.model;

import org.zexnocs.teanekocore.logger.IFailureReason;

import java.io.Serial;

/**
 * 输入校验的安全报告，公开字段位置、结构要求和实际类型，不携带消息或图片原文。
 * <br>2026/11/10：通过既有安全原因契约传播详细诊断，日志不读取任意异常正文。
 *
 * @author zExNocs
 * @date 2026/11/10
 * @since 4.9.1-l
 * @version 4.9.1-l
 */
public final class LLMInputValidationException extends IllegalArgumentException implements IFailureReason {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String field;
    private final String expected;
    private final String actual;

    /**
     * 仅由本包校验器构造；所有描述必须来自固定文字、枚举或数字，不接受用户正文。
     * @param field 固定字段名及从零开始的位置
     * @param expected 允许的结构或能力
     * @param actual 实际结构或枚举值
     */
    LLMInputValidationException(String field, String expected, String actual) {
        super("LLM 输入在发送前未通过校验。");
        this.field = field;
        this.expected = expected;
        this.actual = actual;
    }

    /** @return 可跨异步包装传播的单行诊断，不包含图片地址、正文或凭据。 */
    @Override
    public String failureReason() {
        return "LLM 输入校验失败（发送前）；字段=" + field + "；要求=" + expected + "；实际=" + actual;
    }
}
