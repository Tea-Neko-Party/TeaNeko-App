package org.zexnocs.teanekocore.logger;

/**
 * 日志记录器接口。
 *
 * @author zExNocs
 * @date 2026/02/06
 */
public interface ILogger {
    /**
     * 记录一般日志。
     *
     * @param namespace 日志命名空间
     * @param message   日志信息
     */
    void info(String namespace, String message);

    /**
     * 记录错误日志。
     *
     * @param namespace 日志命名空间
     * @param message   错误信息
     */
    default void errorWithReport(String namespace, String message) {
        errorWithReport(namespace, message, null);
    }

    /**
     * 记录异常日志。
     *
     * @param namespace 日志命名空间
     * @param message   错误信息
     * @param throwable 异常
     */
    void errorWithReport(String namespace, String message, Throwable throwable);

    /**
     * 记录错误日志。
     * @param namespace 日志命名空间
     * @param message  错误信息
     */
    default void error(String namespace, String message) {
        error(namespace, message, null);
    }

    /**
     * 记录错误日志。
     *
     * @param namespace 日志命名空间
     * @param message   错误信息
     * @param throwable 异常
     */
    void error(String namespace, String message, Throwable throwable);

    /**
     * 记录警告日志。
     *
     * @param namespace 日志命名空间
     * @param message   警告信息
     */
    default void warn(String namespace, String message) {
        warn(namespace, message, null);
    }

    /**
     * 记录警告日志。
     *
     * @param namespace 日志命名空间
     * @param message   警告信息
     * @param throwable 异常
     */
    void warn(String namespace, String message, Throwable throwable);

    /**
     * 记录调试信息。
     * @param namespace 日志命名空间
     * @param message 调试信息
     */
    default void debug(String namespace, String message) {
        debug(namespace, message, null);
    }

    /**
     * 记录调试信息。
     * @param namespace 日志命名空间
     * @param message 调试信息
     * @param throwable 异常
     */
    void debug(String namespace, String message, Throwable throwable);
}
