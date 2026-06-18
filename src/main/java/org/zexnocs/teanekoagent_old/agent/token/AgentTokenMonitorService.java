package org.zexnocs.teanekoagent_old.agent.token;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent_old.agent.event.AgentModelCallData;
import org.zexnocs.teanekoagent_old.agent.event.AgentTurnData;
import org.zexnocs.teanekoagent_old.agent.event.AgentTurnEvent;
import org.zexnocs.teanekoagent_old.file_config.AgentTokenMonitorFileConfig;
import org.zexnocs.teanekoagent_old.file_config.interfaces.IAgentFileConfigService;
import org.zexnocs.teanekoagent_old.llm.file_config.interfaces.ILLMFileConfigService;
import org.zexnocs.teanekoagent_old.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent_old.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent_old.llm.framework.message.interfaces.ILLMContent;
import org.zexnocs.teanekoagent_old.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent_old.llm.framework.model.LLMModelId;
import org.zexnocs.teanekoagent_old.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent_old.llm.framework.model.interfaces.ILLMModelOptions;
import org.zexnocs.teanekoagent_old.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekoagent_old.llm.framework.response.interfaces.ILLMUsage;
import org.zexnocs.teanekocore.actuator.task.EmptyTaskResult;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerService;
import org.zexnocs.teanekocore.database.easydata.cleanable.CleanableEasyData;
import org.zexnocs.teanekocore.database.easydata.cleanable.CleanableEasyDataRepository;
import org.zexnocs.teanekocore.database.easydata.debug.DebugEasyData;
import org.zexnocs.teanekocore.event.interfaces.IEventService;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent token 监控服务。
 * <br>该服务在 Agent Runtime 的模型调用完成后记录 token 用量，在单轮对话结束后推送告警事件并输出 warn 日志。
 *
 * @author zExNocs
 * @date 2026/06/11
 * @since 4.4.1
 */
@Service
public class AgentTokenMonitorService {
    /**
     * 日志命名空间。
     */
    private static final String LOG_NAMESPACE = "AgentTokenMonitor";

    /**
     * token 使用日志命名空间。
     */
    private static final String USAGE_NAMESPACE = "agent-token-usage";

    /**
     * 上下文快照命名空间。
     */
    private static final String CONTEXT_NAMESPACE = "agent-token-context";

    /**
     * 上下文快照 key。
     */
    private static final String CONTEXT_KEY = "context";

    /**
     * 数据库任务名称。
     */
    private static final String DATABASE_TASK = "agent-token-monitor";

    /**
     * Agent 文件配置服务。
     */
    private final IAgentFileConfigService agentFileConfigService;

    /**
     * LLM 文件配置服务。
     */
    private final ILLMFileConfigService llmFileConfigService;

    /**
     * core 事件服务。
     */
    private final IEventService eventService;

    /**
     * 定时器服务。
     */
    private final ITimerService timerService;

    /**
     * 可清理 EasyData 仓库。
     */
    private final CleanableEasyDataRepository cleanableEasyDataRepository;

    /**
     * 日志服务。
     */
    private final ILogger logger;

    /**
     * JSON 解析器。
     */
    private final ObjectMapper objectMapper;

    /**
     * 创建 Agent token 监控服务。
     *
     * @param agentFileConfigService      Agent 文件配置服务。
     * @param llmFileConfigService        LLM 文件配置服务。
     * @param eventService                core 事件服务。
     * @param timerService                定时器服务。
     * @param cleanableEasyDataRepository 可清理 EasyData 仓库。
     * @param logger                      日志服务。
     * @param objectMapper                JSON 解析器。
     */
    public AgentTokenMonitorService(IAgentFileConfigService agentFileConfigService,
                                    ILLMFileConfigService llmFileConfigService,
                                    IEventService eventService,
                                    ITimerService timerService,
                                    CleanableEasyDataRepository cleanableEasyDataRepository,
                                    ILogger logger,
                                    @Qualifier("customObjectMapper") ObjectMapper objectMapper) {
        this.agentFileConfigService = agentFileConfigService;
        this.llmFileConfigService = llmFileConfigService;
        this.eventService = eventService;
        this.timerService = timerService;
        this.cleanableEasyDataRepository = cleanableEasyDataRepository;
        this.logger = logger;
        this.objectMapper = objectMapper;
    }

    /**
     * 记录一次成功的模型调用 token 使用量。
     *
     * @param turnData      Agent 单轮运行数据。
     * @param modelCallData Agent 模型调用事件数据。
     */
    public void recordModelCall(AgentTurnData turnData, AgentModelCallData modelCallData) {
        try {
            var config = tokenMonitorConfig();
            if (!config.isEnabled()) {
                return;
            }
            var result = modelCallData.findResult().orElse(null);
            if (result == null) {
                return;
            }
            var record = buildUsageRecord(config, modelCallData, result);
            storeContextIfNecessary(config, modelCallData.getPrompt(), record);
            storeUsageRecord(record);
            turnData.addTokenUsageRecord(record);
        } catch (Exception exception) {
            logger.warn(LOG_NAMESPACE, "记录 Agent token 使用量失败。", exception);
        }
    }

    /**
     * 记录模型调用异常并触发异常告警。
     *
     * @param turnData      Agent 单轮运行数据。
     * @param modelCallData Agent 模型调用事件数据。
     * @param throwable     模型调用异常。
     */
    public void recordModelCallException(AgentTurnData turnData,
                                         AgentModelCallData modelCallData,
                                         Throwable throwable) {
        try {
            var config = tokenMonitorConfig();
            if (!config.isEnabled()) {
                return;
            }
            var reason = "模型调用异常：" + (throwable == null ? "unknown" : throwable.getMessage());
            var data = new AgentTokenWarningData(
                    turnData,
                    AgentTokenUsageLevel.ABNORMAL,
                    reason,
                    turnData.snapshotTokenUsageRecords(),
                    throwable
            );
            pushWarningEventAndLog(data, config);
        } catch (Exception exception) {
            logger.warn(LOG_NAMESPACE, "记录 Agent token 异常告警失败。", exception);
        }
    }

    /**
     * 在单轮对话结束后按配置判断是否需要推送 token 告警事件。
     *
     * @param turnData Agent 单轮运行数据。
     */
    public void warnIfNecessary(AgentTurnData turnData) {
        try {
            var config = tokenMonitorConfig();
            if (!config.isEnabled()) {
                return;
            }
            var records = turnData.snapshotTokenUsageRecords();
            if (records.isEmpty()) {
                return;
            }
            var level = aggregateLevel(records);
            if (level == AgentTokenUsageLevel.NORMAL) {
                return;
            }
            var data = new AgentTokenWarningData(
                    turnData,
                    level,
                    buildWarningReason(records, level),
                    records,
                    null
            );
            pushWarningEventAndLog(data, config);
        } catch (Exception exception) {
            logger.warn(LOG_NAMESPACE, "处理 Agent token 告警失败。", exception);
        }
    }

    /**
     * 注册上下文快照清理定时任务。
     *
     * @param ignored Spring 应用就绪事件。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void registerCleanupTimer(ApplicationReadyEvent ignored) {
        try {
            var config = tokenMonitorConfig();
            if (!config.isEnabled() || !config.isCleanupEnabled() || isBlank(config.getCleanupCron())) {
                return;
            }
            timerService.registerByCron(
                    "agent-token-context-cleanup",
                    AgentTurnEvent.NAMESPACE,
                    () -> {
                        cleanupExpiredContextSnapshots();
                        return EmptyTaskResult.INSTANCE;
                    },
                    config.getCleanupCron(),
                    EmptyTaskResult.getResultType()
            );
        } catch (Exception exception) {
            logger.warn(LOG_NAMESPACE, "注册 Agent token 上下文清理任务失败。", exception);
        }
    }

    /**
     * 清理已过期的上下文快照。
     *
     * @return 清理的上下文快照数量。
     */
    public int cleanupExpiredContextSnapshots() {
        var now = Instant.now();
        var cleaned = 0;
        for (var dataObject : cleanableEasyDataRepository.findAll()) {
            if (!CONTEXT_NAMESPACE.equals(dataObject.getNamespace())
                    || !CONTEXT_KEY.equals(dataObject.getKey())) {
                continue;
            }
            try {
                var snapshot = objectMapper.readValue(dataObject.getValue(), AgentTokenContextSnapshot.class);
                var expiresAt = snapshot.getExpiresAt();
                if (expiresAt == null || expiresAt.isAfter(now)) {
                    continue;
                }
                cleanableEasyDataRepository.deleteByNamespaceAndTargetAndKey(
                        dataObject.getNamespace(),
                        dataObject.getTarget(),
                        dataObject.getKey()
                );
                cleaned++;
            } catch (Exception exception) {
                logger.warn(LOG_NAMESPACE, "清理 Agent token 上下文快照失败。", exception);
            }
        }
        return cleaned;
    }

    /**
     * 构造 token 使用记录。
     *
     * @param config        token 监控器配置。
     * @param modelCallData Agent 模型调用事件数据。
     * @param result        模型调用结果。
     * @return token 使用记录。
     */
    private AgentTokenUsageRecord buildUsageRecord(AgentTokenMonitorFileConfig config,
                                                   AgentModelCallData modelCallData,
                                                   ILLMResult result) {
        var prompt = modelCallData.getPrompt();
        var options = effectiveOptions(prompt);
        var usage = safeUsage(result.getUsage());
        var promptTokens = usage.promptTokens();
        var completionTokens = usage.completionTokens();
        var totalTokens = usage.totalTokens() > 0
                ? usage.totalTokens()
                : promptTokens + completionTokens;
        var contextWindowTokens = positiveOrNull(config.getContextWindowTokens());
        var maxCompletionTokens = options.findMaxTokens().orElse(null);
        var contextRemainingTokens = subtractPositive(contextWindowTokens, promptTokens);
        var completionRemainingTokens = subtractPositive(maxCompletionTokens, completionTokens);
        var contextUsageRatio = ratio(promptTokens, contextWindowTokens);
        var completionUsageRatio = ratio(completionTokens, maxCompletionTokens);
        var level = classify(config, totalTokens, contextRemainingTokens, completionRemainingTokens,
                contextUsageRatio, completionUsageRatio);
        return AgentTokenUsageRecord.builder()
                .usageId(UUID.randomUUID().toString())
                .api(resolveApi(options))
                .provider(options.findProvider().orElse(""))
                .model(firstPresent(result.getModel(), options.findModel().orElse("")))
                .scopeId(modelCallData.getContext().getScopeId())
                .agentId(modelCallData.getContext().getAgentId())
                .userId(modelCallData.getContext().getUserId())
                .conversationId(modelCallData.getContext().getConversationId())
                .requestMessageId(modelCallData.getInboundMessage().getMessageId())
                .round(modelCallData.getRound())
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(totalTokens)
                .promptCacheHitTokens(usage.promptCacheHitTokens())
                .promptCacheMissTokens(usage.promptCacheMissTokens())
                .reasoningTokens(usage.reasoningTokens())
                .contextMessageCount(prompt.getMessages().size())
                .contextCharacterLength(renderContext(prompt, Integer.MAX_VALUE).originalLength())
                .contextWindowTokens(contextWindowTokens)
                .contextRemainingTokens(contextRemainingTokens)
                .maxCompletionTokens(maxCompletionTokens)
                .completionRemainingTokens(completionRemainingTokens)
                .contextUsageRatio(contextUsageRatio)
                .completionUsageRatio(completionUsageRatio)
                .level(level)
                .retentionCategory(retentionCategory(config, level, totalTokens))
                .createdAt(Instant.now())
                .build();
    }

    /**
     * 在需要时保存上下文快照。
     *
     * @param config token 监控器配置。
     * @param prompt 本次模型调用 prompt。
     * @param record token 使用记录。
     */
    private void storeContextIfNecessary(AgentTokenMonitorFileConfig config,
                                         LLMPrompt prompt,
                                         AgentTokenUsageRecord record) {
        if (!config.isRecordContext()) {
            return;
        }
        var expiresAt = contextExpiresAt(config, record.getLevel(), record.getTotalTokens());
        if (record.getLevel() == AgentTokenUsageLevel.ABNORMAL
                && config.getAbnormalContextRetentionDays() == 0) {
            return;
        }
        var rendered = renderContext(prompt, Math.max(0, config.getMaxContextSnapshotCharacters()));
        var snapshot = AgentTokenContextSnapshot.builder()
                .usageId(record.getUsageId())
                .api(record.getApi())
                .provider(record.getProvider())
                .model(record.getModel())
                .scopeId(record.getScopeId())
                .agentId(record.getAgentId())
                .userId(record.getUserId())
                .conversationId(record.getConversationId())
                .requestMessageId(record.getRequestMessageId())
                .round(record.getRound())
                .level(record.getLevel())
                .totalTokens(record.getTotalTokens())
                .originalCharacterLength(rendered.originalLength())
                .storedCharacterLength(rendered.storedLength())
                .truncated(rendered.truncated())
                .messages(rendered.messages())
                .createdAt(record.getCreatedAt())
                .expiresAt(expiresAt)
                .build();
        CleanableEasyData.of(CONTEXT_NAMESPACE)
                .get(record.getUsageId())
                .getTaskConfig(DATABASE_TASK)
                .set(CONTEXT_KEY, snapshot)
                .pushWithFuture()
                .finish()
                .join();
        record.setContextSnapshotStored(true);
        record.setContextSnapshotTarget(record.getUsageId());
        record.setContextSnapshotKey(CONTEXT_KEY);
        record.setContextExpiresAt(expiresAt);
    }

    /**
     * 保存 token 使用摘要。
     *
     * @param record token 使用记录。
     */
    private void storeUsageRecord(AgentTokenUsageRecord record) {
        DebugEasyData.of(USAGE_NAMESPACE)
                .get(usageTarget(record))
                .getTaskConfig(DATABASE_TASK)
                .set(record.getUsageId(), record)
                .pushWithFuture()
                .finish()
                .join();
    }

    /**
     * 推送告警事件并在事件完成后写入 warn 日志。
     *
     * @param data   token 告警数据。
     * @param config token 监控器配置。
     */
    private void pushWarningEventAndLog(AgentTokenWarningData data, AgentTokenMonitorFileConfig config) {
        try {
            eventService.pushEventWithFuture(new AgentTokenWarningEvent(data)).finish().join();
        } catch (Exception exception) {
            logger.warn(LOG_NAMESPACE, "推送 Agent token 告警事件失败。", exception);
        }
        var message = formatWarningMessage(data);
        if (shouldReportToDebugger(data.getLevel(), config)) {
            logger.errorWithReport(LOG_NAMESPACE, message, data.getThrowable(), blankToNull(config.getReportRecipients()));
        }
        logger.warn(LOG_NAMESPACE, message, data.getThrowable());
    }

    /**
     * 读取 token 监控器配置。
     *
     * @return token 监控器配置。
     */
    private AgentTokenMonitorFileConfig tokenMonitorConfig() {
        return agentFileConfigService.getTokenMonitorConfig();
    }

    /**
     * 构造用于记录日志的 EasyData target。
     *
     * @param record token 使用记录。
     * @return EasyData target。
     */
    private static String usageTarget(AgentTokenUsageRecord record) {
        var date = LocalDate.ofInstant(record.getCreatedAt(), ZoneId.systemDefault());
        return "%s/%s/%s".formatted(record.getScopeId(), record.getAgentId(), date);
    }

    /**
     * 渲染 prompt 上下文。
     *
     * @param prompt 本次模型调用 prompt。
     * @param limit  最大保存字符数。
     * @return 渲染后的上下文信息。
     */
    private static RenderedContext renderContext(LLMPrompt prompt, int limit) {
        var messages = new ArrayList<String>();
        var originalLength = 0;
        var storedLength = 0;
        var remaining = Math.max(0, limit);
        var truncated = false;
        for (var message : prompt.getMessages()) {
            var rendered = renderMessage(message);
            originalLength += rendered.length();
            if (remaining <= 0) {
                truncated = true;
                continue;
            }
            if (rendered.length() > remaining) {
                messages.add(rendered.substring(0, remaining));
                storedLength += remaining;
                remaining = 0;
                truncated = true;
                continue;
            }
            messages.add(rendered);
            storedLength += rendered.length();
            remaining -= rendered.length();
        }
        return new RenderedContext(List.copyOf(messages), originalLength, storedLength, truncated);
    }

    /**
     * 渲染单条 LLM message。
     *
     * @param message LLM message。
     * @return 渲染后的文本。
     */
    private static String renderMessage(ILLMMessage message) {
        var builder = new StringBuilder();
        builder.append("role: ").append(message.getRole());
        if (!isBlank(message.getName())) {
            builder.append('\n').append("name: ").append(message.getName().trim());
        }
        builder.append('\n').append("content:");
        for (var content : message.getContents()) {
            builder.append('\n').append(renderContent(content));
        }
        return builder.toString();
    }

    /**
     * 渲染单个 content。
     *
     * @param content LLM content。
     * @return 渲染后的文本。
     */
    private static String renderContent(ILLMContent content) {
        if (content == null) {
            return "";
        } else {
            content.getContentPart();
        }
        if (content.getContentPart() instanceof TextLLMContentPart textPart) {
            return textPart.getText();
        }
        return "[%s:%s]".formatted(content.getType(), content.getContentPart().getClass().getSimpleName());
    }

    /**
     * 计算有效模型调用选项。
     *
     * @param prompt 本次模型调用 prompt。
     * @return 有效模型调用选项。
     */
    private LLMModelOptions effectiveOptions(LLMPrompt prompt) {
        var promptOptions = prompt.getOptions();
        var provider = resolveProvider(promptOptions);
        if (isBlank(provider)) {
            return LLMModelOptions.copyOf(promptOptions);
        }
        try {
            var modelId = LLMModelId.of(provider);
            var configured = llmFileConfigService.getDefaultOptions(
                    modelId,
                    LLMModelOptions.builder().provider(provider).build()
            );
            return LLMModelOptions.merge(configured, promptOptions);
        } catch (Exception ignored) {
            return LLMModelOptions.merge(
                    LLMModelOptions.builder().provider(provider).build(),
                    promptOptions
            );
        }
    }

    /**
     * 解析本次调用使用的 provider。
     *
     * @param options prompt options。
     * @return provider。
     */
    private String resolveProvider(@Nullable ILLMModelOptions options) {
        if (options != null) {
            var provider = options.findProvider().orElse("");
            if (!provider.isBlank()) {
                return provider;
            }
        }
        return agentFileConfigService.findDefaultModelId()
                .map(LLMModelId::id)
                .orElse("");
    }

    /**
     * 从 options 中解析 API 标识。
     *
     * @param options 模型调用选项。
     * @return API 标识。
     */
    private static String resolveApi(LLMModelOptions options) {
        var metadata = options.findMetadata().orElse(Map.of());
        var api = stringValue(metadata.get("api"));
        var baseUrl = stringValue(metadata.get("baseUrl"));
        if (!isBlank(baseUrl) && !isBlank(api)) {
            return baseUrl + api;
        }
        if (!isBlank(api)) {
            return api;
        }
        return options.findProvider().orElse("");
    }

    /**
     * 按配置分类 token 使用级别。
     *
     * @param config                    token 监控器配置。
     * @param totalTokens               总 token 数。
     * @param contextRemainingTokens    上下文剩余 token。
     * @param completionRemainingTokens completion 剩余 token。
     * @param contextUsageRatio         上下文使用比例。
     * @param completionUsageRatio      completion 使用比例。
     * @return token 使用级别。
     */
    private static AgentTokenUsageLevel classify(AgentTokenMonitorFileConfig config,
                                                 int totalTokens,
                                                 @Nullable Integer contextRemainingTokens,
                                                 @Nullable Integer completionRemainingTokens,
                                                 double contextUsageRatio,
                                                 double completionUsageRatio) {
        if (totalTokens >= Math.max(1, config.getAbnormalUsageTokenThreshold())
                || contextUsageRatio >= config.getAbnormalUsageRatio()
                || completionUsageRatio >= config.getAbnormalUsageRatio()) {
            return AgentTokenUsageLevel.ABNORMAL;
        }
        if (contextUsageRatio >= config.getWarningUsageRatio()
                || completionUsageRatio >= config.getWarningUsageRatio()
                || isLowRemaining(config, contextRemainingTokens)
                || isLowRemaining(config, completionRemainingTokens)) {
            return AgentTokenUsageLevel.WARNING;
        }
        return AgentTokenUsageLevel.NORMAL;
    }

    /**
     * 获取上下文保留分类。
     *
     * @param config      token 监控器配置。
     * @param level       token 使用级别。
     * @param totalTokens 总 token 数。
     * @return 保留分类。
     */
    private static String retentionCategory(AgentTokenMonitorFileConfig config,
                                            AgentTokenUsageLevel level,
                                            int totalTokens) {
        if (level == AgentTokenUsageLevel.ABNORMAL) {
            return "abnormal";
        }
        return totalTokens < Math.max(1, config.getShortUsageTokenThreshold())
                ? "short"
                : "long";
    }

    /**
     * 计算上下文快照过期时间。
     *
     * @param config      token 监控器配置。
     * @param level       token 使用级别。
     * @param totalTokens 总 token 数。
     * @return 上下文快照过期时间；为 {@code null} 时不自动清理。
     */
    @Nullable
    private static Instant contextExpiresAt(AgentTokenMonitorFileConfig config,
                                            AgentTokenUsageLevel level,
                                            int totalTokens) {
        var days = switch (level) {
            case ABNORMAL -> config.getAbnormalContextRetentionDays();
            case WARNING, NORMAL -> totalTokens < Math.max(1, config.getShortUsageTokenThreshold())
                    ? config.getShortContextRetentionDays()
                    : config.getLongContextRetentionDays();
        };
        if (days < 0) {
            return null;
        }
        return Instant.now().plusSeconds(days * 86_400L);
    }

    /**
     * 聚合本轮 token 使用级别。
     *
     * @param records token 使用记录。
     * @return 聚合后的 token 使用级别。
     */
    private static AgentTokenUsageLevel aggregateLevel(List<AgentTokenUsageRecord> records) {
        var level = AgentTokenUsageLevel.NORMAL;
        for (var record : records) {
            if (record != null && record.getLevel().ordinal() > level.ordinal()) {
                level = record.getLevel();
            }
        }
        return level;
    }

    /**
     * 构造 token 告警原因。
     *
     * @param records token 使用记录。
     * @param level   聚合 token 使用级别。
     * @return token 告警原因。
     */
    private static String buildWarningReason(List<AgentTokenUsageRecord> records, AgentTokenUsageLevel level) {
        var totalTokens = 0;
        var lowestRemaining = Integer.MAX_VALUE;
        for (var record : records) {
            if (record == null) {
                continue;
            }
            totalTokens += record.getTotalTokens();
            lowestRemaining = minNullable(lowestRemaining, record.getContextRemainingTokens());
            lowestRemaining = minNullable(lowestRemaining, record.getCompletionRemainingTokens());
        }
        if (lowestRemaining == Integer.MAX_VALUE) {
            return "Agent token 使用达到 %s 级别，总消耗 %d tokens。".formatted(level, totalTokens);
        }
        return "Agent token 使用达到 %s 级别，总消耗 %d tokens，估算最小剩余 %d tokens。"
                .formatted(level, totalTokens, lowestRemaining);
    }

    /**
     * 格式化 token 告警日志。
     *
     * @param data token 告警数据。
     * @return token 告警日志。
     */
    private static String formatWarningMessage(AgentTokenWarningData data) {
        return """
                %s
                level: %s
                promptTokens: %d
                completionTokens: %d
                totalTokens: %d
                reasoningTokens: %d
                records: %d
                """.formatted(
                data.getReason(),
                data.getLevel(),
                data.getPromptTokens(),
                data.getCompletionTokens(),
                data.getTotalTokens(),
                data.getReasoningTokens(),
                data.getRecords().size()
        ).trim();
    }

    /**
     * 判断是否需要向 debugger 报告。
     *
     * @param level  token 使用级别。
     * @param config token 监控器配置。
     * @return 是否需要报告。
     */
    private static boolean shouldReportToDebugger(AgentTokenUsageLevel level,
                                                  AgentTokenMonitorFileConfig config) {
        return switch (level) {
            case ABNORMAL -> config.isReportAbnormalToDebugger();
            case WARNING -> config.isReportWarningToDebugger();
            case NORMAL -> false;
        };
    }

    /**
     * 判断剩余 token 是否较低。
     *
     * @param config    token 监控器配置。
     * @param remaining 剩余 token。
     * @return 是否较低。
     */
    private static boolean isLowRemaining(AgentTokenMonitorFileConfig config, @Nullable Integer remaining) {
        return remaining != null && remaining >= 0 && remaining <= Math.max(0, config.getLowRemainingTokenThreshold());
    }

    /**
     * 计算比例。
     *
     * @param value 分子。
     * @param limit 分母。
     * @return 比例。
     */
    private static double ratio(int value, @Nullable Integer limit) {
        if (limit == null || limit <= 0) {
            return 0D;
        }
        return Math.max(0D, value / (double) limit);
    }

    /**
     * 正数相减。
     *
     * @param limit 上限。
     * @param value 已使用值。
     * @return 剩余值。
     */
    @Nullable
    private static Integer subtractPositive(@Nullable Integer limit, int value) {
        if (limit == null || limit <= 0) {
            return null;
        }
        return limit - Math.max(0, value);
    }

    /**
     * 正数或空值。
     *
     * @param value 原始值。
     * @return 正数或空值。
     */
    @Nullable
    private static Integer positiveOrNull(int value) {
        return value > 0 ? value : null;
    }

    /**
     * 读取 usage 字段。
     *
     * @param usage LLM usage。
     * @return 安全 usage 视图。
     */
    private static SafeUsage safeUsage(@Nullable ILLMUsage usage) {
        if (usage == null) {
            return new SafeUsage(0, 0, 0, 0, 0, 0);
        }
        return new SafeUsage(
                usage.getCompletionTokens(),
                usage.getPromptTokens(),
                usage.getTotalTokens(),
                usage.getPromptCacheHitTokens(),
                usage.getPromptCacheMissTokens(),
                usage.getReasoningTokens()
        );
    }

    /**
     * 取第一个非空白文本。
     *
     * @param primary  主文本。
     * @param fallback 备用文本。
     * @return 第一个非空白文本。
     */
    private static String firstPresent(@Nullable String primary, String fallback) {
        if (!isBlank(primary)) {
            return primary.trim();
        }
        return fallback == null ? "" : fallback;
    }

    /**
     * 把对象转换为字符串。
     *
     * @param value 原始对象。
     * @return 字符串。
     */
    private static String stringValue(@Nullable Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 空白字符串转为空值。
     *
     * @param value 原始字符串。
     * @return 非空白字符串或 {@code null}。
     */
    @Nullable
    private static String blankToNull(@Nullable String value) {
        return isBlank(value) ? null : value;
    }

    /**
     * 判断字符串是否为空白。
     *
     * @param value 原始字符串。
     * @return 是否为空白。
     */
    private static boolean isBlank(@Nullable String value) {
        return value == null || value.isBlank();
    }

    /**
     * 计算可空值最小值。
     *
     * @param current 当前最小值。
     * @param value   新值。
     * @return 新的最小值。
     */
    private static int minNullable(int current, @Nullable Integer value) {
        if (value == null) {
            return current;
        }
        return Math.min(current, value);
    }

    /**
     * 安全 usage 视图。
     *
     * @param completionTokens       completion token 数。
     * @param promptTokens           prompt token 数。
     * @param totalTokens            总 token 数。
     * @param promptCacheHitTokens   prompt 缓存命中 token 数。
     * @param promptCacheMissTokens  prompt 缓存未命中 token 数。
     * @param reasoningTokens        reasoning token 数。
     */
    private record SafeUsage(
            int completionTokens,
            int promptTokens,
            int totalTokens,
            int promptCacheHitTokens,
            int promptCacheMissTokens,
            int reasoningTokens
    ) {
    }

    /**
     * 渲染后的上下文。
     *
     * @param messages       渲染后的消息列表。
     * @param originalLength 原始字符数。
     * @param storedLength   保存字符数。
     * @param truncated      是否截断。
     */
    private record RenderedContext(
            List<String> messages,
            int originalLength,
            int storedLength,
            boolean truncated
    ) {
    }
}
