package org.zexnocs.teanekoagent.adapter.personality;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekoagent.agent.prompt.AgentPromptException;
import org.zexnocs.teanekoagent.personality.*;
import org.zexnocs.teanekocore.database.base.DatabaseTaskConfig;
import org.zexnocs.teanekocore.database.base.interfaces.IDatabaseService;
import org.zexnocs.teanekocore.database.easydata.general.GeneralEasyDataObject;
import org.zexnocs.teanekocore.database.easydata.general.GeneralEasyDataRepository;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 在独立人格业务域保存绑定、补正及不可变备份，切换和版本写入同事务提交。
 * 不接触画像或 memory namespace，不沿用旧无保护 JSON 整列表写回。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service
public final class EasyDataPersonalityStore implements IPersonalityStore {
    private static final String DOMAIN = "agent.personality.v1";
    private final GeneralEasyDataRepository repository;
    private final PersonalityLockRepository locks;
    private final IDatabaseService database;
    private final ObjectMapper mapper;

    /**
     * 借用原数据库任务设施，构造期间无 I/O。
     * @param repository 通用行仓库
     * @param locks 本业务域专用行锁
     * @param database 提交后完成的事务入口
     * @param mapper 存储转换器
     */
    public EasyDataPersonalityStore(GeneralEasyDataRepository repository, PersonalityLockRepository locks, IDatabaseService database, ObjectMapper mapper) {
        this.repository = Objects.requireNonNull(repository, "人格仓库不能为空。"); this.locks = Objects.requireNonNull(locks, "人格事务锁端口不能为空。");
        this.database = Objects.requireNonNull(database, "人格数据库任务入口不能为空。"); this.mapper = Objects.requireNonNull(mapper, "人格 JSON 转换器不能为空。");
    }

    /**
     * 同一事务观察原补正、保存备份并切换当前 scope，新 scope 首次使用 scope- 前缀。
     * @param scope 当前作用域
     * @param base 可信基础设定
     * @param now 原始切换时刻
     * @return 真实提交后的组合
     */
    @Override public CompletionStage<Selection> resolve(AgentScope scope, BasePersonality base, Instant now) {
        return submit(() -> {
            var target = scopeTarget(scope); var row = locks.lock(target, "binding");
            var previous = row == null ? null : read(row, PersonalityBinding.class);
            if (previous != null && !previous.scope().equals(scope)) { throw new IllegalStateException("人格绑定存储定位不一致。"); }
            var fingerprint = base.fingerprint();
            if (previous != null && previous.baseFingerprint().equals(fingerprint)) {
                return new Selection(previous, requireState(previous.correctionKey()));
            }
            String backup = null; String key = "scope-" + scope.scopeId();
            if (previous != null) {
                var old = requireState(previous.correctionKey());
                backup = "backup-" + old.key() + "-" + now.toEpochMilli() + "-" + UUID.randomUUID();
                write(null, "state", physical(backup), new PersonalityState(backup, old.revision(), old.corrections(), true));
                key += "-" + UUID.randomUUID();
            }
            var next = new PersonalityBinding(scope, fingerprint, key, previous == null ? 1 : Math.addExact(previous.revision(), 1), backup);
            var state = ensureState(key);
            write(row, target, "binding", next); return new Selection(next, state);
        });
    }

    /**
     * 只读权威绑定，不初始化或刷新原引用。
     * @param scope 真实作用域
     * @return 当前绑定
     */
    @Override public CompletionStage<Optional<PersonalityBinding>> binding(AgentScope scope) {
        return submit(() -> {
            var row = repository.findByNamespaceAndTargetAndKey(DOMAIN, scopeTarget(scope), "binding");
            var result = row == null ? null : read(row, PersonalityBinding.class);
            if (result != null && !result.scope().equals(scope)) { throw new IllegalStateException("人格绑定存储定位不一致。"); }
            return Optional.ofNullable(result);
        });
    }

    /**
     * 在独立只读事务取得有界状态或备份，不遍历整个表。
     * @param key 可信管理定位
     * @return 独立不可变状态
     */
    @Override public CompletionStage<Optional<PersonalityState>> state(String key) {
        return submit(() -> {
            var row = repository.findByNamespaceAndTargetAndKey(DOMAIN, "state", physical(key));
            if (row == null) { return Optional.empty(); }
            var value = read(row, PersonalityState.class); checkKey(value, key); return Optional.of(value);
        });
    }

    /**
     * 锁定原绑定后更新共享状态，旧轮次、同请求不同内容及锁定字段均明确拒绝。
     * @param expected 固定原绑定
     * @param correction 已校验来源候选
     * @return 提交后是否新增
     */
    @Override public CompletionStage<Boolean> append(PersonalityBinding expected, PersonalityCorrection correction) {
        return submit(() -> {
            requireBinding(expected); var key = expected.correctionKey(); var row = locks.lock("state", physical(key));
            if (row == null) { throw changed(); }
            var current = read(row, PersonalityState.class); checkKey(current, key);
            var duplicate = current.corrections().stream().filter(item -> item.requestId().equals(correction.requestId())).findFirst();
            if (duplicate.isPresent()) {
                if (!duplicate.orElseThrow().equals(correction)) { throw new IllegalArgumentException("同一人格候选标识对应不同内容。"); }
                return false;
            }
            if (current.backup() || current.corrections().size() >= 64 || current.corrections().stream()
                    .anyMatch(item -> item.locked() && item.field().equals(correction.field()))) { throw new IllegalStateException("人格补正空间已满、只读或字段已锁定。"); }
            var items = new ArrayList<>(current.corrections()); items.add(correction);
            write(row, "state", physical(key), new PersonalityState(key, Math.addExact(current.revision(), 1), items, false)); return true;
        });
    }

    /**
     * 为后续 DEBUG 管理提供绑定事务，不在普通 cfg 或模型中暴露目标 key。
     * @param expected 原始预期绑定
     * @param key 已授权目标
     * @return 本次提交后的绑定
     */
    @Override public CompletionStage<PersonalityBinding> bind(PersonalityBinding expected, String key) {
        return submit(() -> {
            var row = locks.lock(scopeTarget(expected.scope()), "binding");
            if (row == null) { throw changed(); }
            var current = read(row, PersonalityBinding.class);
            if (current.correctionKey().equals(key) && current.baseFingerprint().equals(expected.baseFingerprint())
                    && (current.equals(expected) || current.revision() == expected.revision() + 1)) { return current; }
            if (!current.equals(expected)) { throw changed(); }
            var next = new PersonalityBinding(expected.scope(), expected.baseFingerprint(), key, Math.addExact(expected.revision(), 1), expected.backupKey());
            if (ensureState(key).backup()) { throw new IllegalArgumentException("备份不能作为可写补正空间绑定，请先克隆。"); }
            write(row, scopeTarget(expected.scope()), "binding", next); return next;
        });
    }

    /**
     * 锁内校验旧绑定仍有效，不允许已切换 scope 的迟到候选写回旧 key。
     * @param expected 原绑定
     * @throws AgentPromptException 当绑定已改变时
     */
    private void requireBinding(PersonalityBinding expected) {
        var row = locks.lock(scopeTarget(expected.scope()), "binding");
        if (row == null || !read(row, PersonalityBinding.class).equals(expected)) { throw changed(); }
    }
    /**
     * 锁定已存在的共享补正，缺失不是空记录。
     * @param key 逻辑 key
     * @return 当前状态
     */
    private PersonalityState requireState(String key) {
        var row = locks.lock("state", physical(key)); if (row == null) { throw new IllegalStateException("人格绑定引用了缺失的补正空间。"); }
        var state = read(row, PersonalityState.class); checkKey(state, key); return state;
    }
    /**
     * 只在初始化或可信绑定事务创建空集合，唯一键负责首次并发仲裁。
     * @param key 逻辑 key
     * @return 原状态或新空状态
     */
    private PersonalityState ensureState(String key) {
        var row = locks.lock("state", physical(key));
        if (row != null) { var value = read(row, PersonalityState.class); checkKey(value, key); return value; }
        var value = new PersonalityState(key, 0, List.of(), false); write(null, "state", physical(key), value); return value;
    }
    /**
     * 摘要键不能替代正文定位校验。
     * @param state 当前状态
     * @param key 预期逻辑 key
     */
    private void checkKey(PersonalityState state, String key) { if (!state.key().equals(key)) { throw new IllegalStateException("人格补正存储定位不一致。"); } }
    /**
     * 编码有界业务对象并按行版本写入，始终位于当前数据库事务。
     * @param row 原行，首次为空
     * @param target 物理目标
     * @param key 物理键
     * @param value 明确业务数据
     */
    private void write(@Nullable GeneralEasyDataObject row, String target, String key, Object value) {
        var json = mapper.writeValueAsString(Map.of("schemaVersion", 1, "value", value));
        if (row == null) { row = repository.create(DOMAIN, target, key, json); } else { row.setValue(json); }
        repository.saveAndFlush(row);
    }
    /**
     * 校验存储 schema 后解析明确类型，不反序列化任意 Java 类名。
     * @param row 原行
     * @param type 明确类型
     * @param <T> 不可变业务类型
     * @return 已校验对象
     */
    private <T> T read(GeneralEasyDataObject row, Class<T> type) {
        try {
            var json = mapper.readTree(row.getValue()); var version = json.path("schemaVersion");
            if (!version.isIntegralNumber() || !version.canConvertToInt() || version.intValue() != 1 || !json.hasNonNull("value")) {
                throw new IllegalStateException("人格存储版本或内容无效。");
            }
            return Objects.requireNonNull(mapper.treeToValue(json.path("value"), type), "人格存储内容不能为空。");
        } catch (RuntimeException invalid) { throw new IllegalStateException("人格存储数据损坏或版本不受支持。"); }
    }
    /**
     * 以完整 client/scope 定位独立绑定。
     * @param scope 真实作用域
     * @return 有界物理目标
     */
    private String scopeTarget(AgentScope scope) { return "scope:" + PersonalityKeys.digest(scope.clientId(), scope.scopeId()); }
    /**
     * 对逻辑 key 生成物理键，支持完整备份名而不超出数据库索引长度。
     * @param key 逻辑 key
     * @return 稳定摘要
     */
    private String physical(String key) { return PersonalityKeys.digest(key); }
    /**
     * 提供不含来源正文的固定换代失败。
     * @return 明确过期类别
     */
    private AgentPromptException changed() { return new AgentPromptException(AgentPromptException.Reason.CHANGED); }
    /**
     * 借用原 DatabaseTask 事务及重试机制，只有最终提交成功才公开结果。
     * @param action 当前事务工作
     * @param <T> 非空业务结果
     * @return 提交后完成的只读阶段
     */
    private <T> CompletionStage<T> submit(Supplier<T> action) {
        try {
            var result = new AtomicReference<T>(); var task = new DatabaseTaskConfig(database, "人格与补正事务");
            task.addTransactionTask(() -> { result.set(null); result.set(action.get()); });
            return task.pushAsync().thenApply(ignored -> Objects.requireNonNull(result.get(), "人格事务未产生结果。"));
        } catch (RuntimeException failure) { return CompletableFuture.failedStage(failure); }
    }
}
