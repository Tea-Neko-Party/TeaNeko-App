package org.zexnocs.teanekoagent.adapter.memory;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.memory.fact.MemoryEventTime;
import org.zexnocs.teanekoagent.memory.fact.MemoryFactProvenance;
import org.zexnocs.teanekoagent.memory.fact.MemoryFactValue;
import org.zexnocs.teanekoagent.memory.fact.legacy.LegacyMemoryLocation;
import org.zexnocs.teanekoagent.memory.fact.legacy.LegacyMemorySnapshot;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

/**
 * 只解析旧 memory/profile 数组，不实例化带随机 ID 和当前时间默认值的旧 Bean。
 * 所有调整保留原 JSON；未知结构和超限资料整体失败，不静默截断或覆盖来源。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class LegacyMemoryDecoder {
    public static final int MAX_BYTES = 1_048_576;
    public static final int MAX_RECORDS = 1000;
    private static final Set<String> FIELDS = Set.of("id", "type", "content", "source", "scopeId", "agentId", "subjectId", "tags",
            "confidence", "importance", "eventTime", "createdAt", "updatedAt", "expiresAt", "locked");
    private static final Set<String> OTHER_TYPES = Set.of("RELATIONSHIP", "PERSONALITY_DELTA", "EPISODE_SUMMARY");
    private final ObjectMapper mapper;

    /**
     * 固定严格 JSON 解析，不改变共享转换器的配置。
     * @param mapper 本地转换器
     * @throws NullPointerException 当转换器为空时
     */
    public LegacyMemoryDecoder(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "旧 JSON 转换器不能为空。").rebuild()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    }

    /**
     * 生成可重复预览，缺失时间仅使用调用者固定估算，不读取系统时钟。
     * @param location 旧物理定位
     * @param version 原行版本
     * @param json 完整旧原文
     * @param fallbackRecordedAt 缺失时间估算
     * @return 不带授权含义的转换快照
     * @throws NullPointerException 当必需输入为空时
     * @throws IllegalArgumentException 当 JSON、定位、时间、类型或大小无效时
     */
    public LegacyMemorySnapshot decode(LegacyMemoryLocation location, long version, String json, Instant fallbackRecordedAt) {
        Objects.requireNonNull(location, "旧资料定位不能为空。"); Objects.requireNonNull(json, "旧资料原文不能为空。");
        Objects.requireNonNull(fallbackRecordedAt, "旧资料估算时刻不能为空。");
        try {
            if (json.length() > MAX_BYTES || json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) { throw new IllegalArgumentException(); }
            var root = mapper.readTree(json);
            if (root == null || !root.isArray() || root.size() > MAX_RECORDS) { throw new IllegalArgumentException(); }
            var entries = new ArrayList<LegacyMemorySnapshot.Entry>(); var skipped = new ArrayList<LegacyMemorySnapshot.Skipped>();
            var ids = new HashSet<String>();
            for (int index = 0; index < root.size(); index++) {
                var node = root.get(index); requireFields(node, FIELDS); var changes = new TreeSet<String>();
                var id = string(node, "id", "");
                if (!node.has("id")) { id = "row-index:" + index; changes.add("缺失标识按行内位置固定"); }
                if (id.isBlank() || id.length() > 256 || !ids.add(id)) { throw new IllegalArgumentException(); }
                var type = string(node, "type", "USER_FACT");
                if (OTHER_TYPES.contains(type)) { skipped.add(new LegacyMemorySnapshot.Skipped(id, type)); continue; }
                var kind = switch (type) {
                    case "USER_FACT" -> MemoryFactValue.Kind.FACT;
                    case "PREFERENCE" -> MemoryFactValue.Kind.PREFERENCE;
                    default -> throw new IllegalArgumentException();
                };
                requireIdentity(node, "scopeId", location.scopeId()); requireIdentity(node, "agentId", location.agentId());
                string(node, "source", "");
                var subject = string(node, "subjectId", "");
                if (subject.isBlank()) { subject = location.subjectKey(); changes.add("缺失主体使用明确的旧画像键"); }
                var created = instant(node, "createdAt");
                if (created == null) { created = fallbackRecordedAt; changes.add("缺失创建时间使用固定估算"); }
                var updated = instant(node, "updatedAt");
                if (updated == null) { updated = created; changes.add("缺失更新时间使用创建时间"); }
                var confidence = node.get("confidence"); var locked = node.get("locked");
                if (confidence != null && !confidence.isNumber() || locked != null && !locked.isBoolean()) { throw new IllegalArgumentException(); }
                var value = new MemoryFactValue(kind, string(node, "content", ""), tags(node), confidence == null ? 1 : confidence.doubleValue(),
                        MemoryFactValue.Importance.valueOf(string(node, "importance", "NORMAL")), event(node.get("eventTime"), created, changes), instant(node, "expiresAt"));
                var original = mapper.writeValueAsString(node);
                var provenance = new MemoryFactProvenance("llm_related.memory.v1", location.target(), location.key(), id, digest(original), original, changes);
                entries.add(new LegacyMemorySnapshot.Entry(id, subject, value, created, updated, locked != null && locked.booleanValue(), provenance));
            }
            return new LegacyMemorySnapshot(location, version, digest(json), entries, skipped);
        } catch (RuntimeException invalid) { throw new IllegalArgumentException("旧记忆 JSON 的结构、定位、时间或容量不受支持，原数据未修改。"); }
    }

    /**
     * 拒绝未知字段和非对象结构，避免把未来格式当作空默认资料。
     * @param node 待检查对象
     * @param fields 当前已知字段
     * @throws IllegalArgumentException 当对象或字段无效时
     */
    private static void requireFields(JsonNode node, Set<String> fields) {
        if (!node.isObject()) { throw new IllegalArgumentException(); }
        for (var property : node.properties()) { if (!fields.contains(property.getKey())) { throw new IllegalArgumentException(); } }
    }

    /**
     * 使用明确的旧字段默认值，显式错误类型不强制转字符串。
     * @param node 对象
     * @param key 字段名
     * @param fallback 缺失时的默认值
     * @return 原字符串或默认值
     * @throws IllegalArgumentException 当字段不是字符串时
     */
    private static String string(JsonNode node, String key, String fallback) {
        var field = node.get(key); if (field == null) { return fallback; }
        if (!field.isString()) { throw new IllegalArgumentException(); } return field.stringValue();
    }

    /**
     * 校验旧正文声明的来源与实际行一致；空声明由明确物理定位解释。
     * @param node 原对象
     * @param key 来源字段
     * @param expected 实际定位
     * @throws IllegalArgumentException 当非空声明指向另一来源时
     */
    private static void requireIdentity(JsonNode node, String key, String expected) {
        var value = string(node, key, ""); if (!value.isEmpty() && !value.equals(expected)) { throw new IllegalArgumentException(); }
    }

    /**
     * 解析 ISO 时间，不使用服务器时区或隐式当前时间。
     * @param node 原对象
     * @param key 时间字段
     * @return 原时刻或空
     * @throws IllegalArgumentException 当时间类型或 ISO 表达错误时
     */
    private static @Nullable Instant instant(JsonNode node, String key) {
        var field = node.get(key); if (field == null || field.isNull()) { return null; }
        return Instant.parse(string(node, key, ""));
    }

    /**
     * 保留标签并由领域内容统一校验容量，不截断或修剪原标签。
     * @param node 原对象
     * @return 标签列表
     * @throws IllegalArgumentException 当标签不是字符串数组时
     */
    private static List<String> tags(JsonNode node) {
        var field = node.get("tags"); if (field == null) { return List.of(); }
        if (!field.isArray()) { throw new IllegalArgumentException(); }
        var result = new ArrayList<String>();
        for (var tag : field) { if (!tag.isString()) { throw new IllegalArgumentException(); } result.add(tag.stringValue()); }
        return result;
    }

    /**
     * 保留旧时间查询语义；仅有终点时补入原记录时间，倒置区间交换并记录调整。
     * @param node 原时间对象
     * @param created 原记录时间或明确估算
     * @param changes 调整说明集合
     * @return 新事件时间
     * @throws IllegalArgumentException 当时间或精度无法解释时
     */
    private static MemoryEventTime event(@Nullable JsonNode node, Instant created, Set<String> changes) {
        if (node == null || node.isNull()) { changes.add("缺失事件时间保留未知精度"); return MemoryEventTime.unknown(); }
        requireFields(node, Set.of("start", "end", "precision", "originalExpression", "known"));
        var start = instant(node, "start"); var end = instant(node, "end");
        // 旧 Bean 的 isKnown 也会被序列化；它只描述原端点，不能替代端点内容。
        var known = node.get("known");
        if (known != null && (!known.isBoolean() || known.booleanValue() != (start != null || end != null))) { throw new IllegalArgumentException(); }
        var precision = MemoryEventTime.Precision.valueOf(string(node, "precision", "UNKNOWN"));
        if (start == null && end != null) { start = created; changes.add("仅有事件终点按旧规则补入记录时间"); }
        if (start != null && end != null && end.isBefore(start)) { var previous = start; start = end; end = previous; changes.add("倒置事件区间按旧规则交换端点"); }
        if (start == null && precision != MemoryEventTime.Precision.UNKNOWN) { precision = MemoryEventTime.Precision.UNKNOWN; changes.add("无事件端点改用未知精度"); }
        if (start != null && precision == MemoryEventTime.Precision.UNKNOWN) { precision = MemoryEventTime.Precision.APPROXIMATE; changes.add("未知精度端点标记为估算"); }
        return new MemoryEventTime(start, end, precision, string(node, "originalExpression", ""));
    }

    /**
     * 对精确 UTF-8 原文计算摘要，来源变化必须重新预览。
     * @param text 原文
     * @return SHA-256 十六进制摘要
     * @throws IllegalStateException 当运行环境不提供标准算法时
     */
    public static String digest(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException missing) { throw new IllegalStateException("旧记忆摘要算法不可用。"); }
    }
}
