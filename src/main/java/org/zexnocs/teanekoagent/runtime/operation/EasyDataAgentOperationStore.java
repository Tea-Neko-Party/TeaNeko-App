package org.zexnocs.teanekoagent.runtime.operation;

// 2026/11/07：按真实职责分离通用来源、防重底座与插件业务，持久标识保持不变。
// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.database.easydata.TeaNekoAgentEasyData;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataLock;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataTransactionDto;
import org.zexnocs.teanekocore.utils.StableDigest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * 用既有事务、唯一键和 Version 仲裁原生操作防重，进程重启后不接管未知副作用。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/07：业务归属与通用底座按职责分离，保留持久身份及完成边界。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@Service
public final class EasyDataAgentOperationStore implements IAgentOperationStore {
    private static final String DOMAIN = "agent.command.v1";
    private final TeaNekoAgentEasyData data;
    private final ObjectMapper mapper;
    /**
     * 保存借用持久化设施，不在构造时读取或清理记录。
     * @param data Agent 专用数据入口
     * @param mapper 明确业务 schema 转换器
     */
    public EasyDataAgentOperationStore(TeaNekoAgentEasyData data, ObjectMapper mapper) {
        this.data = Objects.requireNonNull(data).inNamespace(DOMAIN); this.mapper = Objects.requireNonNull(mapper);
    }
    /**
     * 取得防重目标的事务视图，不缓存尚未提交的执行权。
     * @return 固定目标 DTO
     */
    private IEasyDataTransactionDto attempts() { return data.get("attempt").atomic(); }
    /**
     * 唯一冲突由原 DatabaseTask 重试后读取最新回执，不重复执行宿主命令。
     * @param attempt 固定动作
     * @return 已提交的执行权或原状态
     */
    @Override public CompletionStage<Claim> claim(Attempt attempt) {
        return submit(() -> {
            var key = key(attempt); var row = attempts().find(key, EasyDataLock.NONE);
            if (row != null) {
                var original = decode(row.getValue());
                return new Claim(false, original.attempt().equals(identity(attempt)) ? original.receipt()
                        : new AgentOperationReceipt(AgentOperationReceipt.Status.CONFLICT));
            }
            var receipt = new AgentOperationReceipt(AgentOperationReceipt.Status.INDETERMINATE);
            // 2026/11/07：保留原 JSON 身份结构，读回时不反序列化由插件拥有的引用接口。
            attempts().save(attempts().create(key, mapper.writeValueAsString(new State(1, identity(attempt), receipt))));
            return new Claim(true, receipt);
        });
    }
    /**
     * 按原行版本写真实终态，晚到的观察者不能覆盖不同终态。
     * @param attempt 原动作
     * @param receipt 实际终态
     * @return 持久化后的回执
     */
    @Override public CompletionStage<AgentOperationReceipt> finish(Attempt attempt, AgentOperationReceipt receipt) {
        return submit(() -> {
            var row = attempts().find(key(attempt), EasyDataLock.WRITE);
            if (row == null) { throw new IllegalStateException("命令执行占用记录缺失。"); }
            var original = decode(row.getValue());
            if (!original.attempt().equals(identity(attempt))) {
                throw new IllegalStateException("命令执行占用身份已改变。");
            }
            if (original.receipt().status() != AgentOperationReceipt.Status.INDETERMINATE) {
                if (!original.receipt().equals(receipt)) { throw new IllegalStateException("命令执行终态不能被另一结果覆盖。"); }
                return original.receipt();
            }
            row.setValue(mapper.writeValueAsString(new State(1, identity(attempt), receipt)));
            attempts().save(row);
            return receipt;
        });
    }
    /**
     * 原请求/调用定位独立于正文，改变业务参数只会冲突，不能取得新执行权。
     * @param attempt 原动作
     * @return 无拼接歧义的有界键
     */
    private String key(Attempt attempt) { return StableDigest.sha256(attempt.kind().name(), attempt.requestId().toString(), attempt.callId()); }
    /**
     * 使用与持久化读回一致的数字节点类型固定身份，避免小范围 long 与 int 的节点差异形成假冲突。
     * @param attempt 已通过运行契约校验的固定动作
     * @return 与既有 JSON 结构完全一致的身份树
     */
    private JsonNode identity(Attempt attempt) {
        return mapper.readTree(mapper.writeValueAsString(attempt));
    }
    /**
     * 拒绝损坏或未来 schema，不输出原 JSON/异常链。
     * @param json 原存储正文
     * @return 明确动作状态
     */
    private State decode(String json) {
        try {
            var state = Objects.requireNonNull(mapper.readValue(json, State.class));
            if (state.schemaVersion() != 1 || state.attempt() == null || !state.attempt().isObject()
                    || state.receipt() == null) {
                        throw new IllegalArgumentException();
                    }
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
            return data.transaction("命令执行防重事务", action);
        } catch (RuntimeException failure) { return CompletableFuture.failedStage(failure); }
    }
    /**
     * 明确的持久化 schema，不保存命令正文或业务任意返回对象。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param schemaVersion 存储版本
     * @param attempt 原动作身份
     * @param receipt 已观察终态或在途状态
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.5.0-l
     * @version 4.9.0-l
     */
    private record State(int schemaVersion, JsonNode attempt, AgentOperationReceipt receipt) {}
}
