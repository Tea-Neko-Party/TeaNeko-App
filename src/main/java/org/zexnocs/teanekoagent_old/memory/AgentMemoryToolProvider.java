package org.zexnocs.teanekoagent_old.memory;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent_old.agent.prompt.AgentRequestContext;
import org.zexnocs.teanekoagent_old.llm.framework.tool.api.LLMToolMapping;
import org.zexnocs.teanekoagent_old.llm.framework.tool.api.LLMToolProvider;
import org.zexnocs.teanekocore.framework.description.Description;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Agent 长期记忆 LLM 工具提供者。
 * <br>该类通过 LLM framework 的工具注解暴露显式记忆查询和用户事实写入能力，供模型在 tool call loop 中按需调用。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@LLMToolProvider(toolPackage = "memory")
@RequiredArgsConstructor
public class AgentMemoryToolProvider {
    /**
     * 记忆查询工具的默认返回数量。
     */
    private static final int DEFAULT_TOOL_MEMORY_LIMIT = 8;

    /**
     * 记忆查询工具允许的最大返回数量。
     */
    private static final int MAX_TOOL_MEMORY_LIMIT = 20;

    /**
     * Agent 记忆查询服务。
     */
    private final AgentMemoryQueryService memoryQueryService;

    /**
     * 查询当前 scope、agent 和 user 下的长期记忆。
     *
     * @param scopeId 当前作用域 ID。
     * @param agentId 当前 agent ID。
     * @param userId  当前用户 ID。
     * @param limit   最大返回数量；为空或非法时使用默认值。
     * @return 可直接作为 tool message 回填给模型的记忆查询结果。
     */
    @LLMToolMapping(
            name = "query_memory",
            description = "Query stable long-term memories for the current scope, agent, and user."
    )
    public String queryMemory(@Description("Current scope id.") String scopeId,
                              @Description("Current agent id.") String agentId,
                              @Description("Current user id.") String userId,
                              @Description("Maximum result count.") @Nullable Integer limit) {
        if (isBlank(scopeId) || isBlank(agentId) || isBlank(userId)) {
            return "Memory query skipped: scopeId, agentId, and userId are required.";
        }
        var records = memoryQueryService.findRelevant(
                AgentRequestContext.of(scopeId, agentId, userId),
                normalizeLimit(limit)
        );
        if (records.isEmpty()) {
            return "No relevant memory.";
        }
        var text = new StringBuilder();
        for (var index = 0; index < records.size(); index++) {
            var record = records.get(index);
            text.append(index + 1)
                    .append(". [")
                    .append(record.getType())
                    .append(", importance=")
                    .append(record.getImportance())
                    .append(", confidence=")
                    .append(record.getConfidence())
                    .append(", eventTime=")
                    .append(formatEventTime(record))
                    .append("] ")
                    .append(record.getContent())
                    .append('\n');
        }
        return text.toString().trim();
    }

    /**
     * 格式化记忆事件时间。
     *
     * @param record 记忆记录。
     * @return 时间描述。
     */
    private static String formatEventTime(AgentMemoryRecord record) {
        var eventTime = record.getEventTime();
        if (eventTime == null || !eventTime.isKnown()) {
            return "unknown; recordedAt=" + record.getCreatedAt();
        }
        return eventTime.getStart() + ".." + eventTime.getEnd()
                + "; precision=" + eventTime.getPrecision()
                + "; expression=" + eventTime.getOriginalExpression();
    }

    /**
     * 按 Agent 已解析出的标准时间点或时间范围查询长期记忆。
     * <br>该工具不解析“昨天”或“一周前左右”等自然语言。Agent 必须根据 Prompt 中的当前时间，
     * 先把自然语言转换成 ISO-8601 时间点或范围后再调用。
     *
     * @param scopeId   当前作用域 ID。
     * @param agentId   当前 agent ID。
     * @param userId    当前用户 ID。
     * @param timePoint 可选 ISO-8601 时间点或日期。
     * @param rangeStart 可选 ISO-8601 范围起点。
     * @param rangeEnd   可选 ISO-8601 范围终点。
     * @param limit      最大返回数量。
     * @return 与指定时间相交的记忆。
     */
    @LLMToolMapping(
            name = "query_memory_by_time",
            description = "Query long-term memories by an ISO-8601 event time point or time range. Resolve natural-language time against temporal-context before calling."
    )
    public String queryMemoryByTime(
            @Description("Current scope id.") String scopeId,
            @Description("Current agent id.") String agentId,
            @Description("Current user id.") String userId,
            @Description("Optional ISO-8601 time point or date, for example 2026-06-11T19:22:00+08:00 or 2026-06-11.") @Nullable String timePoint,
            @Description("Optional inclusive ISO-8601 range start. Use this with rangeEnd for vague time such as about a week ago.") @Nullable String rangeStart,
            @Description("Optional inclusive ISO-8601 range end. Use this with rangeStart for vague time such as about a week ago.") @Nullable String rangeEnd,
            @Description("Maximum result count.") @Nullable Integer limit) {
        if (isBlank(scopeId) || isBlank(agentId) || isBlank(userId)) {
            return "Memory query skipped: scopeId, agentId, and userId are required.";
        }
        final TimeQuery query;
        try {
            query = parseTimeQuery(timePoint, rangeStart, rangeEnd);
        } catch (IllegalArgumentException exception) {
            return "Memory query skipped: " + exception.getMessage();
        }
        if (query.start() == null && query.end() == null) {
            return "Memory query skipped: provide timePoint or at least one range boundary.";
        }
        var records = memoryQueryService.findRelevant(
                AgentRequestContext.of(scopeId, agentId, userId),
                query.start(),
                query.end(),
                normalizeLimit(limit)
        );
        return renderMemoryResults(records, query);
    }

    /**
     * 写入一条稳定用户事实或偏好记忆。
     *
     * @param scopeId    当前作用域 ID。
     * @param agentId    当前 agent ID。
     * @param userId     当前用户 ID。
     * @param content    已清洗、适合长期保存的短文本。
     * @param confidence 记忆置信度，范围为 0 到 1；为空时使用 1。
     * @param importance 记忆重要度。
     * @param eventTimePoint 事件时间点或日期。
     * @param eventTimeStart 事件时间范围起点。
     * @param eventTimeEnd 事件时间范围终点。
     * @param timePrecision 事件时间精度。
     * @param originalTimeExpression 原始自然语言时间表达。
     * @return 可直接作为 tool message 回填给模型的写入结果。
     */
    @LLMToolMapping(
            name = "write_user_fact",
            description = "Write a stable user fact or preference into long-term memory."
    )
    public String writeUserFact(@Description("Current scope id.") String scopeId,
                                @Description("Current agent id.") String agentId,
                                @Description("Current user id.") String userId,
                                @Description("Short cleaned memory content.") String content,
                                @Description("Confidence from 0 to 1.") @Nullable Double confidence,
                                @Description("Memory importance: LOW, NORMAL, HIGH, or CRITICAL.") @Nullable String importance,
                                @Description("Optional ISO-8601 event time point or date.") @Nullable String eventTimePoint,
                                @Description("Optional inclusive ISO-8601 event range start.") @Nullable String eventTimeStart,
                                @Description("Optional inclusive ISO-8601 event range end.") @Nullable String eventTimeEnd,
                                @Description("Event time precision: EXACT, MINUTE, HOUR, DAY, WEEK, MONTH, YEAR, APPROXIMATE, or UNKNOWN.") @Nullable String timePrecision,
                                @Description("Original natural-language time expression, for example about a week ago.") @Nullable String originalTimeExpression) {
        if (isBlank(scopeId) || isBlank(agentId) || isBlank(userId)) {
            return "Memory was not written: scopeId, agentId, and userId are required.";
        }
        if (isBlank(content)) {
            return "Memory was not written: content is required.";
        }

        final TimeQuery eventTime;
        try {
            eventTime = parseTimeQuery(eventTimePoint, eventTimeStart, eventTimeEnd);
        } catch (IllegalArgumentException exception) {
            return "Memory was not written: " + exception.getMessage();
        }

        var now = Instant.now();
        var record = new AgentMemoryRecord();
        record.setType(MemoryRecordType.USER_FACT);
        record.setContent(content.trim());
        record.setSource("tool:write_user_fact");
        record.setScopeId(scopeId.trim());
        record.setAgentId(agentId.trim());
        record.setSubjectId(userId.trim());
        record.setTags(List.of("tool-written"));
        record.setConfidence(clampConfidence(confidence));
        record.setImportance(parseImportance(importance));
        var range = new MemoryTimeRange();
        range.setStart(eventTime.start());
        range.setEnd(eventTime.end());
        range.setPrecision(parsePrecision(timePrecision, eventTime));
        range.setOriginalExpression(safe(originalTimeExpression));
        record.setEventTime(range);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);

        memoryQueryService.appendUserMemory(scopeId, agentId, userId, record);
        return "Memory written: " + record.getId();
    }

    /**
     * 渲染带时间信息的记忆查询结果。
     *
     * @param records 记忆列表。
     * @param query   查询时间范围。
     * @return Tool 回填文本。
     */
    private String renderMemoryResults(List<AgentMemoryRecord> records, TimeQuery query) {
        if (records.isEmpty()) {
            return "No memory overlaps time range: " + query.start() + " .. " + query.end();
        }
        var text = new StringBuilder("Memory time query: ")
                .append(query.start())
                .append(" .. ")
                .append(query.end())
                .append('\n');
        for (var index = 0; index < records.size(); index++) {
            var record = records.get(index);
            var eventTime = record.getEventTime();
            text.append(index + 1)
                    .append(". [")
                    .append(record.getType())
                    .append(", importance=")
                    .append(record.getImportance())
                    .append(", confidence=")
                    .append(record.getConfidence())
                    .append(", eventTime=")
                    .append(eventTime == null ? "unknown" : eventTime.getStart() + ".." + eventTime.getEnd())
                    .append("] ")
                    .append(record.getContent())
                    .append('\n');
        }
        return text.toString().trim();
    }

    /**
     * 解析 Tool 接收的标准 ISO 时间参数。
     *
     * @param timePoint 时间点或日期。
     * @param rangeStart 范围起点。
     * @param rangeEnd 范围终点。
     * @return 标准时间查询范围。
     */
    private static TimeQuery parseTimeQuery(@Nullable String timePoint,
                                            @Nullable String rangeStart,
                                            @Nullable String rangeEnd) {
        if (!isBlank(timePoint)) {
            if (!isBlank(rangeStart) || !isBlank(rangeEnd)) {
                throw new IllegalArgumentException("timePoint cannot be combined with rangeStart or rangeEnd");
            }
            var value = parseBoundary(timePoint, false);
            var end = isDateOnly(timePoint) ? parseBoundary(timePoint, true) : value;
            return new TimeQuery(value, end);
        }
        var start = isBlank(rangeStart) ? null : parseBoundary(rangeStart, false);
        var end = isBlank(rangeEnd) ? null : parseBoundary(rangeEnd, true);
        if (start != null && end != null && end.isBefore(start)) {
            throw new IllegalArgumentException("time range end must not be before start");
        }
        return new TimeQuery(start, end);
    }

    /**
     * 解析 ISO-8601 时间边界。
     *
     * @param value 原始标准时间。
     * @param endOfDate 日期输入是否取当天结束时间。
     * @return UTC 时间点。
     */
    private static Instant parseBoundary(String value, boolean endOfDate) {
        var normalized = value.trim();
        try {
            return Instant.parse(normalized);
        } catch (DateTimeParseException ignored) {
            // Continue with offset date-time and date-only formats.
        }
        try {
            return OffsetDateTime.parse(normalized).toInstant();
        } catch (DateTimeParseException ignored) {
            // Continue with date-only format.
        }
        try {
            var date = LocalDate.parse(normalized);
            var zoneId = ZoneId.systemDefault();
            return endOfDate
                    ? date.plusDays(1).atStartOfDay(zoneId).toInstant().minusNanos(1)
                    : date.atStartOfDay(zoneId).toInstant();
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("time values must use ISO-8601 format: " + value, exception);
        }
    }

    /**
     * 判断输入是否为 ISO 日期。
     *
     * @param value 原始值。
     * @return 是否为日期格式。
     */
    private static boolean isDateOnly(String value) {
        try {
            LocalDate.parse(value.trim());
            return true;
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }

    /**
     * 解析记忆重要度。
     *
     * @param value 重要度字符串。
     * @return 记忆重要度。
     */
    private static MemoryImportance parseImportance(@Nullable String value) {
        if (isBlank(value)) {
            return MemoryImportance.NORMAL;
        }
        try {
            return MemoryImportance.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return MemoryImportance.NORMAL;
        }
    }

    /**
     * 解析事件时间精度。
     *
     * @param value 精度字符串。
     * @param query 已解析时间范围。
     * @return 时间精度。
     */
    private static MemoryTimePrecision parsePrecision(@Nullable String value, TimeQuery query) {
        if (!isBlank(value)) {
            try {
                return MemoryTimePrecision.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // Use inferred precision below.
            }
        }
        if (query.start() == null && query.end() == null) {
            return MemoryTimePrecision.UNKNOWN;
        }
        return query.start() != null && query.start().equals(query.end())
                ? MemoryTimePrecision.EXACT
                : MemoryTimePrecision.APPROXIMATE;
    }

    /**
     * 规范化可空字符串。
     *
     * @param value 原始字符串。
     * @return 非空字符串。
     */
    private static String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * Tool 标准时间查询范围。
     *
     * @param start 范围起点。
     * @param end   范围终点。
     */
    private record TimeQuery(@Nullable Instant start, @Nullable Instant end) {
    }

    /**
     * 规范化记忆查询数量。
     *
     * @param limit 原始查询数量。
     * @return 夹取到合法范围内的查询数量。
     */
    private static int normalizeLimit(@Nullable Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_TOOL_MEMORY_LIMIT;
        }
        return Math.min(limit, MAX_TOOL_MEMORY_LIMIT);
    }

    /**
     * 规范化记忆置信度。
     *
     * @param confidence 原始置信度。
     * @return 夹取到 0 到 1 范围内的置信度。
     */
    private static double clampConfidence(@Nullable Double confidence) {
        if (confidence == null || confidence.isNaN()) {
            return 1.0;
        }
        return Math.clamp(confidence, 0.0, 1.0);
    }

    /**
     * 判断字符串是否为空白。
     *
     * @param value 待判断字符串。
     * @return 如果字符串为 {@code null} 或空白则返回 {@code true}。
     */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
