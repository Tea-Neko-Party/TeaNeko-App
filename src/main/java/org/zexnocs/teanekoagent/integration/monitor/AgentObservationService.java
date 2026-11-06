package org.zexnocs.teanekoagent.integration.monitor;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.component.llm.framework.interaction.ILLMInteractionObserver;
import org.zexnocs.teanekoagent.component.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekoagent.configuration.file_config.AgentObservationFileConfig;
import org.zexnocs.teanekoagent.runtime.AgentRunResult;
import org.zexnocs.teanekoagent.runtime.monitor.AgentTurnReport;
import org.zexnocs.teanekoagent.runtime.monitor.IAgentActivityObserver;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.logger.ILogger;

import java.util.UUID;

/**
 * 每条请求结束后输出一份可配置汇总，不持有跨请求正文缓存。
 * <br>2026/11/06：按已应用文件快照查询开关，不在请求中扫描配置目录。
 * <br>2026/11/06：按独立开关和日志级别输出精简文字汇总，去重并继续脱敏。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.4-l
 * @version 4.8.6-l
 */
@Service
public final class AgentObservationService implements ILLMInteractionObserver, IAgentActivityObserver {
    private final IFileConfigService files;
    private final ILogger logger;
    private final java.util.concurrent.atomic.AtomicBoolean configurationReported = new java.util.concurrent.atomic.AtomicBoolean();
    /**
     * @param files 已应用文件配置查询
     * @param logger 项目日志
     */
    public AgentObservationService(IFileConfigService files, ILogger logger) {
        this.files = files;
        this.logger = logger;
    }
    /**
     * @param request 原请求编号
     * @param elapsedMillis 实际交互耗时
     * @param result 本次全部尝试，不读取父执行域累计用量
     */
    @Override
    public void finished(UUID request, long elapsedMillis, LLMInteractionResult result) {
        safely(() -> {
            var settings = settings();
            if (settings != null && settings.isLlmInfo() && settings.getVisibility() != AgentObservationFileConfig.Visibility.NONE) {
                emit(settings, "LLM 请求汇总", "请求=" + request + "，状态=" + result.status() + "，耗时毫秒=" + elapsedMillis
                        + InteractionLogFormatter.metrics(result) + InteractionLogFormatter.details(result, settings));
            }
        });
    }
    /**
     * 读取已应用观测配置；故障只报告一次，不让日志配置中断业务。
     * @return 已应用配置，故障时为空且等待正常热更新恢复
     */
    private @org.jspecify.annotations.Nullable AgentObservationFileConfig settings() {
        try {
            var value = files.requireCurrent(AgentObservationFileConfig.class);
            configurationReported.set(false);
            return value;
        } catch (RuntimeException failure) {
            if (configurationReported.compareAndSet(false, true)) {
                logger.warn("Agent 观测", "观测配置暂不可用，INFO 汇总已暂停；"
                        + org.zexnocs.teanekocore.logger.FailureDiagnostic.from(failure));
            }
            return null;
        }
    }

    /**
     * 2026/11/06：Agent 统一报告自己及内部 LLM 的执行；平台交付仍以真实回执为准。
     * @param report 本轮终态计数
     * @param result 临时结果；没有交互时不伪造模型上下文
     */
    @Override
    public void finished(AgentTurnReport report, @Nullable AgentRunResult result) {
        safely(() -> {
            var settings = settings();
            if (settings != null && settings.isAgentInfo() && settings.getVisibility() != AgentObservationFileConfig.Visibility.NONE) {
                var interaction = result == null ? null : result.interaction().orElse(null);
                var metrics = interaction == null ? "，模型调用=0，工具调用=0"
                        : InteractionLogFormatter.metrics(interaction);
                var failure = result != null && result.failure() != null
                        && (interaction == null || !result.failure().equals(interaction.failure()))
                        ? "，" + result.failure() : "";
                emit(settings, "Agent 请求汇总", "请求=" + report.requestId() + "，活动=" + report.activity()
                        + "，状态=" + report.status() + "，耗时毫秒=" + report.elapsedMillis() + metrics + failure
                        + (interaction == null ? "" : InteractionLogFormatter.details(interaction, settings)));
            }
        });
    }

    /**
     * 2026/11/06：一次请求只产生一个选定级别事件，不将长正文编码为 JSON 日志。
     * @param settings 本次配置
     * @param title 固定事件名称
     * @param body 人类可读正文
     */
    private void emit(AgentObservationFileConfig settings, String title, String body) {
        switch (settings.getVisibility()) {
            case INFO -> logger.info(title, body);
            case DEBUG -> logger.debug(title, body);
            case NONE -> { }
        }
    }
    /**
     * 观测故障不改变业务终态，不能用另一个日志重试产生递归失败。
     * @param action 仅包含观测动作
     */
    private static void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ignored) {
            // 日志端口故障只停止本次观测，业务仍由原执行控制处理。
        }
    }
}
