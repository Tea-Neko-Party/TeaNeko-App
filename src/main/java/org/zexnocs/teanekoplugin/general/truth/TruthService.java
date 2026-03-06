package org.zexnocs.teanekoplugin.general.truth;

import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.sender.api.sender_box.IEasyMessageSenderBuilder;
import org.zexnocs.teanekocore.actuator.task.EmptyTaskResult;
import org.zexnocs.teanekocore.actuator.task.TaskConfig;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskService;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekoplugin.general.dice.DiceService;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 真心话服务类，负责管理真心话的状态和数据。
 *
 * @see DiceService
 * @author zExNocs
 * @date 2026/03/06
 * @since 4.1.0
 */
@Service("truthService")
public class TruthService {
    private final DiceService diceService;
    private final ITaskService taskService;

    /// scope id → 群组数据
    private final Map<String, TruthGroupData> truthGroupDataMap = new ConcurrentHashMap<>();

    @Autowired
    public TruthService(ITaskService taskService,
                        DiceService diceService) {
        this.diceService = diceService;
        this.taskService = taskService;
    }

    /**
     * 判断真心话是否正在运行。
     *
     * @param scopeId scope ID
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
     */
    public void start(ITeaNekoMessageData data, long autoEndTime) {
        // 获取是否已经存在真心话数据
        var scopeId = data.getScopeId();
        // 先创建一个新的 TruthGroupData
        var newGroupData = new TruthGroupData(data);
        if(truthGroupDataMap.putIfAbsent(scopeId, newGroupData) != null) {
            data.getMessageSender(CommandData.getCommandToken())
                    .sendReplyMessage("真心话已经在进行中喵！");
            return;
        }
        // 获取该群组的骰子数据
        var diceData = diceService.getDiceData(scopeId);
        String OPEN_TEXT = """
                真心话开始喵！
                玩法：输入"/d"投掷骰子，第一个数值最大的问最后一个数值最小的喵。
                真心话将会在 %d 秒后自动结束喵。
                具体规则请看群公告喵。
                当前骰子最大值：%d""".formatted(autoEndTime, diceData.getDiceMaxValue());
        data.getMessageSender(CommandData.getCommandToken())
                .sendTextMessage(OPEN_TEXT);
        // 创建一个自动停止的定时器
        var taskConfig = TaskConfig.<Void>builder()
                .name("结束 %s 真心话".formatted(scopeId))
                .delayDuration(Duration.ofSeconds(autoEndTime))
                .callable(() -> {
                    // 防止结束新的真心话时，旧的定时器还在执行结束操作
                    if(newGroupData.setEnd()) {
                        end(null, scopeId);
                    }
                    return EmptyTaskResult.INSTANCE;
                })
                .build();

        taskService.subscribe(taskConfig, EmptyTaskResult.getResultType());
    }

    /**
     * 加入真心话。
     *
     * @param data 消息数据
     * @param value 投掷的值
     * @param time 投掷的时间
     */
    public void join(ITeaNekoMessageData data, int value, long time) {
        var scopeId = data.getScopeId();
        var groupData = truthGroupDataMap.get(scopeId);
        // 判断真心话是否正在运行
        if (groupData == null) {
            data.getMessageSender(CommandData.getCommandToken())
                    .sendAtReplyMessage("真心话还没开始喵！");
            return;
        }
        // 判断用户是否已经加入
        var userId = data.getUserData().getUuid();
        if (groupData.userDataMap.putIfAbsent(userId,
                new TruthUserData(data.getUserData().getUserIdInPlatform(), value, time)) == null) {
            groupData.getMessageSender().sendAtReplyMessage("投掷成功喵！点数是：" + value);
        } else {
            groupData.getMessageSender().sendAtReplyMessage("你已经投掷过了喵！");
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
     * @param scopeId scope ID
     */
    public void end(ITeaNekoMessageData data, String scopeId) {
        // 检测游戏状态
        var groupData = truthGroupDataMap.remove(scopeId);
        if(groupData == null) {
            // 如果是自动结束，则不需要发送消息；如果是手动结束，则需要发送消息
            if(data != null) {
                data.getMessageSender(CommandData.getCommandToken())
                        .sendTextMessage("真心话还没开始喵！");
            }
            return;
        }
        groupData.setEnd();
        // 获取数据
        var userDataMap = groupData.userDataMap;
        if (userDataMap.size() < 2) {
            groupData.getMessageSender().sendTextMessage("真心话人数不足喵！本次真心话取消喵！");
            return;
        }

        // 转化成 list 并进行排序
        var sortedUserList = userDataMap.values().stream().sorted(TruthUserData::compareTo).toList();

        // 获取第一个数值最大的用户
        var maxData = sortedUserList.getLast();

        // 获取最后一个数值最小的用户
        var minData = sortedUserList.getFirst();

        // 发送消息
        groupData.getMessageSender()
                .addAtMessage(String.valueOf(maxData.userID))
                .addTextMessage(" 喵\n    问\n")
                .addAtMessage(String.valueOf(minData.userID))
                .addTextMessage(" 喵！")
                .send();
    }

    /**
     * 一个用户的真心话数据
     */
    @AllArgsConstructor
    private static class TruthUserData implements Comparable<TruthUserData> {
        /// 用户ID
        private final String userID;

        /// 用户数据
        private final int value;

        /// 时间
        private final long time;

        @Override
        public int compareTo(TruthUserData o) {
            if (value == o.value) {
                return Long.compare(o.time, time);
            }
            return Long.compare(value, o.value);
        }
    }

    /**
     * 群组的真心话数据。
     */
    private static class TruthGroupData {
        /// 用户数据列表
        private final Map<UUID, TruthUserData> userDataMap = new ConcurrentHashMap<>();

        /// 开始时的 data
        private final ITeaNekoMessageData data;

        /// 该数据是否已经结束
        private final AtomicBoolean isEnded = new AtomicBoolean(false);

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

        /// 发送消息
        public IEasyMessageSenderBuilder getMessageSender() {
            return data.getMessageSender(CommandData.getCommandToken());
        }
    }
}
