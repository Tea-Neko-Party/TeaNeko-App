package org.zexnocs.teanekoplugin.general.truth;

import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.sender.api.sender_box.IEasyMessageSenderBuilder;
import org.zexnocs.teanekocore.actuator.task.EmptyTaskResult;
import org.zexnocs.teanekocore.actuator.task.TaskConfig;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskService;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekoplugin.general.dice.DiceService;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 真心话服务类，负责管理真心话的状态和数据。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.1.0
 * @version 4.5.0-l
 * @see DiceService
 */
@Service("truthService")
public class TruthService {
    private final DiceService diceService;
    private final ILogger logger;
    private final ITaskService taskService;

    /// scope id → 群组数据
    private final Map<String, TruthGroupData> truthGroupDataMap = new ConcurrentHashMap<>();

    /**
     * 装配任务、骰子和诊断服务；当前命令完成不等待未来独立定时回调。
     *
     * @param taskService 由装配方提供的 {@code ITaskService} 协作依赖
     * @param diceService 由装配方提供的 {@code DiceService} 协作依赖
     * @param logger 项目日志接口
     */
    @Autowired
    public TruthService(ITaskService taskService,
                        DiceService diceService, ILogger logger) {
        this.diceService = diceService;
        this.logger = logger;
        this.taskService = taskService;
    }

    /**
     * 判断真心话是否正在运行。
     *
     * @param scopeId 本次操作所属作用域
     * @return true 如果没有正在运行
     */
    public boolean isNotRunning(String scopeId) {
        return !truthGroupDataMap.containsKey(scopeId);
    }

    /**
     * 开始真心话。
     *
     * @param data 消息数据，用于获取消息发送器和 scope ID
     * @param autoEndTime 自动结束时间，单位秒
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    public CompletionStage<Void> start(ITeaNekoMessageData data, long autoEndTime) {
        // 获取是否已经存在真心话数据
        var scopeId = data.getScopeId();
        // 先创建一个新的 TruthGroupData
        var newGroupData = new TruthGroupData(data);
        if(truthGroupDataMap.putIfAbsent(scopeId, newGroupData) != null) {
            return data.getMessageSender().sendReplyMessageAsync("真心话已经在进行中喵！");
        }
        // 获取该群组的骰子数据
        var diceData = diceService.getDiceData(scopeId);
        String OPEN_TEXT = """
                真心话开始喵！
                玩法：输入"/d"投掷骰子，第一个数值最大的问最后一个数值最小的喵。
                真心话将会在 %d 秒后自动结束喵。
                具体规则请看群公告喵。
                当前骰子最大值：%d""".formatted(autoEndTime, diceData.getDiceMaxValue());
        var announced = data.getMessageSender().sendTextMessageAsync(OPEN_TEXT);
        // 创建一个自动停止的定时器
        var taskConfig = TaskConfig.<Void>builder()
                .name("结束 %s 真心话".formatted(scopeId))
                .delayDuration(Duration.ofSeconds(autoEndTime))
                .callable(() -> {
                    // 防止结束新的真心话时，旧的定时器还在执行结束操作
                    if(newGroupData.setEnd()) {
                        TaskFuture.fromStage(logger, "真心话自动结束", end(null, scopeId)).finish();
                    }
                    return EmptyTaskResult.INSTANCE;
                })
                .build();

        taskService.subscribe(taskConfig, EmptyTaskResult.getResultType());

        return announced;
    }

    /**
     * 加入真心话。
     *
     * @param data 消息数据
     * @param value 投掷的值
     * @param time 投掷的时间
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    public CompletionStage<Void> join(ITeaNekoMessageData data, int value, Instant time) {
        var scopeId = data.getScopeId();
        var groupData = truthGroupDataMap.get(scopeId);
        // 判断真心话是否正在运行
        if (groupData == null) {
            return data.getMessageSender()
                    .sendAtReplyMessageAsync("真心话还没开始喵！");
        }
        // 判断用户是否已经加入
        var userId = data.getUserData().getUuid();
        if (groupData.userDataMap.putIfAbsent(userId,
                new TruthUserData(data.getUserData().getUserIdInPlatform(), value, time)) == null) {
            return data.getMessageSender()
                    .sendAtReplyMessageAsync("投掷成功喵！点数是：" + value);
        } else {
            return data.getMessageSender()
                    .sendAtReplyMessageAsync("你已经投掷过了喵！");
        }
    }

    /**
     * 结束真心话。
     * <br>只有一种极端情况会导致错误：
     * <br>1. 第一轮手动结束时还没有 setEnd()，其他线程在自动结束进入到 end()
     * <br>2. 此时又手动开启了第二轮，上一轮的自动结束进入 end() 拿到了第二轮的数据进行结算。
     * <br>但基本上不可能出现这种超级极端情况，暂时不修该 bug。
     * 如果要修，建议在自动停止中检测是否是同一 TruthGroupData 实例，如果不是则不执行结束操作。
     *
     * @param data 消息数据。如果是自动结束，则为 null。
     * @param scopeId 本次操作所属作用域
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    public CompletionStage<Void> end(ITeaNekoMessageData data, String scopeId) {
        // 检测游戏状态
        var groupData = truthGroupDataMap.remove(scopeId);
        if(groupData == null) {
            // 如果是自动结束，则不需要发送消息；如果是手动结束，则需要发送消息
            if(data != null) {
                return data.getMessageSender()
                        .sendTextMessageAsync("真心话还没开始喵！");
            }
            return CompletableFuture.completedStage(null);
        }
        groupData.setEnd();
        // 获取数据
        var userDataMap = groupData.userDataMap;
        if (userDataMap.size() < 2) {
            return groupData.getMessageSender().sendTextMessageAsync("真心话人数不足喵！本次真心话取消喵！");
        }

        // 转化成 list 并进行排序
        var sortedUserList = userDataMap.values().stream().sorted(TruthUserData::compareTo).toList();

        // 获取第一个数值最大的用户
        var maxData = sortedUserList.getLast();

        // 获取最后一个数值最小的用户
        var minData = sortedUserList.getFirst();

        // 发送消息
        return groupData.getMessageSender()
                .addAtMessage(String.valueOf(maxData.userID))
                .addTextMessage(" 喵\n    问\n")
                .addAtMessage(String.valueOf(minData.userID))
                .addTextMessage(" 喵！")
                .sendAsync();
    }

    /**
     * 一个用户的真心话数据
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    @AllArgsConstructor
    private static class TruthUserData implements Comparable<TruthUserData> {
        /// 用户ID
        private final String userID;

        /// 用户数据
        private final int value;

        /// 时间
        private final Instant time;

        /**
         * 按真心话轮次的既有排序字段比较参与者，使展示与业务选择保持一致。
         *
         * @param o 用于排序比较的另一条业务数据
         * @return 按当前输入与状态计算的数值
         */
        @Override
        public int compareTo(TruthUserData o) {
            if (value == o.value) {
                return o.time.compareTo(time);
            }
            return Integer.compare(value, o.value);
        }
    }

    /**
     * 群组的真心话数据。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private static class TruthGroupData {
        /// 用户数据列表
        private final Map<UUID, TruthUserData> userDataMap = new ConcurrentHashMap<>();

        /// 开始时的 data
        private final ITeaNekoMessageData data;

        /// 该数据是否已经结束
        private final AtomicBoolean isEnded = new AtomicBoolean(false);

        /**
         * 固定当前真心话轮次的原群消息来源，供参与者与结束通知使用。
         *
         * @param data 本次处理的输入数据
         */
        private TruthGroupData(ITeaNekoMessageData data) {
            this.data = data;
        }

        /**
         * 原子性地设置 end
         *
         * @return boolean 如果成功设置为 end，返回 true；如果已经是 end，返回 false。
         */
        public boolean setEnd() {
            return isEnded.compareAndSet(false, true);
        }

        /**
         * 发送消息
         *
         * @return 当前构建器，供调用方继续组合后提交
         */
        public IEasyMessageSenderBuilder getMessageSender() {
            return data.getMessageSender();
        }
    }
}
