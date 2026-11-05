package org.zexnocs.teanekocore.logger;

import org.jspecify.annotations.Nullable;

/**
 * 由可信业务异常声明可公开的固定原因；禁止返回请求、凭据、响应正文或未经筛选的异常消息。
 * <br>2026/11/05：允许异常携带已有的脱敏诊断快照，避免跨结果边界丢失原始状态及位置。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.2-l
 * @version 4.8.3-l
 */
public interface IFailureReason {
    /** @return 可以同时用于日志和用户提示的固定原因 */
    String failureReason();

    /**
     * 2026/11/05：只提供已有的安全快照，不持有或重建原始异常与响应正文。
     * @return 原失败诊断；没有已保存的诊断时为空
     */
    default @Nullable FailureDiagnostic failureDiagnostic() {
        return null;
    }
}
