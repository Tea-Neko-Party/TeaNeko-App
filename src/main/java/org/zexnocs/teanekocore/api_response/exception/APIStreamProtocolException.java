package org.zexnocs.teanekocore.api_response.exception;

import org.zexnocs.teanekocore.logger.IFailureReason;

import java.io.Serial;

/**
 * 将非事件流响应映射为可公开的固定协议说明，不包含代理响应正文或地址。
 * <br>2026/11/10：便于区分不支持 SSE 的端点与普通连接失败。
 *
 * @author zExNocs
 * @date 2026/11/10
 * @since 4.9.1-l
 * @version 4.9.1-l
 */
public final class APIStreamProtocolException extends IllegalArgumentException implements IFailureReason {
    @Serial
    private static final long serialVersionUID = 1L;
    private static final String REASON = "流式响应类型不符合要求：要求 Content-Type=text/event-stream，"
            + "实际返回其他类型或缺少声明；请检查服务端是否支持 SSE。";

    /** 创建不包含任何远端正文的固定诊断。 */
    public APIStreamProtocolException() {
        super(REASON);
    }

    /** @return 可用于日志和用户提示的安全协议说明 */
    @Override
    public String failureReason() {
        return REASON;
    }
}
