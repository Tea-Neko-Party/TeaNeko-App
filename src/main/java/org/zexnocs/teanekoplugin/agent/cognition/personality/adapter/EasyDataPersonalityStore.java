package org.zexnocs.teanekoplugin.agent.cognition.personality.adapter;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.database.easydata.EasyDataMaintenancePage;
import org.zexnocs.teanekoagent.database.easydata.TeaNekoAgentEasyData;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptException;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataLock;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataRow;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataTransactionDto;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.IMemoryMaintenance;
import org.zexnocs.teanekoplugin.agent.cognition.personality.BasePersonality;
import org.zexnocs.teanekoplugin.agent.cognition.personality.IPersonalityManagementStore;
import org.zexnocs.teanekoplugin.agent.cognition.personality.IPersonalityStore;
import org.zexnocs.teanekoplugin.agent.cognition.personality.PersonalityBinding;
import org.zexnocs.teanekoplugin.agent.cognition.personality.PersonalityCorrection;
import org.zexnocs.teanekoplugin.agent.cognition.personality.PersonalityKeys;
import org.zexnocs.teanekoplugin.agent.cognition.personality.PersonalityManagementCommand;
import org.zexnocs.teanekoplugin.agent.cognition.personality.PersonalityManagementResult;
import org.zexnocs.teanekoplugin.agent.cognition.personality.PersonalityState;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 在独立人格业务域保存绑定、补正及不可变备份，切换和版本写入同事务提交。
 * 不接触画像或 memory namespace，不沿用旧无保护 JSON 整列表写回。
 * <br>2026/11/03：允许表达观察在原事务中按固定人格和管理代次渐进采用。
 * <br>2026/11/03：释放到期补正占用的容量，保持备份不可变与管理代次稳定。
 * <br>2026/11/03：按原编号查询同管理主体及目标的持久化回执。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/06：增加空间分页和独立管理事务，保留版本、回执、备份及撤权回滚。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@Service
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.cognition")
public final class EasyDataPersonalityStore implements IPersonalityStore, IPersonalityManagementStore, IMemoryMaintenance {
    /**
     * 2026/11/03：按原编号查询同管理主体结果，不重放修改。
     * @param operationId 原操作
     * @param actor 原主体及目标
     * @param guard 当前授权
     * @return 实际原回执
     */
    @Override
    public CompletionStage<PersonalityManagementResult> operation(java.util.UUID operationId,
            org.zexnocs.teanekoagent.runtime.AgentActorContext actor, Runnable guard) {
        return submit(() -> {
            guard.run();
            var row = dto("management-operation").find(operationId.toString(), EasyDataLock.READ);
            if (row == null) {
                throw new java.util.NoSuchElementException("人格管理回执不存在。");
            }
            var receipt = read(row, ManagementReceipt.class);
            if (!receipt.command().actor().equals(actor)) {
                throw new SecurityException("人格操作不属于当前管理主体或目标。");
            }
            guard.run();
            return receipt.result();
        });
    }
    private static final String DOMAIN = "agent.personality.v1";
    private final TeaNekoAgentEasyData data;
    private final ObjectMapper mapper;

    /**
     * 2026/11/06：沿状态目标的持久键有界分页，不依赖当前聊天绑定。
     * @param after 原物理游标
     * @param limit 一至五十项
     * @return 实际补正空间及备份目录
     */
    @Override
    public CompletionStage<org.zexnocs.teanekoagent.database.easydata.StoredPage<PersonalityState>> page(String after, int limit) {
        if (after.length() > 128 || limit < 1 || limit > 50) {
            throw new IllegalArgumentException("人格目录分页无效。");
        }
        return submit(() -> {
            var rows = dto("state").scan(after, null, limit + 1, EasyDataLock.NONE);
            var items = rows.stream().limit(limit).map(row -> read(row, PersonalityState.class)).toList();
            return new org.zexnocs.teanekoagent.database.easydata.StoredPage<>(items,
                    rows.size() > limit ? rows.get(limit - 1).getKey() : "");
        });
    }

    /**
     * 借用原数据库任务设施，构造期间无 I/O。
     * @param data Agent 专用数据入口
     * @param mapper 存储转换器
     */
    public EasyDataPersonalityStore(TeaNekoAgentEasyData data, ObjectMapper mapper) {
        this.data = Objects.requireNonNull(data, "人格 EasyData 不能为空。").inNamespace(DOMAIN);
        this.mapper = Objects.requireNonNull(mapper, "人格 JSON 转换器不能为空。");
    }

    /**
     * 同一事务观察原补正、保存备份并切换当前 scope，新 scope 首次使用 scope- 前缀。
     * @param scope 当前作用域
     * @param base 可信基础设定
     * @param now 原始切换时刻
     * @return 真实提交后的组合
     */
    @Override public CompletionStage<Selection> resolve(AgentScope scope, BasePersonality base, Instant now) {
        return submit(() -> resolveInTransaction(scope, base, now));
    }

    /**
     * 复用初始化和切换逻辑，在管理事务提交前再次检查授权。
     * @param scope 原作用域
     * @param base 当前基础人格
     * @param now 固定时刻
     * @param guard 当前授权
     * @return 原子选择
     */
    @Override public CompletionStage<Selection> inspect(AgentScope scope, BasePersonality base, Instant now, Runnable guard) {
        return submit(() -> { guard.run(); var selection = resolveInTransaction(scope, base, now); guard.run(); return selection; });
    }

    /**
     * 在当前事务内先保存旧状态备份，再仅切换当前 scope 的引用。
     * @param scope 当前作用域
     * @param base 基础人格
     * @param now 原时刻
     * @return 当前绑定和状态
     */
    private Selection resolveInTransaction(AgentScope scope, BasePersonality base, Instant now) {
        var target = scopeTarget(scope); var row = lock(target, "binding");
        var previous = row == null ? null : read(row, PersonalityBinding.class);
        if (previous != null && !previous.scope().equals(scope)) { throw new IllegalStateException("人格绑定存储定位不一致。"); }
        var fingerprint = base.fingerprint();
        if (previous != null && previous.baseFingerprint().equals(fingerprint)) {
            return new Selection(previous, requireState(previous.correctionKey()));
        }
        String backup = null; String key = "scope-" + PersonalityKeys.digest(scope.clientId(), scope.scopeId());
        if (previous != null) {
            var old = requireState(previous.correctionKey());
            backup = archive(old, now, UUID.randomUUID());
            key += "-" + UUID.randomUUID();
        }
        var next = new PersonalityBinding(scope, fingerprint, key, previous == null ? 1 : Math.addExact(previous.revision(), 1), backup);
        var state = ensureState(key);
        write(row, target, "binding", next); return new Selection(next, state);
    }

    /**
     * 只读权威绑定，不初始化或刷新原引用。
     * @param scope 真实作用域
     * @return 当前绑定
     */
    @Override public CompletionStage<Optional<PersonalityBinding>> binding(AgentScope scope) {
        return submit(() -> {
            var row = dto(scopeTarget(scope)).find("binding", EasyDataLock.NONE);
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
            var row = dto("state").find(physical(key), EasyDataLock.NONE);
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
        return submit(() -> appendInTransaction(expected, correction, -1, () -> {}));
    }
    /**
     * 学习候选使用原管理代次，锁内仲裁后再次复核准入，拒绝迟到回灌。
     * @param expected 原绑定
     * @param correction 来源候选
     * @param managementRevision 原管理代次
     * @param guard 当前配置和执行控制
     * @return 实际新增状态
     */
    @Override public CompletionStage<Boolean> append(PersonalityBinding expected, PersonalityCorrection correction, long managementRevision, Runnable guard) {
        if (managementRevision < 0) { throw new IllegalArgumentException("原管理代次不能为负。"); }
        return submit(() -> appendInTransaction(expected, correction, managementRevision, guard));
    }
    /**
     * 同一事务仲裁候选来源、稳定 ID、容量及锁定字段，提交前不公开成功。
     * @param expected 原绑定
     * @param correction 已校验候选
     * @param managementRevision 管理代次，可信直接追加用 -1
     * @param guard 提交前准入复核
     * @return 是否实际新增
     */
    private boolean appendInTransaction(PersonalityBinding expected, PersonalityCorrection correction, long managementRevision, Runnable guard) {
        guard.run();
        requireBinding(expected); var key = expected.correctionKey(); var row = lock("state", physical(key));
        if (row == null) { throw changed(); }
        var current = read(row, PersonalityState.class); checkKey(current, key);
        if (managementRevision >= 0 && current.managementRevision() != managementRevision) { throw changed(); }
        var duplicate = current.corrections().stream().filter(item -> item.requestId().equals(correction.requestId())).findFirst();
        if (duplicate.isPresent()) {
            if (!duplicate.orElseThrow().equals(correction)) { throw new IllegalArgumentException("同一人格候选标识对应不同内容。"); }
            guard.run(); return false;
        }
        if (current.backup() || current.corrections().size() >= 64 || current.corrections().stream()
                .anyMatch(item -> item.locked() && item.field().equals(correction.field()))) {
            throw new IllegalStateException("人格补正空间已满、只读或字段已锁定。");
        }
        var items = new ArrayList<>(current.corrections()); items.add(correction);
        write(row, "state", physical(key), new PersonalityState(key, Math.addExact(current.revision(), 1), items, false, current.managementRevision()));
        guard.run(); return true;
    }
    /**
     * 2026/11/03：观察与长期水位同事务提交，目标变化明确拒绝，锁定或容量不足只保留观察。
     * @param scope 原作用域
     * @param basis 生成时固定人格
     * @param correction 已核对观察派生的表达补正
     * @param guard 当前学习和执行守卫
     * @return 新增或同候选已采用时为真，受管理保护时为假
     */
    public boolean adoptObservedInTransaction(AgentScope scope, org.zexnocs.teanekoplugin.agent.cognition.personality.AgentPersonalitySnapshot basis,
            PersonalityCorrection correction, Runnable guard) {
        verifyObservationTargetInTransaction(scope, basis, guard);
        guard.run(); var bindingRow = lock(scopeTarget(scope), "binding");
        if (bindingRow == null) { throw changed(); }
        var binding = read(bindingRow, PersonalityBinding.class); var state = requireState(binding.correctionKey());
        if (!binding.correctionKey().equals(basis.correctionKey()) || !binding.identity(true, state.managementRevision()).equals(basis.identity())
                && !binding.identity(false, state.managementRevision()).equals(basis.identity())) { throw changed(); }
        if (state.backup() || state.corrections().stream().anyMatch(item -> item.locked() && item.field().equals(correction.field()))
                || state.corrections().size() >= 64) { return false; }
        appendInTransaction(binding, correction, state.managementRevision(), guard); return true;
    }
    /**
     * 2026/11/03：即使样本不足以采用，也不能把迟到观察写入已换代人格。
     * @param scope 固定作用域
     * @param basis 生成时人格
     * @param guard 当前学习守卫
     */
    public void verifyObservationTargetInTransaction(AgentScope scope, org.zexnocs.teanekoplugin.agent.cognition.personality.AgentPersonalitySnapshot basis, Runnable guard) {
        guard.run(); var row = lock(scopeTarget(scope), "binding"); if (row == null) { throw changed(); }
        var binding = read(row, PersonalityBinding.class); var state = requireState(binding.correctionKey());
        if (!binding.scope().equals(scope) || !binding.correctionKey().equals(basis.correctionKey())
                || !binding.identity(true, state.managementRevision()).equals(basis.identity())
                && !binding.identity(false, state.managementRevision()).equals(basis.identity())) { throw changed(); }
        guard.run();
    }
    /**
     * 2026/11/03：到期补正从可写集合物理移除，不修改不可变备份，不回收有效或无期限补正。
     * @param now 当前时刻
     * @return 实际精简的状态数量
     */
    @Override public CompletionStage<Integer> maintain(Instant now) {
        return EasyDataMaintenancePage.run(data, "释放到期人格补正容量", rows -> {
            int changed = 0;
            for (var scanned : rows) {
                if (!scanned.getTarget().equals("state")) { continue; }
                var row = dto("state").find(scanned.getKey(), EasyDataLock.WRITE); if (row == null) { continue; }
                var value = read(row, PersonalityState.class); if (value.backup()) { continue; }
                var active = value.corrections().stream().filter(item -> item.activeAt(now)).toList();
                if (active.size() == value.corrections().size()) { continue; }
                write(row, "state", row.getKey(), new PersonalityState(value.key(), Math.addExact(value.revision(), 1), active, false, value.managementRevision())); changed++;
            }
            return changed;
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
            var row = lock(scopeTarget(expected.scope()), "binding");
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
     * 读取固定 key 的最近备份索引，不扫描补正表。
     * @param key 源 key
     * @return 从新到旧的备份引用
     */
    @Override public CompletionStage<List<String>> backups(String key) {
        return submit(() -> {
            var row = dto("backups").find(physical(key), EasyDataLock.NONE);
            return row == null ? List.of() : backupIndex(row, key).keys();
        });
    }

    /**
     * 管理动作与原命令回执同事务保存，数据库重试仍固定原版本及请求。
     * @param command 可信管理命令
     * @param base 当前基础人格
     * @param now 固定时刻
     * @param guard 当前权限和取消复核
     * @return 真实提交后回执
     */
    @Override public CompletionStage<PersonalityManagementResult> apply(PersonalityManagementCommand command, BasePersonality base, Instant now, Runnable guard) {
        return submit(() -> {
            guard.run(); var operation = command.operationId().toString(); var receipts = dto("management-operation");
            var previous = receipts.find(operation, EasyDataLock.NONE);
            if (previous != null) { var result = replay(previous, command); guard.run(); return result; }
            var bindingRow = lock(scopeTarget(command.actor().scope()), "binding");
            var binding = bindingRow == null ? null : read(bindingRow, PersonalityBinding.class);
            PersonalityManagementResult result;
            if (binding == null || !binding.scope().equals(command.actor().scope()) || binding.revision() != command.bindingRevision()
                    || !binding.baseFingerprint().equals(base.fingerprint())) {
                result = new PersonalityManagementResult(PersonalityManagementResult.Status.CONFLICT, command.key(), -1,
                        binding == null ? 0 : binding.revision(), null);
            } else {
                result = manage(command, base, binding, Objects.requireNonNull(bindingRow), now);
            }
            write(null, "management-operation", operation, new ManagementReceipt(command, result));
            guard.run(); return result;
        });
    }

    /**
     * 2026/11/06：独立空间管理复用版本、备份与操作账本，不伪造绑定或改变聊天引用。
     * @param command 固定管理请求
     * @param baseProvider 仅首次执行解析当前可信基础人格，已提交重放不重新解析
     * @param now 操作时刻
     * @param guard 当前授权
     * @return 事务提交后的原回执
     */
    @Override
    public CompletionStage<PersonalityManagementResult> applyUnbound(PersonalityManagementCommand command,
            java.util.function.Supplier<@org.jspecify.annotations.Nullable BasePersonality> baseProvider,
            Instant now, Runnable guard) {
        if (command.actor().origin() != org.zexnocs.teanekoagent.runtime.AgentActorContext.Origin.MANAGEMENT
                || command.action() == PersonalityManagementCommand.Action.BIND || command.bindingRevision() != 0) {
            throw new SecurityException("独立人格管理只接受管理员的空间内容操作，不能修改聊天绑定。");
        }
        return submit(() -> {
            guard.run();
            var previous = dto("management-operation").find(command.operationId().toString(), EasyDataLock.NONE);
            if (previous != null) {
                var result = replay(previous, command);
                guard.run();
                return result;
            }
            var base = baseProvider.get();
            if (java.util.Set.of(PersonalityManagementCommand.Action.ADD, PersonalityManagementCommand.Action.EDIT,
                    PersonalityManagementCommand.Action.LOCK).contains(command.action())
                    && (base == null || !base.fingerprint().equals(command.baseFingerprint()))) {
                throw new IllegalArgumentException("独立内容操作需要匹配已确认的基础人格。");
            }
            var result = manage(command, base, null, null, now);
            if (base != null && !base.equals(baseProvider.get())) {
                throw new IllegalStateException("基础人格配置已变化，请重新读取。");
            }
            write(null, "management-operation", command.operationId().toString(), new ManagementReceipt(command, result));
            guard.run();
            return result;
        });
    }

    /**
     * 固定绑定后仲裁源/目标集合，备份及修改仅在当前事务发布。
     * @param command 原命令
     * @param base 当前基础人格
     * @param binding 最新绑定
     * @param bindingRow 绑定行
     * @param now 固定时刻
     * @return 当前动作结果
     */
    private PersonalityManagementResult manage(PersonalityManagementCommand command, @Nullable BasePersonality base, @Nullable PersonalityBinding binding,
            @Nullable EasyDataRow bindingRow, Instant now) {
        if (command.action() == PersonalityManagementCommand.Action.BIND) {
            Objects.requireNonNull(binding, "绑定动作需要真实聊天引用。");
            for (var key : java.util.stream.Stream.of(command.key(), binding.correctionKey()).distinct().sorted(Comparator.comparing(this::physical)).toList()) {
                lock("state", physical(key));
            }
        }
        var row = lock("state", physical(command.key()));
        var state = row == null ? null : read(row, PersonalityState.class);
        if (state != null) { checkKey(state, command.key()); }
        if (state == null && command.action() != PersonalityManagementCommand.Action.BIND) {
            return managementResult(PersonalityManagementResult.Status.NOT_FOUND, command.key(), null, binding, null);
        }
        if ((state == null ? -1 : state.revision()) != command.stateRevision()) {
            return managementResult(PersonalityManagementResult.Status.CONFLICT, command.key(), state, binding, null);
        }
        if (command.action() == PersonalityManagementCommand.Action.BIND) {
            Objects.requireNonNull(binding, "绑定动作需要真实聊天引用。");
            if (state != null && state.backup()) { throw new IllegalArgumentException("备份必须先克隆才能绑定。"); }
            if (binding.correctionKey().equals(command.key())) {
                if (state == null) { throw new IllegalStateException("当前人格绑定引用的补正空间缺失。"); }
                return managementResult(PersonalityManagementResult.Status.UNCHANGED, command.key(), state, binding, null);
            }
            var backup = archive(requireState(binding.correctionKey()), now, command.operationId());
            if (state == null) { state = ensureState(command.key()); }
            var next = new PersonalityBinding(binding.scope(), binding.baseFingerprint(), command.key(), Math.addExact(binding.revision(), 1), backup);
            write(bindingRow, scopeTarget(binding.scope()), "binding", next);
            return managementResult(PersonalityManagementResult.Status.APPLIED, command.key(), state, next, backup);
        }
        if (command.action() == PersonalityManagementCommand.Action.BACKUP) {
            return managementResult(PersonalityManagementResult.Status.APPLIED, state.key(), state, binding, archive(state, now, command.operationId()));
        }
        if (command.action() == PersonalityManagementCommand.Action.CLONE) {
            var key = "clone-" + physical(state.key()).substring(0, 16) + "-" + command.operationId();
            var clone = new PersonalityState(key, 0, state.corrections(), false);
            if (dto("state").find(physical(key), EasyDataLock.NONE) != null) { throw new IllegalStateException("补正克隆目标已存在。"); }
            write(null, "state", physical(key), clone);
            return managementResult(PersonalityManagementResult.Status.APPLIED, key, clone, binding, null);
        }
        if (state.backup() || binding != null && !binding.correctionKey().equals(state.key())) { throw new IllegalArgumentException("只能编辑已授权的可写补正空间。"); }
        var items = new ArrayList<>(state.corrections());
        if (command.action() == PersonalityManagementCommand.Action.ADD) {
            if (!Objects.requireNonNull(base).mutableFields().contains(command.field()) || items.size() >= 64) { throw new IllegalArgumentException("补正字段不允许修改或集合已满。"); }
            items.add(new PersonalityCorrection(command.operationId(), base.id(), base.version(), Objects.requireNonNull(command.field()),
                    Objects.requireNonNull(command.text()), 1, command.actor(), now, null, false));
        } else {
            var original = items.stream().filter(item -> item.requestId().equals(command.correctionId())).findFirst().orElse(null);
            if (original == null) { return managementResult(PersonalityManagementResult.Status.NOT_FOUND, state.key(), state, binding, null); }
            if (command.action() == PersonalityManagementCommand.Action.REMOVE) { items.remove(original); }
            else {
                if (!Objects.requireNonNull(base).id().equals(original.baseId()) || base.version() != original.baseVersion() || !base.mutableFields().contains(original.field())) {
                    throw new IllegalArgumentException("该补正不兼容当前基础人格，只能撤销或克隆保存。");
                }
                var replacement = new PersonalityCorrection(original.requestId(), original.baseId(), original.baseVersion(), original.field(),
                        command.text() == null ? original.text() : command.text(), original.confidence(), original.source(), original.occurredAt(), original.expiresAt(),
                        command.locked() == null ? original.locked() : command.locked());
                if (original.equals(replacement)) { return managementResult(PersonalityManagementResult.Status.UNCHANGED, state.key(), state, binding, null); }
                items.set(items.indexOf(original), replacement);
            }
        }
        var backup = archive(state, now, command.operationId());
        var next = new PersonalityState(state.key(), Math.addExact(state.revision(), 1), items, false, Math.addExact(state.managementRevision(), 1));
        write(row, "state", physical(state.key()), next);
        return managementResult(PersonalityManagementResult.Status.APPLIED, state.key(), next, binding, backup);
    }

    /**
     * 对完整原命令做幂等比较，改变身份或参数不能重复取得管理副作用。
     * @param row 持久化回执
     * @param command 当前命令
     * @return 原结果或冲突
     */
    private PersonalityManagementResult replay(EasyDataRow row, PersonalityManagementCommand command) {
        var previous = read(row, ManagementReceipt.class);
        return previous.command().equals(command) ? previous.result()
                : new PersonalityManagementResult(PersonalityManagementResult.Status.CONFLICT, command.key(), -1, command.bindingRevision(), null);
    }

    /**
     * 保存不可变原证据并维护有界索引，较早备份仍可按完整 key 读取。
     * @param state 原状态
     * @param now 固定时刻
     * @param operation 原操作
     * @return 新备份 key
     */
    private String archive(PersonalityState state, Instant now, UUID operation) {
        var key = "backup-" + physical(state.key()).substring(0, 16) + "-" + now.toEpochMilli() + "-" + operation;
        write(null, "state", physical(key), new PersonalityState(key, state.revision(), state.corrections(), true, state.managementRevision()));
        var index = lock("backups", physical(state.key()));
        var keys = new ArrayList<>(index == null ? List.of() : backupIndex(index, state.key()).keys());
        keys.addFirst(key); if (keys.size() > 64) { keys.removeLast(); }
        write(index, "backups", physical(state.key()), new BackupIndex(state.key(), keys)); return key;
    }

    /**
     * 校验备份索引的原 key，摘要冲突不能变成跨空间读取。
     * @param row 索引行
     * @param key 预期源
     * @return 已校验索引
     */
    private BackupIndex backupIndex(EasyDataRow row, String key) {
        var index = read(row, BackupIndex.class);
        if (!index.key().equals(key)) { throw new IllegalStateException("人格备份索引定位不一致。"); }
        return index;
    }

    /**
     * 生成不复制整份补正正文的管理回执。
     * @param status 状态
     * @param key 结果 key
     * @param state 原状态，不存在时为空
     * @param binding 当前绑定
     * @param backup 本操作备份
     * @return 原提交观察
     */
    private PersonalityManagementResult managementResult(PersonalityManagementResult.Status status, String key, @Nullable PersonalityState state,
            @Nullable PersonalityBinding binding, @Nullable String backup) {
        return new PersonalityManagementResult(status, key, state == null ? -1 : state.revision(), binding == null ? 0 : binding.revision(), backup);
    }

    /**
     * 显式管理账本，不保存权限布尔值或运行控制。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     * @param command 固定命令
     * @param result 原结果
     *
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.5.0-l
     * @version 4.9.0-l
     */
    private record ManagementReceipt(PersonalityManagementCommand command, PersonalityManagementResult result) {}

    /**
     * 每个源 key 的有界备份列表，正文保存在独立不可变状态中。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     * @param key 原 key
     * @param keys 从新到旧的备份引用
     *
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.5.0-l
     * @version 4.9.0-l
     */
    private record BackupIndex(String key, List<String> keys) {
        /**
         * 冻结备份列表并拒绝损坏索引。
         * @param key 原 key
         * @param keys 备份引用
         * @throws IllegalArgumentException 当索引越界或引用重复时
         */
        private BackupIndex {
            keys = List.copyOf(keys);
            if (key == null || key.isBlank() || keys.size() > 64 || keys.stream().distinct().count() != keys.size()
                    || keys.stream().anyMatch(item -> !item.startsWith("backup-"))) { throw new IllegalArgumentException("人格备份索引无效。"); }
        }
    }

    /**
     * 取得人格域的事务目标，仓库、版本及缓存由 EasyData 负责。
     * @param target 固定目标
     * @return 无缓存事务视图
     */
    private IEasyDataTransactionDto dto(String target) { return data.get(target).atomic(); }
    /**
     * 使用专用 DTO 锁定最新行，不泄漏持久化实体。
     * @param target 目标
     * @param key 固定键
     * @return 当前行或空
     */
    private @Nullable EasyDataRow lock(String target, String key) { return dto(target).find(key, EasyDataLock.WRITE); }
    /**
     * 锁内校验旧绑定仍有效，不允许已切换 scope 的迟到候选写回旧 key。
     * @param expected 原绑定
     * @throws AgentPromptException 当绑定已改变时
     */
    private void requireBinding(PersonalityBinding expected) {
        var row = lock(scopeTarget(expected.scope()), "binding");
        if (row == null || !read(row, PersonalityBinding.class).equals(expected)) { throw changed(); }
    }
    /**
     * 锁定已存在的共享补正，缺失不是空记录。
     * @param key 逻辑 key
     * @return 当前状态
     */
    private PersonalityState requireState(String key) {
        var row = lock("state", physical(key)); if (row == null) { throw new IllegalStateException("人格绑定引用了缺失的补正空间。"); }
        var state = read(row, PersonalityState.class); checkKey(state, key); return state;
    }
    /**
     * 只在初始化或可信绑定事务创建空集合，唯一键负责首次并发仲裁。
     * @param key 逻辑 key
     * @return 原状态或新空状态
     */
    private PersonalityState ensureState(String key) {
        var row = lock("state", physical(key));
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
    private void write(@Nullable EasyDataRow row, String target, String key, Object value) {
        var json = mapper.writeValueAsString(Map.of("schemaVersion", 1, "value", value));
        if (row == null) { row = dto(target).create(key, json); } else { row.setValue(json); }
        dto(target).save(row);
    }
    /**
     * 校验存储 schema 后解析明确类型，不反序列化任意 Java 类名。
     * @param row 原行
     * @param type 明确类型
     * @param <T> 不可变业务类型
     * @return 已校验对象
     */
    private <T> T read(EasyDataRow row, Class<T> type) {
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
    <T> CompletionStage<T> submit(Supplier<T> action) {
        try {
            return data.transaction("人格与补正事务", action);
        } catch (RuntimeException failure) { return CompletableFuture.failedStage(failure); }
    }
}
