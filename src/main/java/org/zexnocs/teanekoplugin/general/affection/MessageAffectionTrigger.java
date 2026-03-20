package org.zexnocs.teanekoplugin.general.affection;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekoapp.message.TeaNekoMessageReceiveEvent;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.message.api.content.IAtTeaNekoContentPart;
import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserService;
import org.zexnocs.teanekocore.cache.ConcurrentMapCacheContainer;
import org.zexnocs.teanekocore.cache.interfaces.ICacheService;
import org.zexnocs.teanekocore.event.core.EventHandler;
import org.zexnocs.teanekocore.event.core.EventListener;
import org.zexnocs.teanekocore.framework.pair.HashPair;
import org.zexnocs.teanekocore.utils.RandomUtil;
import org.zexnocs.teanekoplugin.general.affection.interfaces.IAffectionService;

import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 由消息触发的好感度变动
 *
 * @author zExNocs
 * @date 2026/03/06
 * @since 4.1.0
 */
@EventListener
public class MessageAffectionTrigger {
    /// 当前配置的期望值是： | 9 | 14 | 18 | 23 | 28 | 36 | 40 | 50 | 58 | 65 |  顺序按照位置从 1 到 10
    /// 核心参数
    public static final double ENERGY_GROWTH_RATE = 0.15;               // 势能增长系数，控制失败时势能增加量
    public static final double ENERGY_DECAY_RATE = 1.5;                 // 势能衰减系数，控制增量随势能衰减的速度
    public static final double ENERGY_INFLUENCE_RATE = 0.7;             // 势能影响系数，控制势能对概率的最大影响
    public static final double PROBABILITY_GROWTH_SENSITIVITY = 4.0;    // 概率增长敏感度，控制概率随势能增长的速度
    public static final double LOCATION_DECAY_RATE = 0.2;               // 位置权重衰减系数
    public static final double BASE_EXPECTED = 35.0;                    // 基础势能累计量
    public static final double SELF_AFFECTION_RATE = 0.33;              // 自我好感度触发率

    /// 最多允许缓存的消息数量
    public static final int MAX_CACHED_MESSAGES = 10;

    /// scope id → (size, messageList)，存储最近发送的消息发送者列表，缓存时间是 10 min
    private final ConcurrentMapCacheContainer<String, Deque<UUID>> messageCache;

    /// scope id → (sender, target) → energy，存储每对用户之间的势能值，缓存时间是 2h
    private final ConcurrentMapCacheContainer<String, Map<HashPair<UUID, UUID>, Double>> energyCache;

    private final RandomUtil randomUtil;
    private final IAffectionService iAffectionService;
    private final ITeaUserService iTeaUserService;

    @Autowired
    public MessageAffectionTrigger(ICacheService iCacheService,
                                   RandomUtil randomUtil,
                                   IAffectionService iAffectionService, ITeaUserService iTeaUserService) {
        // 10 分钟过期，不参与手动清理
        this.messageCache = ConcurrentMapCacheContainer.of(iCacheService,
                10 * 60_000L,
                false);
        // 2 小时过期，不参与手动清理
        this.energyCache = ConcurrentMapCacheContainer.of(iCacheService,
                2 * 60 * 60_000L,
                false);

        this.randomUtil = randomUtil;
        this.iAffectionService = iAffectionService;
        this.iTeaUserService = iTeaUserService;
    }

    /**
     * 接收到消息尝试触发好感度变动
     */
    @EventHandler
    public void handle(TeaNekoMessageReceiveEvent<?> event) {
        var data = event.getData();
        var scopeId = data.getScopeId();
        var senderId = data.getUserData().getUuid();

        // 处理普通消息
        var queue = this.messageCache.computeIfAbsent(scopeId, k -> new ConcurrentLinkedDeque<>());
        // 先拿出一份副本
        var copyQueue = new ConcurrentLinkedDeque<>(queue);
        // 操作原来的队列，是线程不安全的，但是问题不大
        // 加入当前发送者
        queue.offerLast(senderId);
        // 超出长度限制，移除最早的发送者
        while(queue.size() > MAX_CACHED_MESSAGES) {
            queue.pollFirst();
        }

        // ------------- 加好感 --------------
        // 处理一次at消息，如果处理成功了则不再处理普通消息
        if(handleAt(data)) {
            return;
        }

        // 操作副本，计算当前发送者对每个历史发送者好感度变动
        int position = 1;
        var currentEnergyMap = energyCache.computeIfAbsent(scopeId, k -> new ConcurrentHashMap<>());
        var closeList = new HashSet<UUID>();
        while(!copyQueue.isEmpty() && position <= MAX_CACHED_MESSAGES) {
            // 从队列尾部取出发送者，尾部是最近发送的
            var targetId = copyQueue.pollLast();
            if(targetId == null || closeList.contains(targetId)) {
                // 已经处理过的目标，跳过
                position++;
                continue;
            }
            closeList.add(targetId);
            // 获取 sender → target 的势能
            var key = HashPair.of(senderId, targetId);
            int finalPos = position;
            final var successHolder = new boolean[1];
            currentEnergyMap.compute(key, (k, oldVal) -> {
                double currentEnergy = (oldVal != null) ? oldVal : 0.0;
                double successProbability = _calculateProbability(finalPos, currentEnergy);
                if(randomUtil.nextDouble() < successProbability) {
                    // 增加好感度
                    successHolder[0] = true;
                    // 成功，势能归零
                    return 0.0;
                } else {
                    // 失败，增加势能
                    var delta = _calculateEnergyIncrement(finalPos, currentEnergy);
                    if(senderId.equals(targetId)) {
                        // 如果是自己发给自己的消息，势能增量打折
                        delta *= SELF_AFFECTION_RATE;
                    }
                    return currentEnergy + delta;
                }
            });
            if(successHolder[0]) {
                // 成功增加好感度
                iAffectionService.increaseAffectionDailyLimit(senderId, targetId, 1);
            }
            // 更新位置
            position += 1;
        }
    }

    /**
     * 处理 @ 消息
     * 额外累加一次好感度触发机会
     *
     * @return 是否触发了好感度增加
     */
    private boolean handleAt(ITeaNekoMessageData data) {
        var messages = data.getMessages();
        IAtTeaNekoContentPart atMessageContent = null;
        // 查找 @ 子数据
        for(var message : messages) {
            var content = message.getContentPart();
            if(content instanceof IAtTeaNekoContentPart atContent) {
                atMessageContent = atContent;
                if(atMessageContent.getId().equalsIgnoreCase("all")) {
                    // @全体成员 不触发好感度增加
                    return false;
                }
                break;
            }
        }
        // 没有找到 @ 子数据
        if(atMessageContent == null) {
            return false;
        }

        var scopeId = data.getScopeId();
        var senderId = data.getUserData().getUuid();
        var targetId = iTeaUserService.get(data.getClient(), atMessageContent.getId());
        if(targetId == null) {
            // 一般不会发送这种情况
            return false;
        }
        var energyMap = energyCache.computeIfAbsent(scopeId, k -> new ConcurrentHashMap<>());
        // 获取 sender → target 的势能
        var key = HashPair.of(senderId, targetId);
        final var successHolder = new boolean[1];
        energyMap.compute(key, (k, oldVal) -> {
            double currentEnergy = (oldVal != null) ? oldVal : 0.0;
            double successProbability = _calculateProbability(1, currentEnergy);
            if(randomUtil.nextDouble() < successProbability) {
                // 增加好感度
                successHolder[0] = true;
                // 成功，减少势能
                return 0.0;
            } else {
                // 失败，增加位置为 1 的势能增量
                return currentEnergy + _calculateEnergyIncrement(1, currentEnergy);
            }
        });
        if(successHolder[0]) {
            // 成功增加好感度
            iAffectionService.increaseAffectionDailyLimit(senderId, targetId, 1);
        }
        return true;
    }

    /// 根据位置和势能计算成功概率
    private double _calculateProbability(int position, double energy) {
        // 基础成功概率: P_base = 1 / E[X]
        double P_base = Math.min(0.999999, Math.max(1e-6, 1.0 / _expectedTimes(position)));
        // 使用有界增长方法计算实际成功概率
        double offset = Math.log(P_base / (1.0 - P_base));
        double growth = _shiftedSigmoid(PROBABILITY_GROWTH_SENSITIVITY * energy, offset);
        return P_base + ENERGY_INFLUENCE_RATE * growth * (1.0 - P_base);
    }

    /// 计算失败时势能增量
    private double _calculateEnergyIncrement(int position, double currentEnergy) {
        double w = Math.exp(-LOCATION_DECAY_RATE * (position - 1));
        return ENERGY_GROWTH_RATE * w * (1.0 - _sigmoid(ENERGY_DECAY_RATE * currentEnergy));
    }

    /// 偏移的 sigmoid 函数
    private static double _shiftedSigmoid(double x, double offset) {
        return _sigmoid(x + offset) - _sigmoid(offset);
    }

    /// sigmoid 函数
    private static double _sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    /// 期望次数计算，位置越靠前期望越小，概率越大
    private static double _expectedTimes(int position) {
        return BASE_EXPECTED * position;
    }
}
