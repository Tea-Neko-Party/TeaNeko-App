package org.zexnocs.teanekoagent.adapter.command;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.agent.command.CommandInvocationReceipt;
import org.zexnocs.teanekoagent.agent.command.ICommandAttemptStore;
import org.zexnocs.teanekocore.database.base.DatabaseTaskConfig;
import org.zexnocs.teanekocore.database.base.interfaces.IDatabaseService;
import org.zexnocs.teanekocore.database.easydata.general.GeneralEasyDataRepository;
import org.zexnocs.teanekocore.utils.StableDigest;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 用既有事务、唯一键和 Version 仲裁命令防重，进程重启后不接管未知副作用。
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service
public final class EasyDataCommandAttemptStore implements ICommandAttemptStore {
    private static final String DOMAIN = "agent.command.v1";
    private final GeneralEasyDataRepository repository;
    private final IDatabaseService database;
    private final ObjectMapper mapper;
    /**
     * 保存借用持久化设施，不在构造时读取或清理记录。
     * @param repository 原通用行仓库
     * @param database 原数据库事务服务
     * @param mapper 明确业务 schema 转换器
     */
    public EasyDataCommandAttemptStore(GeneralEasyDataRepository repository, IDatabaseService database, ObjectMapper mapper) {
        this.repository = Objects.requireNonNull(repository); this.database = Objects.requireNonNull(database); this.mapper = Objects.requireNonNull(mapper);
    }
    /**
     * 唯一冲突由原 DatabaseTask 重试后读取最新回执，不重复执行宿主命令。
     * @param attempt 固定动作
     * @return 已提交的执行权或原状态
     */
    @Override public CompletionStage<Claim> claim(Attempt attempt) {
        return submit(() -> {
            var key = key(attempt); var row = repository.findByNamespaceAndTargetAndKey(DOMAIN, "attempt", key);
            if (row != null) {
                var original = decode(row.getValue());
                return new Claim(false, original.attempt().equals(attempt) ? original.receipt() : new CommandInvocationReceipt(CommandInvocationReceipt.Status.CONFLICT));
            }
            var receipt = new CommandInvocationReceipt(CommandInvocationReceipt.Status.INDETERMINATE);
            repository.saveAndFlush(repository.create(DOMAIN, "attempt", key, mapper.writeValueAsString(new State(1, attempt, receipt))));
            return new Claim(true, receipt);
        });
    }
    /**
     * 按原行版本写真实终态，晚到的观察者不能覆盖不同终态。
     * @param attempt 原动作
     * @param receipt 实际终态
     * @return 持久化后的回执
     */
    @Override public CompletionStage<CommandInvocationReceipt> finish(Attempt attempt, CommandInvocationReceipt receipt) {
        return submit(() -> {
            var row = repository.findByNamespaceAndTargetAndKey(DOMAIN, "attempt", key(attempt));
            if (row == null) { throw new IllegalStateException("命令执行占用记录缺失。"); }
            var original = decode(row.getValue());
            if (!original.attempt().equals(attempt)) { throw new IllegalStateException("命令执行占用身份已改变。"); }
            if (original.receipt().status() != CommandInvocationReceipt.Status.INDETERMINATE) {
                if (!original.receipt().equals(receipt)) { throw new IllegalStateException("命令执行终态不能被另一结果覆盖。"); }
                return original.receipt();
            }
            row.setValue(mapper.writeValueAsString(new State(1, attempt, receipt))); repository.saveAndFlush(row); return receipt;
        });
    }
    /**
     * 原请求/调用定位独立于正文，改变业务参数只会冲突，不能取得新执行权。
     * @param attempt 原动作
     * @return 无拼接歧义的有界键
     */
    private String key(Attempt attempt) { return StableDigest.sha256(attempt.kind().name(), attempt.requestId().toString(), attempt.callId()); }
    /**
     * 拒绝损坏或未来 schema，不输出原 JSON/异常链。
     * @param json 原存储正文
     * @return 明确动作状态
     */
    private State decode(String json) {
        try {
            var state = Objects.requireNonNull(mapper.readValue(json, State.class));
            if (state.schemaVersion() != 1 || state.attempt() == null || state.receipt() == null) { throw new IllegalArgumentException(); }
            return state;
        } catch (RuntimeException invalid) { throw new IllegalStateException("命令执行记录损坏或版本不受支持。"); }
    }
    /**
     * 仅在最终事务提交后公开执行权或回执，复用原重试与提交机制。
     * @param action 事务动作
     * @param <T> 业务结果
     * @return 提交后完成的阶段
     */
    private <T> CompletionStage<T> submit(Supplier<T> action) {
        try {
            var result = new AtomicReference<T>(); var task = new DatabaseTaskConfig(database, "命令执行防重事务");
            task.addTransactionTask(() -> { result.set(null); result.set(action.get()); });
            return task.pushAsync().thenApply(ignored -> Objects.requireNonNull(result.get(), "命令事务未产生回执。"));
        } catch (RuntimeException failure) { return CompletableFuture.failedStage(failure); }
    }
    /**
     * 明确的持久化 schema，不保存命令正文或业务任意返回对象。
     * @param schemaVersion 存储版本
     * @param attempt 原动作身份
     * @param receipt 已观察终态或在途状态
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record State(int schemaVersion, Attempt attempt, CommandInvocationReceipt receipt) {}
}
