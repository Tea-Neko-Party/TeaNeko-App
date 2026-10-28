package org.zexnocs.teanekocore.api_response.exception;

/**
 * 单次 HTTP 阶段时限耗尽；不同于整轮 ExecutionStoppedException 的 deadline 原因。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class APIRequestTimeoutException extends RuntimeException {
    /**
     * 建立单次 HTTP 阶段超时信号，与整轮控制的截止时间原因分别处理。
     */
    public APIRequestTimeoutException() {
        super("API request timed out.");
    }
}
