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
     * 记录错误日志并报告给默认监护人。
     *
     * @param namespace 日志命名空间
     * @param message   错误信息
     */
    default void errorWithReport(String namespace, String message) {
        errorWithReport(namespace, message, null, null);
    }

    /**
     * 记录错误日志并报告给默认监护人。
     *
     * @param namespace 日志命名空间
     * @param message   错误信息
     * @param throwable 异常
     */
    default void errorWithReport(String namespace, String message, Throwable throwable) {
        errorWithReport(namespace, message, throwable, null);
    }

    /**
     * 记录错误日志并报告给指定监护人。
     *
     * @param namespace 日志命名空间
     * @param message   错误信息
     * @param throwable 异常
     * @param reportRecipients 报告接收者；如果为 null 则报告给默认监护人
     */
    default void errorWithReport(String namespace, String message, Throwable throwable, String reportRecipients) {
        errorWithReport(LoggerReportData.builder().
                namespace(namespace).
                message(message).
                throwable(throwable).
                reportRecipients(reportRecipients).
                build());
    }

    /**
     * 使用 report 数据记录错误日志并报告给监护人。
     * @param data 日志报告数据
     */
    void errorWithReport(LoggerReportData data);

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
