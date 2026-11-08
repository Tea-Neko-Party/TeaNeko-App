package org.zexnocs.teanekoagent.llm.framework.structured;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekocore.logger.FailureDiagnostic;
import org.zexnocs.teanekocore.logger.IFailureReason;

import java.io.Serial;
import java.util.UUID;

/**
 * 结构化生成的安全失败，保留真实状态和结构定位，不持有原输出或第三方异常正文。
 * <br>2026/11/06：区分模型终止、JSON 语法、字段结构与领域构造失败。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.3-l
 * @version 4.9.0-l
 */
public final class LLMJsonException extends IllegalArgumentException implements IFailureReason {
    @Serial
    private static final long serialVersionUID = 1L;
    private final Code code;
    private final LLMInteractionResult.Status status;
    private final transient @Nullable FailureDiagnostic diagnostic;

    /**
     * @param code 固定原因
     * @param status 实际模型终态
     * @param request 宿主请求编号；无上下文的兼容入口可以为空
     * @param detail 已脱敏结构定位
     * @param diagnostic 已有安全诊断，不包含 Throwable
     */
    public LLMJsonException(Code code, LLMInteractionResult.Status status, @Nullable UUID request, String detail,
            @Nullable FailureDiagnostic diagnostic) {
        super("结构化输出失败。请求=" + (request == null ? "未取得" : request)
                + "，模型状态=" + status + "，原因=" + code + "；" + detail);
        this.code = code;
        this.status = status;
        this.diagnostic = diagnostic;
    }

    /** @return 用于业务层分类的固定原因 */
    public Code code() { return code; }

    /** @return 实际取得的模型状态，解码失败不伪装为 HTTP 失败 */
    public LLMInteractionResult.Status status() { return status; }

    /** @return 不含原文的可公开诊断 */
    @Override public String failureReason() { return getMessage(); }

    /** @return 已有 HTTP、类型和代码位置，不虚构缺失信息 */
    @Override public @Nullable FailureDiagnostic failureDiagnostic() { return diagnostic; }

    /**
     * 生成与解码阶段的固定错误分类。
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.3-l
     * @version 4.8.3-l
     */
    public enum Code { MODEL_RESULT, MISSING_OUTPUT, OUTPUT_LIMIT, JSON_SYNTAX, JSON_SHAPE, JSON_BINDING }
}
