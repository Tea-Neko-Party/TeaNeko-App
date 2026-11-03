package org.zexnocs.teanekoapp.web;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import jakarta.annotation.PreDestroy;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekocore.actuator.runtime.RuntimeMonitor;

/**
 * 应用就绪后借用实际 Logback 事件，日志采集不读取文件，不复制异常链和参数数组。
 * 默认只记录元数据；启用正文后仍受 Core 的有界脱敏限制。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：启动登录信息仅留在后端日志，不进入网页日志窗口。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@Component
public final class WebRuntimeLogCapture extends AppenderBase<ILoggingEvent> {
    private final RuntimeMonitor monitor;

    /** @param monitor 原进程窗口 */
    public WebRuntimeLogCapture(RuntimeMonitor monitor) {
        this.monitor = monitor;
    }

    /** 在日志后端就绪后安装一次，不干扰其他输出器。 */
    @EventListener(ApplicationReadyEvent.class)
    public void ready() {
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext context && !isStarted()) {
            setContext(context);
            setName("TEANEKO_RUNTIME_WINDOW");
            start();
            context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).addAppender(this);
        }
    }

    /**
     * 2026/11/04：排除包含根令牌的启动日志，不向网页日志窗口传播。
     * @param event 原实际日志，只提取安全字段
     */
    @Override
    protected void append(ILoggingEvent event) {
        if (event.getLoggerName().equals(WebCredentials.class.getName() + ".startup")) {
            return;
        }
        var mdc = event.getMDCPropertyMap();
        String namespace = event.getLoggerName();
        var arguments = event.getArgumentArray();
        if (namespace.equals("org.zexnocs.teanekocore.logger.DefaultLogger")
                && arguments != null && arguments.length > 0 && arguments[0] instanceof String source) {
            namespace = source;
        }
        // 默认未开启正文时，不触发参数对象的格式化或异常展开。
        monitor.log(event.getLevel().toString(), namespace,
                monitor.policy().captureMessages() ? event.getFormattedMessage() : "",
                mdc.getOrDefault("requestId", ""), mdc.getOrDefault("scope", ""), mdc.getOrDefault("plugin", ""));
    }

    /** 只卸载本实例拥有的输出器，不关闭宿主日志系统。 */
    @PreDestroy
    public void detach() {
        if (getContext() instanceof LoggerContext context) {
            context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).detachAppender(this);
        }
        stop();
    }
}
