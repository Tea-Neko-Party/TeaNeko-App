package org.zexnocs.teanekoagent.memory.profile;

import org.zexnocs.teanekoagent.memory.fact.MemoryFactGenerationException;
import org.zexnocs.teanekoagent.memory.fact.MemoryFactValue;
import org.zexnocs.teanekoagent.memory.recall.IMemoryRecallService;
import org.zexnocs.teanekoagent.memory.recall.MemoryRecallException;
import org.zexnocs.teanekoagent.memory.recall.MemoryRecallQuery;
import org.zexnocs.teanekoagent.memory.recall.MemoryRecallRequest;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;

/**
 * 用一次明确主体的 Recall 重建画像，复用稳定读取和预算，不维护第二套存储或缓存。
 * 初始调用与视图校验均在借用业务域进行，不直接访问 Repository、模型或聊天历史。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class UserProfileService implements IUserProfileService {
    private final IMemoryRecallService recall;
    private final Executor executor;

    /**
     * 保存业务端口与执行器，构造不读取配置、数据或生成任何画像。
     * @param recall 独立事实检索端口
     * @param executor Core 借用业务执行器，本服务不关闭它
     * @throws NullPointerException 当依赖为空时
     */
    public UserProfileService(IMemoryRecallService recall, Executor executor) {
        this.recall = Objects.requireNonNull(recall, "画像事实检索端口不能为空。");
        this.executor = Objects.requireNonNull(executor, "画像业务执行器不能为空。");
    }

    /**
     * 固定唯一主体及全部事实/偏好范围，保留未知事件的显式记录时间估算。
     * 取消返回观察视图不撤销底层只读事务；失败不回退到缓存或正常空画像。
     * @param request 当前可信请求
     * @return 按来源原覆盖程度完成的不可变视图
     * @throws NullPointerException 当请求为空时
     */
    @Override public CompletionStage<UserProfileView> read(UserProfileRequest request) {
        Objects.requireNonNull(request, "画像读取请求不能为空。");
        try {
            return CompletableFuture.supplyAsync(() -> {
                var query = new MemoryRecallQuery(request.subjectId(), null, Set.of(MemoryFactValue.Kind.FACT, MemoryFactValue.Kind.PREFERENCE),
                        Set.of(), List.of(), null, null, true);
                return Objects.requireNonNull(recall.recall(new MemoryRecallRequest(request.reference(), query, request.use(), request.limits())), "画像来源不能返回空阶段。");
            }, executor).thenCompose(stage -> stage)
                    .thenApplyAsync(result -> UserProfileView.from(request, Objects.requireNonNull(result, "画像来源不能返回空结果。")), executor)
                    .handle((view, failure) -> {
                        if (failure != null) { throw classify(failure); } return view;
                    }).minimalCompletionStage();
        } catch (RuntimeException failure) { return CompletableFuture.failedStage(classify(failure)); }
    }

    /**
     * 保留缺失、旧代次和配置分类，其余错误统一为暂不可用，不传播来源正文和异常链。
     * @param failure 原失败
     * @return 仅包含固定类别的中文异常
     */
    private static RuntimeException classify(Throwable failure) {
        var current = failure;
        for (int depth = 0; depth < 32 && (current instanceof CompletionException || current instanceof ExecutionException); depth++) {
            var cause = current.getCause(); if (cause == null || cause == current) { break; } current = cause;
        }
        if (current instanceof MemoryFactGenerationException generation) { return new MemoryFactGenerationException(generation.missingNamespace()); }
        if (current instanceof MemoryRecallException known) { return new MemoryRecallException(known.reason()); }
        return new MemoryRecallException(MemoryRecallException.Reason.UNAVAILABLE);
    }
}
