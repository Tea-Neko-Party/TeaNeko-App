package org.zexnocs.teanekoagent.adapter.personality;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.adapter.memory.MemoryFactDataObject;
import org.zexnocs.teanekoagent.adapter.memory.MemoryFactRepository;
import org.zexnocs.teanekoagent.personality.ILegacyPersonalityStore;
import org.zexnocs.teanekoagent.personality.PersonalityCorrection;
import org.zexnocs.teanekoagent.personality.PersonalityKeys;
import org.zexnocs.teanekocore.database.easydata.general.GeneralEasyDataRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletionStage;

/**
 * 原子导入明确旧人格行，原文和映射保存在独立审计行；不改变或删除旧 llm_related。
 * 仅为可信迁移及后续 DEBUG 入口提供端口，不注册聊天命令或模型工具。
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service
public final class EasyDataLegacyPersonalityStore implements ILegacyPersonalityStore {
    private static final String DOMAIN = "agent.personality.v1";
    private static final Set<String> FIELDS = Set.of("id", "field", "content", "source", "tags", "confidence", "eventTime", "createdAt", "updatedAt", "expiresAt", "locked", "reason");
    private final EasyDataPersonalityStore store;
    private final MemoryFactRepository legacy;
    private final GeneralEasyDataRepository repository;
    private final ObjectMapper mapper;
    private final Clock clock;
    /**
     * Spring 使用 UTC 时钟，构造期间不读取旧数据。
     * @param store 人格事务及追加原语
     * @param legacy 原表仓库及行锁
     * @param repository 审计账本仓库
     * @param mapper JSON 转换器
     */
    @Autowired public EasyDataLegacyPersonalityStore(EasyDataPersonalityStore store, MemoryFactRepository legacy, GeneralEasyDataRepository repository, ObjectMapper mapper) {
        this(store, legacy, repository, mapper, Clock.systemUTC());
    }
    /**
     * 显式注入验证时钟，不改变实际数据布局。
     * @param store 人格事务原语
     * @param legacy 旧行仓库
     * @param repository 审计仓库
     * @param mapper JSON 转换器
     * @param clock 到期检查时钟
     */
    public EasyDataLegacyPersonalityStore(EasyDataPersonalityStore store, MemoryFactRepository legacy, GeneralEasyDataRepository repository, ObjectMapper mapper, Clock clock) {
        this.store = Objects.requireNonNull(store); this.legacy = Objects.requireNonNull(legacy); this.repository = Objects.requireNonNull(repository);
        this.mapper = Objects.requireNonNull(mapper); this.clock = Objects.requireNonNull(clock);
    }
    /**
     * 有界预览指定原行，原数据损坏不会伪装为空。
     * @param location 固定旧定位
     * @param fallbackTime 缺失时间的固定估算
     * @return 缺失或完整原文预览
     */
    @Override public CompletionStage<Optional<Preview>> preview(Location location, Instant fallbackTime) {
        Objects.requireNonNull(location); Objects.requireNonNull(fallbackTime);
        return store.submit(() -> {
            var row = legacy.findByNamespaceAndTargetAndKey("memory", location.target(), location.key());
            return row == null ? Optional.empty() : Optional.of(decode(row, location, fallbackTime));
        });
    }
    /**
     * 人格绑定、补正行、旧行和唯一审计键共同仲裁，失败无部分导入。
     * 同一来源行仅迁移一次；其他目标或变化后的源行需人工核对，不自动覆盖。
     * @param command 可信完整导入映射
     * @return 真实提交后的原回执或新增结果
     */
    @Override public CompletionStage<Result> importSnapshot(Import command) {
        Objects.requireNonNull(command);
        return store.submit(() -> {
            // 统一先锁绑定及补正，再锁旧行；与普通学习和人格切换保持相同锁顺序。
            store.appendInTransaction(command.expected(), List.of());
            var preview = command.preview(); var location = preview.location();
            var row = legacy.lockLegacyProfile(location.target(), location.key());
            if (row == null || !decode(row, location, preview.fallbackTime()).equals(preview)) { throw new IllegalStateException("旧人格源行已变化或缺失，请重新预览。"); }
            var auditKey = PersonalityKeys.digest("memory", location.target(), location.key());
            var original = repository.findByNamespaceAndTargetAndKey(DOMAIN, "legacy", auditKey);
            if (original != null) {
                final Audit audit;
                try { audit = Objects.requireNonNull(mapper.readValue(original.getValue(), Audit.class)); }
                catch (RuntimeException invalid) { throw new IllegalStateException("旧人格迁移审计损坏或版本不受支持。"); }
                if (audit.schemaVersion() != 1 || !audit.command().equals(command)) { throw new IllegalStateException("旧人格已导入，不能改变映射或目标重新应用。"); }
                return audit.result();
            }
            var candidates = new ArrayList<PersonalityCorrection>(); var skipped = new LinkedHashMap<String, String>();
            var nodes = mapper.readTree(preview.json());
            for (int i = 0; i < preview.entries().size(); i++) {
                var entry = preview.entries().get(i); var node = nodes.get(i);
                if (location.conflicts()) { skipped.put(entry.id(), "旧冲突仅保留审计"); continue; }
                if (!command.base().mutableFields().contains(entry.field())) { skipped.put(entry.id(), "目标人格不允许的字段"); continue; }
                var occurred = instant(node, "createdAt"); if (occurred == null) { occurred = preview.fallbackTime(); }
                var expires = instant(node, "expiresAt");
                double confidence = node.has("confidence") ? node.path("confidence").doubleValue() : 1.0;
                if (confidence < 0.2 || expires != null && !expires.isAfter(clock.instant())) { skipped.put(entry.id(), "置信度不足或已经到期"); continue; }
                var source = command.sources().get(entry.source());
                if (source == null) { throw new IllegalArgumentException("旧人格来源缺少明确的真实用户映射。"); }
                var id = UUID.nameUUIDFromBytes(PersonalityKeys.digest(auditKey, entry.id()).getBytes(StandardCharsets.UTF_8));
                candidates.add(new PersonalityCorrection(id, command.base().id(), command.base().version(), entry.field(), entry.text(), confidence,
                        source, occurred, expires, node.path("locked").asBoolean(false)));
            }
            store.appendInTransaction(command.expected(), candidates);
            var result = new Result(candidates.stream().map(PersonalityCorrection::requestId).toList(), skipped, auditKey);
            var audit = repository.create(DOMAIN, "legacy", auditKey, mapper.writeValueAsString(new Audit(1, command, result)));
            repository.saveAndFlush(audit); return result;
        });
    }
    /**
     * 拒绝损坏、未知格式、无界列表及重复 ID，原时间、标签和冲突原因完整保存在原文。
     * @param row 实际原行
     * @param location 预期物理定位
     * @param fallbackTime 缺失时间估算
     * @return 不产生随机默认值的预览
     */
    private Preview decode(MemoryFactDataObject row, Location location, Instant fallbackTime) {
        try {
            if (!row.getNamespace().equals("memory") || !row.getTarget().equals(location.target()) || !row.getKey().equals(location.key())
                    || row.getValue() == null || row.getValue().length() > 262144) { throw new IllegalArgumentException(); }
            var nodes = mapper.readTree(row.getValue()); if (!nodes.isArray() || nodes.size() > 128) { throw new IllegalArgumentException(); }
            var entries = new ArrayList<Entry>(); var ids = new HashSet<String>();
            for (int i = 0; i < nodes.size(); i++) {
                var node = nodes.get(i); if (!node.isObject() || node.properties().stream().anyMatch(field -> !FIELDS.contains(field.getKey()))) { throw new IllegalArgumentException(); }
                var id = string(node, "id", "row-index:" + i);
                if (id.isBlank() || id.length() > 256 || !ids.add(id)) { throw new IllegalArgumentException(); }
                var confidence = node.get("confidence"); var locked = node.get("locked");
                if (confidence != null && (!confidence.isNumber() || !Double.isFinite(confidence.doubleValue()) || confidence.doubleValue() < 0 || confidence.doubleValue() > 1)
                        || locked != null && !locked.isBoolean()) { throw new IllegalArgumentException(); }
                instant(node, "createdAt"); instant(node, "updatedAt"); instant(node, "expiresAt");
                entries.add(new Entry(id, string(node, "field", ""), string(node, "content", ""), string(node, "source", ""), string(node, "reason", "")));
            }
            return new Preview(location, row.getVersion(), PersonalityKeys.digest(row.getValue()), row.getValue(), fallbackTime, entries);
        } catch (RuntimeException invalid) { throw new IllegalArgumentException("旧人格 JSON 结构、定位、时间或容量无效，原数据未修改。"); }
    }
    /**
     * 只接受真实字符串，缺失才使用旧格式的明确默认值。
     * @param node 原对象
     * @param key 字段
     * @param fallback 缺失默认值
     * @return 原字符串
     */
    private String string(JsonNode node, String key, String fallback) {
        if (!node.has(key)) { return fallback; }
        if (!node.path(key).isString()) { throw new IllegalArgumentException("旧人格字符串字段无效。"); }
        return node.path(key).stringValue();
    }
    /**
     * 保留原绝对时间，不将非法值替换成当前时间。
     * @param node 原对象
     * @param key 原时间字段
     * @return 原时间，缺失或显式 null 时为空
     */
    private @Nullable Instant instant(JsonNode node, String key) {
        if (!node.hasNonNull(key)) { return null; }
        return Instant.parse(string(node, key, ""));
    }
    /**
     * 与补正同事务保存的完整来源及回执，不参与正常 Prompt。
     * @param schemaVersion 存储版本
     * @param command 原文、映射及真实导入来源
     * @param result 原始提交回执
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record Audit(int schemaVersion, Import command, Result result) {}
}
