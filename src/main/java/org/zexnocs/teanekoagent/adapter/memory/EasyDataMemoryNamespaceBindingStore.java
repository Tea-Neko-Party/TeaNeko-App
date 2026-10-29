package org.zexnocs.teanekoagent.adapter.memory;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekoagent.memory.namespace.*;
import org.zexnocs.teanekocore.database.easydata.general.GeneralEasyDataObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

import static org.zexnocs.teanekoagent.memory.namespace.MemoryNamespaceBindingOutcome.Status.*;

/**
 * 在既有空间存储事务中同时提交创建、配额、scope 引用与操作回执，不串联两次异步写入。
 * 同域适配器共享事务辅助方法，业务服务只依赖绑定端口，不接触仓库或 ConfigData 缓存。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service
public final class EasyDataMemoryNamespaceBindingStore implements IMemoryNamespaceBindingStore {
    private final EasyDataMemoryNamespaceStore namespaces;

    /**
     * 借用同存储域的事务设施，构造不读取数据或修改绑定。
     * @param namespaces 空间元数据事务适配器
     * @throws NullPointerException 当事务适配器为空时
     */
    public EasyDataMemoryNamespaceBindingStore(EasyDataMemoryNamespaceStore namespaces) {
        this.namespaces = Objects.requireNonNull(namespaces, "记忆空间事务适配器不能为空。");
    }

    /**
     * 读取权威引用，不存在时只返回隐式默认值，不写入配置或数据库。
     * @param scope 真实作用域
     * @return 当前事务观察到的绑定
     * @throws NullPointerException 当作用域为空时
     */
    @Override public CompletionStage<MemoryNamespaceBinding> readBinding(AgentScope scope) {
        Objects.requireNonNull(scope, "绑定作用域不能为空。");
        return namespaces.submit("读取记忆空间绑定", () -> currentBinding(scope));
    }

    /**
     * 同一事务取得引用及数据代次，不使用先前操作回执代替当前引用。
     * @param scope 真实作用域
     * @return 默认空间尚未登记时为空；显式引用目标缺失时失败
     * @throws NullPointerException 当作用域为空时
     */
    @Override public CompletionStage<Optional<MemoryNamespaceReference>> readReference(AgentScope scope) {
        Objects.requireNonNull(scope, "绑定作用域不能为空。");
        return namespaces.submit("读取记忆空间运行引用", () -> {
            var binding = currentBinding(scope); var row = namespaces.namespaceRow(binding.namespaceId());
            if (row == null) {
                if (binding.revision() == 0) { return Optional.empty(); }
                throw new IllegalStateException("显式记忆空间绑定引用了缺失的空间。");
            }
            return Optional.of(reference(binding, namespaces.namespace(row, binding.namespaceId())));
        });
    }

    /**
     * 对原始命令执行一次完整事务；相同 scope/requestId 的已保存结果不重新执行。
     * @param request 原始可信命令
     * @param maxCreated 本次固定创建配额
     * @param now 原始操作时刻
     * @return 持久化回执，仍须由上层复核当前引用和协调内存会话
     * @throws NullPointerException 当命令或时刻为空时
     * @throws IllegalArgumentException 当配额无效时
     */
    @Override public CompletionStage<MemoryNamespaceBindingOutcome> apply(MemoryNamespaceBindingRequest request, int maxCreated, Instant now) {
        Objects.requireNonNull(request, "绑定命令不能为空。"); Objects.requireNonNull(now, "绑定操作时刻不能为空。");
        if (maxCreated < 1) { throw new IllegalArgumentException("个人记忆空间配额必须为正整数。"); }
        return namespaces.submit("变更记忆空间绑定", () -> applyInTransaction(request, maxCreated, now));
    }

    /**
     * 先查询原操作再仲裁当前版本，创建失败与绑定失败不得留下半次成功。
     * @param request 固定原始命令
     * @param maxCreated 本次配额
     * @param now 原始操作时刻
     * @return 本次事务候选结果，仅在最终提交后交付
     */
    private MemoryNamespaceBindingOutcome applyInTransaction(MemoryNamespaceBindingRequest request, int maxCreated, Instant now) {
        var scope = request.actor().scope(); var digest = scopeKey(scope);
        var operationRow = namespaces.row("binding-op:" + digest, request.requestId().toString());
        var currentRow = namespaces.row("binding:" + digest, "current");
        var current = binding(currentRow, scope);
        if (operationRow != null) {
            var operation = namespaces.read(operationRow, BindingOperation.class);
            if (!operation.request().actor().scope().equals(scope) || !operation.request().requestId().equals(request.requestId())) {
                throw new IllegalStateException("绑定操作回执与存储定位不一致。");
            }
            return operation.request().equals(request) ? operation.outcome() : new MemoryNamespaceBindingOutcome(CONFLICT, current, null);
        }
        var outcome = request.expectedRevision() != current.revision()
                ? new MemoryNamespaceBindingOutcome(CONFLICT, current, null)
                : change(request, current, currentRow, maxCreated, now);
        namespaces.write(null, "binding-op:" + digest, request.requestId().toString(), new BindingOperation(1, request, outcome, now));
        return outcome;
    }

    /**
     * 同一事务登记目标、仲裁配额并更新引用；业务拒绝在创建写入发生前确定。
     * @param request 固定命令
     * @param current 原始绑定
     * @param currentRow 原有绑定行，隐式默认时为空
     * @param maxCreated 个人创建配额
     * @param now 变更时刻
     * @return 本次业务结果
     */
    private MemoryNamespaceBindingOutcome change(MemoryNamespaceBindingRequest request, MemoryNamespaceBinding current,
            @Nullable GeneralEasyDataObject currentRow, int maxCreated, Instant now) {
        MemoryNamespace target;
        if (request.mode() == MemoryNamespaceBindingRequest.Mode.CREATE_AND_BIND) {
            var candidate = new MemoryNamespace(request.target(), 0, 0, now, new MemoryNamespace.Creation(request.requestId(), request.actor()), null);
            var created = namespaces.createInTransaction(candidate, maxCreated);
            if (created.status() == MemoryNamespaceMutation.Status.QUOTA_EXCEEDED) { return new MemoryNamespaceBindingOutcome(QUOTA_EXCEEDED, current, null); }
            if (created.status() == MemoryNamespaceMutation.Status.CONFLICT) { return new MemoryNamespaceBindingOutcome(CONFLICT, current, null); }
            target = Objects.requireNonNull(created.namespace(), "已登记的绑定目标不能为空。");
        } else {
            var row = namespaces.namespaceRow(request.target());
            if (row == null) { return new MemoryNamespaceBindingOutcome(TARGET_NOT_FOUND, current, null); }
            target = namespaces.namespace(row, request.target());
        }
        if (current.namespaceId().equals(target.id())) { return new MemoryNamespaceBindingOutcome(UNCHANGED, current, reference(current, target)); }
        var next = current.next(request, now);
        namespaces.write(currentRow, "binding:" + scopeKey(current.scope()), "current", new BindingData(1, next));
        return new MemoryNamespaceBindingOutcome(BOUND, next, reference(next, target));
    }

    /**
     * 在当前事务读取绑定行，不创建默认记录。
     * @param scope 真实作用域
     * @return 当前引用或隐式默认
     */
    private MemoryNamespaceBinding currentBinding(AgentScope scope) {
        return binding(namespaces.row("binding:" + scopeKey(scope), "current"), scope);
    }

    /**
     * 校验完整 scope，摘要键不替代正文定位校验。
     * @param row 绑定行或空
     * @param scope 预期作用域
     * @return 已校验绑定
     * @throws IllegalStateException 当存储内容不属于预期 scope 时
     */
    private MemoryNamespaceBinding binding(@Nullable GeneralEasyDataObject row, AgentScope scope) {
        if (row == null) { return MemoryNamespaceBinding.initial(scope); }
        var value = namespaces.read(row, BindingData.class).value();
        if (!value.scope().equals(scope)) { throw new IllegalStateException("记忆空间绑定与作用域定位不一致。"); }
        return value;
    }

    /**
     * 将当前引用和本次事务观察的数据代次组合为纯数据。
     * @param binding 作用域绑定
     * @param namespace 已校验目标元数据
     * @return 需要在动作前复核的引用快照
     */
    private static MemoryNamespaceReference reference(MemoryNamespaceBinding binding, MemoryNamespace namespace) {
        return new MemoryNamespaceReference(binding.scope(), namespace.id(), binding.revision(), namespace.generation());
    }

    /**
     * 分别编码 client/scope 长度再生成稳定摘要，避免拼接歧义与数据库大小写排序规则混淆。
     * @param scope 已校验的完整作用域
     * @return 固定长度物理定位；不是权限令牌
     * @throws IllegalStateException 当运行环境缺少标准摘要算法时
     */
    private static String scopeKey(AgentScope scope) {
        String encoded = scope.clientId().length() + ":" + scope.clientId() + scope.scopeId().length() + ":" + scope.scopeId();
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException("运行环境缺少标准作用域摘要器。"); }
    }

    /**
     * 明确 schema 的绑定存储，不与普通 ConfigData 字段维护两份权威值。
     * @param schemaVersion 存储版本
     * @param value 作用域绑定
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record BindingData(int schemaVersion, MemoryNamespaceBinding value) {
        /**
         * 拒绝未来 schema、空值及把隐式零版本保存成显式绑定。
         * @param schemaVersion 存储版本
         * @param value 绑定数据
         * @throws IllegalArgumentException 当版本或引用状态无效时
         */
        private BindingData {
            if (schemaVersion != 1 || value == null || value.revision() == 0) { throw new IllegalArgumentException("空间绑定存储版本或内容无效。"); }
        }
    }

    /**
     * 单个操作的持久化原始命令与结果；后续绑定不覆盖此前回执。
     * @param schemaVersion 存储版本
     * @param request 原始命令
     * @param outcome 原始业务回执
     * @param occurredAt 原始操作时刻
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record BindingOperation(int schemaVersion, MemoryNamespaceBindingRequest request, MemoryNamespaceBindingOutcome outcome, Instant occurredAt) {
        /**
         * 验证原始命令与回执定位，损坏记录不能被解释为未执行。
         * @param schemaVersion 存储版本
         * @param request 原始命令
         * @param outcome 操作回执
         * @param occurredAt 原始操作时刻
         * @throws IllegalArgumentException 当 schema、定位或字段缺失时
         */
        private BindingOperation {
            var reference = outcome == null ? null : outcome.reference();
            if (schemaVersion != 1 || request == null || outcome == null || occurredAt == null
                    || !request.actor().scope().equals(outcome.binding().scope())
                    || reference != null && !request.target().equals(reference.namespaceId())
                    || outcome.status() == BOUND && !request.equals(outcome.binding().lastChange())
                    || outcome.status() == UNCHANGED && request.expectedRevision() != outcome.binding().revision()) {
                throw new IllegalArgumentException("空间绑定操作回执版本或定位无效。");
            }
        }
    }
}
