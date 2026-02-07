package org.zexnocs.teanekocore.logger;

/**
 * @author zExNocs
 * @date 2026/02/07
 */
public class DefaultLogger implements ILogger {
    /**
     * 记录一般日志。
     *
     * @param namespace 日志命名空间
     * @param message   日志信息
     */
    @Override
    public void info(String namespace, String message) {

    }

    /**
     * 记录错误日志 + 汇报。
     *
     * @param namespace 日志命名空间
     * @param message   错误信息
     */
    @Override
    public void errorWithReport(String namespace, String message, Throwable throwable) {

    }

    /**
     * 记录异常日志。
     *
     * @param namespace 日志命名空间
     * @param message   错误信息
     * @param throwable 异常
     */
    @Override
    public void error(String namespace, String message, Throwable throwable) {

    }

    /**
     * 记录警告日志。
     *
     * @param namespace 日志命名空间
     * @param message   警告信息
     * @param throwable 异常
     */
    @Override
    public void warn(String namespace, String message, Throwable throwable) {

    }

    /**
     * 记录调试信息。
     * @param namespace 日志命名空间
     * @param message 调试信息
     * @param throwable 异常
     */
    @Override
    public void debug(String namespace, String message, Throwable throwable) {

    }
}
