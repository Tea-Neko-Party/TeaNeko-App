package org.zexnocs.teanekoplugin.general.signin;

import lombok.Builder;
import lombok.Data;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.sender.api.sender_box.IEasyMessageSenderBuilder;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.cache.ConcurrentMapCacheContainer;
import org.zexnocs.teanekocore.cache.interfaces.ICacheService;
import org.zexnocs.teanekocore.database.easydata.cleanable.CleanableEasyData;
import org.zexnocs.teanekocore.database.easydata.general.GeneralEasyData;
import org.zexnocs.teanekocore.database.itemdata.exception.InsufficientItemCountException;
import org.zexnocs.teanekocore.database.itemdata.interfaces.IItemDataDTO;
import org.zexnocs.teanekocore.utils.ChinaDateUtil;
import org.zexnocs.teanekoplugin.general.signin.data.SignInChunkData;
import org.zexnocs.teanekoplugin.general.signin.data.SignInData;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 补签系统。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.3.4
 * @version 4.5.0-l
 */
@Service
public class RepairSignInService {
    /// 签到服务
    private final SignInService signInService;

    /** UUID -> 锁对象 */
    private final ConcurrentMapCacheContainer<UUID, ReentrantLock> userLocks;

    /**
     * 装配原签到业务和补签缓存，使补签复用原事务与反馈规则。
     *
     * @param signInService 由装配方提供的 {@code SignInService} 协作依赖
     * @param iCacheService 缓存注册与生命周期服务
     */
    public RepairSignInService(SignInService signInService, ICacheService iCacheService) {
        this.signInService = signInService;
        this.userLocks = ConcurrentMapCacheContainer.of(iCacheService);
    }

    /**
     * 线性安全的补签
     *
     * @param sender 由装配方提供的 {@code IEasyMessageSenderBuilder} 协作依赖
     * @param userId 原业务边界中的目标用户标识
     * @param now 用于本次处理的 {@code now} 数据
     * @param k 用于本次处理的 {@code k} 数据
     * @param card 用于本次处理的 {@code card} 数据
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    public CompletionStage<Void> repairSignIn(@Nullable IEasyMessageSenderBuilder sender,
                             UUID userId,
                             Instant now,
                             int k,
                             @Nullable IItemDataDTO<?> card) {
        var lock = userLocks.computeIfAbsent(userId, key -> new ReentrantLock());
        lock.lock();
        try {
            return __repairSignIn(sender, userId, now, k, card);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 补签
     *
     * @param sender 发送器，用于发送信息
     * @param userId 原业务边界中的目标用户标识
     * @param now    当前时间点
     * @param k      修补的最大个数
     * @param card   用于扣除补签卡，如果为 null 则不扣除
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    public CompletionStage<Void> __repairSignIn(@Nullable IEasyMessageSenderBuilder sender,
                             UUID userId,
                             Instant now,
                             int k,
                             @Nullable IItemDataDTO<?> card) {
        var currentDate = ChinaDateUtil.Instance.convertToChinaDate(now);

        var target = GeneralEasyData.of(SignInService.NAMESPACE).get(userId.toString());
        var data = signInService.getSignInData(target);

        var lastDate = ChinaDateUtil.Instance.convertToChinaDate(data.getLastTime());

        // 1. 今天没签到
        if (!currentDate.isEqual(lastDate)) {
            return sender == null ? CompletableFuture.completedStage(null) : sender.sendReplyMessageAsync("主人今天还没有签到喵！请先签到喵~");
        }

        var chunks = new LinkedList<>(target.getList(SignInChunkData.KEY, SignInChunkData.class));

        // 2. 只有一个 chunk
        if (chunks.size() <= 1) {
            return sender == null ? CompletableFuture.completedStage(null) : sender.sendReplyMessageAsync("主人目前没有可补签的断档喵！");
        }

        // 3. 开始构造新的 chunks
        int remain = k;
        // 用于记录本次补签区间
        LinkedList<String> repairedRanges = new LinkedList<>();
        for (int i = chunks.size() - 1; i >= 1 && remain > 0; i--) {
            var curr = chunks.get(i);
            var prev = chunks.get(i - 1);
            // 从当前区块的第一个时间开始到上一个区块的最后一个时间
            // 两个时间的 gap days 就是这两个区块区间需要补签的时间
            var prevLast = ChinaDateUtil.Instance.convertToChinaDate(prev.getLastTime());
            var currLast = ChinaDateUtil.Instance.convertToChinaDate(curr.getLastTime());
            var currFirst = currLast.minusDays(curr.getContinuous() - 1);
            int gapDays = Math.toIntExact(ChronoUnit.DAYS.between(prevLast, currFirst) - 1);
            // 从后往前取该区间的日期，要么全部取完，要么取到最大值
            if(gapDays <= 0) {
                // 一般不会发生，但是防止鲁棒性
                break;
            }
            if(remain >= gapDays) {
                // 说明需要补完整个 gap
                prev.setContinuous(prev.getContinuous() + gapDays + curr.getContinuous());
                prev.setLastTime(curr.getLastTime());
                // 因为是从后往前的遍历，所以删除 i 是合理的。
                chunks.remove(i);
                // 记录本次补签的区间
                String range = formatRange(prevLast.plusDays(1), currFirst.minusDays(1));
                repairedRanges.addFirst(range);
                // 更新 remain
                remain -= gapDays;
            } else {
                // 只补一部分 gap，扩展当前 chunk
                curr.setContinuous(curr.getContinuous() + remain);
                // 记录补签区间
                LocalDate start = currFirst.minusDays(remain);
                LocalDate end = currFirst.minusDays(1);
                String range = formatRange(start, end);
                repairedRanges.addFirst(range);
                // 更新 remain
                remain = 0;
            }
        }
        // 如果没有合适的补签区间
        if (repairedRanges.isEmpty()) {
            return sender == null ? CompletableFuture.completedStage(null) : sender.sendReplyMessageAsync("主人目前没有可补签的断档喵！");
        }
        // 实际的 repair 个数
        int repairCount = k - remain;
        // 4. 更新 SignInData
        var newData = data.toBuilder()
                .totalDays(data.getTotalDays() + repairCount)
                .continuousDays(chunks.getLast().getContinuous())
                .build();
        // 5. 写入自定义记录 ----
        var recordTask = CleanableEasyData.of(SignInService.NAMESPACE)
                .get(userId.toString())
                .getTaskConfig("补签记录更新");

        RepairRecordData recordData = RepairRecordData.builder()
                .repairCount(repairCount)
                .repairRanges(repairedRanges)
                .timestamp(now)
                .build();

        recordTask.set("repair-" + ChinaDateUtil.Instance.convertToDateTimeString(now), recordData);
        // ---- 6. 更新 SignInData + chunks + 补签卡 ----
        var dataTask = target.getTaskConfig("补签数据更新")
                .set(SignInData.KEY, newData)
                .set(SignInChunkData.KEY, chunks);
        recordTask.merge(dataTask);

        // ---- 7. 更新补签卡 ----
        if(card != null) {
            var cardTask = card.getDatabaseTaskConfig("消耗补签卡")
                    .reduceCount(repairCount);
            recordTask.merge(cardTask);
        }

        // ---- 8. 提交任务 ----
        return recordTask.pushAsync()
                .thenCompose(ignored -> sender == null ? CompletableFuture.completedStage(null)
                        : sender.sendReplyMessageAsync("""
                            主人补签成功喵！
                            本次补签的天数：%d
                            本次补签日期：%s
                            当前连续签到：%d 天
                            当前累计签到：%d 天""".formatted(repairCount,
                                String.join(", ", repairedRanges), chunks.getLast().getContinuous(), newData.getTotalDays())))
                .exceptionallyCompose(error -> {
                    if (TaskFuture.unwrapException(error) instanceof InsufficientItemCountException && sender != null) {
                        return sender.sendReplyMessageAsync("补签卡不足哦~");
                    }
                    return CompletableFuture.failedStage(error);
                });
    }

    /**
     * 格式化连续日期区间，如 [05-03, 05-10]
     *
     * @param start 用于本次处理的 {@code start} 数据
     * @param end 用于本次处理的 {@code end} 数据
     * @return 按本方法契约生成的文本或标识
     */
    private String formatRange(LocalDate start, LocalDate end) {
        // 如果 start 和 end 相等，那么不使用区间而是单个时间
        if(start.isEqual(end)) {
            return ChinaDateUtil.Instance.convertToString(start);
        }
        // 否则使用区间的格式
        return "[%s, %s]".formatted(
                ChinaDateUtil.Instance.convertToString(start),
                ChinaDateUtil.Instance.convertToString(end)
        );
    }

    /**
     * 自定义记录数据类
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    @Data
    @Builder
    public static class RepairRecordData {
        private int repairCount;                 // 本次补签数量
        private LinkedList<String> repairRanges; // 补签的区间描述
        private Instant timestamp;               // 补签操作时间
    }
}
