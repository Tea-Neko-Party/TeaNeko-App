package org.zexnocs.teanekocore.logger;

/**
 * 由可信业务异常声明可公开的固定原因；禁止返回请求、凭据、响应正文或未经筛选的异常消息。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.2-l
 * @version 4.8.2-l
 */
public interface IFailureReason {
    /** @return 可以同时用于日志和用户提示的固定原因 */
    String failureReason();
}
