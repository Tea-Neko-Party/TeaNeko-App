package org.zexnocs.teanekoagent.component.memory.layered;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException;

import java.time.Instant;

/**
 * 长期整理的安全管理状态及失败策略，不导出候选、原话或租约令牌。
 * <br>2026/11/05：同一输入连续三次无效生成后暂停，原水位保持不变。
 * <br>2026/11/06：持久格式故障独立分类，不计入模型候选暂停次数。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.3-l
 * @version 4.8.3-l
 */
public final class LongMemoryJobs {
    public static final int INVALID_LIMIT = 3;

    /** 禁止实例化管理合同容器。 */
    private LongMemoryJobs() { }

    /**
     * 作业状态与租约阶段分离，暂停不是成功或跳过。
     * @author zExNocs
     * @date 2026/11/05
     * @since 4.8.3-l
     * @version 4.8.3-l
     */
    public enum State { READY, RUNNING, BACKOFF, PAUSED }

    /**
     * 可公开的单分区整理状态。
     * @param revision 管理并发版本
     * @param through 已提交水位
     * @param state 当前状态
     * @param failures 退避失败次数
     * @param invalidAttempts 同输入累计无效生成次数
     * @param reasonCode 最近固定原因，未失败时为空
     * @param failedAt 最近失败时刻
     * @param retryAt 允许再次领取的时刻，暂停时不代表会自动调用
     * @param inputFingerprint 失败输入摘要，不含正文
     * @author zExNocs
     * @date 2026/11/05
     * @since 4.8.3-l
     * @version 4.8.3-l
     */
    public record Status(long revision, long through, State state, int failures, int invalidAttempts,
                         String reasonCode, @Nullable Instant failedAt, Instant retryAt, String inputFingerprint) { }

    /**
     * 只保留已知原因和重试属性，任意第三方异常消息不进入持久状态。
     * @param code 固定原因
     * @param invalidCandidate 是否计入生成暂停阈值
     * @author zExNocs
     * @date 2026/11/05
     * @since 4.8.3-l
     * @version 4.8.3-l
     */
    public record Failure(String code, boolean invalidCandidate) {
        /**
         * 从有界异常链读取领域分类，兼容数据库任务和完成阶段包装。
         * @param failure 实际异常，旧退避调用可为空
         * @return 不含原文的分类
         */
        public static Failure from(@Nullable Throwable failure) {
            for (int depth = 0; failure != null && depth < 24; depth++, failure = failure.getCause()) {
                // 2026/11/06：持久读取失败不是模型候选错误，不误触发三次暂停。
                if (failure instanceof MemoryStorageException) {
                    return new Failure("MEMORY_STORAGE_FORMAT", false);
                }
                if (failure instanceof MemoryEvidenceException evidence) {
                    return new Failure(evidence.code().name(), evidence.code().invalidCandidate());
                }
                if (failure instanceof MemoryProcessingException processing) {
                    return new Failure(processing.reason().name(),
                            processing.reason() == MemoryProcessingException.Reason.INVALID_OUTPUT
                                    || processing.reason() == MemoryProcessingException.Reason.OUTPUT_LIMIT);
                }
                if (failure instanceof ExecutionStoppedException stopped) {
                    return new Failure("EXECUTION_" + stopped.reason().name(), false);
                }
            }
            return new Failure("PROCESSING_FAILED", false);
        }
    }
}
