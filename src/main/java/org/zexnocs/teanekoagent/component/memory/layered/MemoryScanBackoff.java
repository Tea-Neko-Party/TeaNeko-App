package org.zexnocs.teanekoagent.component.memory.layered;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 领取租约前的进程内退避，不覆盖不可读取的持久记录；由单在途处理器串行使用。
 * <br>2026/11/06：按目录或分区阶段退避，容量耗尽时对未跟踪目标采用共同退避。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.3-l
 * @version 4.8.3-l
 */
final class MemoryScanBackoff {
    private static final int MAX_TRACKED = 1024;
    private final Map<String, Retry> retries = new HashMap<>();
    private @Nullable Retry overflow;

    /** 创建空的本进程退避状态，不读取持久记录。 */
    MemoryScanBackoff() { }

    /**
     * 查询目标自己的冷却；容量不足时采用公共冷却，不在此处累计次数。
     * @param key 目录或分区阶段键，不含原话
     * @param now 当前时刻
     * @return 当前可进行一次实际读取时为真
     */
    boolean allows(String key, Instant now) {
        var retry = retries.get(key);
        if (retry == null) {
            retry = overflow;
        }
        return retry == null || !retry.at().isAfter(now);
    }

    /**
     * 只为实际失败累加次数；冷却期间跳过不会延长时刻或再次告警。
     * @param key 原阶段键
     * @param now 失败观察时刻
     * @return 下次允许尝试的时刻
     */
    Instant failed(String key, Instant now) {
        var previous = retries.get(key);
        if (previous == null && retries.size() >= MAX_TRACKED) {
            overflow = next(overflow, now);
            return overflow.at();
        }
        var next = next(previous, now);
        retries.put(key, next);
        return next.at();
    }

    /**
     * 成功读取后解除该阶段退避，不删除其他分区的失败记录。
     * @param key 原阶段键
     */
    void succeeded(String key) {
        retries.remove(key);
        if (retries.size() < MAX_TRACKED) {
            overflow = null;
        }
    }

    /**
     * 计算有上限的指数退避，避免长期故障导致数值溢出。
     * @param previous 原失败计数，没有历史时为空
     * @param now 当前失败时刻
     * @return 30 秒起、最长一小时的下一次尝试
     */
    private static Retry next(@Nullable Retry previous, Instant now) {
        int attempts = previous == null ? 1 : Math.min(30, previous.attempts() + 1);
        return new Retry(attempts, now.plusSeconds(Math.min(3600, 30L << Math.min(7, attempts - 1))));
    }

    /**
     * 无正文、异常或租约引用的短状态。
     * @param attempts 实际失败次数
     * @param at 允许重试的时刻
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.3-l
     * @version 4.8.3-l
     */
    private record Retry(int attempts, Instant at) { }
}
