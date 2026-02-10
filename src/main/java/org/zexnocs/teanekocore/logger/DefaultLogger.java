package org.zexnocs.teanekocore.logger;

import org.zexnocs.teanekocore.utils.ExceptionUtils;

import java.util.logging.Logger;

/**
 * 默认日志记录器实现，提供基本的日志记录功能。
 *
 * @author zExNocs
 * @date 2026/02/07
 */
public class DefaultLogger implements ILogger {
    private final Logger logger;

    /**
     * 构造函数，初始化默认日志记录器。
     */
    public DefaultLogger() {
        this.logger = Logger.getLogger("默认日志");
    }

    /**
     * 或者注入一个自定义的日志记录器。
     * @param logger 自定义日志记录器
     */
    public DefaultLogger(Logger logger) {
        this.logger = logger;
    }

    /**
     * 记录一般日志。
     *
     * @param namespace 日志命名空间
     * @param message   日志信息
     */
    @Override
    public void info(String namespace, String message) {
        logger.info(namespace + ": " + message);
    }

    /**
     * 记录错误日志 + 汇报。
     *
     * @param namespace 日志命名空间
     * @param message   错误信息
     */
    @Override
    public void errorWithReport(String namespace, String message, Throwable throwable, String reportRecipients) {
        this.error(namespace, message, throwable);
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
        logger.severe(namespace + ": " + message + "\n" + ExceptionUtils.instance.buildExceptionMessage(throwable));
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
        logger.warning(namespace + ": " + message + "\n" + ExceptionUtils.instance.buildExceptionMessage(throwable));
    }

    /**
     * 记录调试信息。
     * @param namespace 日志命名空间
     * @param message 调试信息
     * @param throwable 异常
     */
    @Override
    public void debug(String namespace, String message, Throwable throwable) {
        logger.fine(namespace + ": " + message + "\n" + ExceptionUtils.instance.buildExceptionMessage(throwable));
    }
}
