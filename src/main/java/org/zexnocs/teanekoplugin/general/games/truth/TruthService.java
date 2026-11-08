package org.zexnocs.teanekoplugin.general.games.truth;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginAccess;
import org.zexnocs.teanekocore.plugin.IPluginLifecycle;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ExecutionDelays;
import org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException;
import org.zexnocs.teanekoplugin.support.PluginTaskScope;
import org.jspecify.annotations.Nullable;

import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.sender.api.sender_box.IEasyMessageSenderBuilder;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekoplugin.general.games.dice.DiceService;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 真心话服务类，负责管理真心话的状态和数据。
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 * <br>2026/11/07：自动结束由插件拥有可取消等待，停止后不补发旧轮次通知。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.1.0
 * @version 4.9.0-l
 * @see DiceService
 */
@Service("truthService")
@PluginOwned("general.games")
public class TruthService implements IPluginLifecycle {
    private final DiceService diceService;
    private final ILogger logger;
    private final ScheduledExecutorService scheduler;
    private final PluginAccess plugins;
    private final Set<ExecutionControl> delays = ConcurrentHashMap.newKeySet();
    private @Nullable PluginTaskScope lifecycle;
    private CompletionStage<Void> stopping = CompletableFuture.completedStage(null);

    /// scope id → 群组数据
    private final Map<String, TruthGroupData> truthGroupDataMap = new ConcurrentHashMap<>();

    /**
     * 装配任务、骰子和诊断服务；当前命令完成不等待未来独立定时回调。
     *
     * @param scheduler 宿主借用调度器，不由本服务关闭
     * @param diceService 由装配方提供的 {@code DiceService} 协作依赖
     * @param logger 项目日志接口
     * @param plugins 插件代次入口
     */
    @Autowired
    public TruthService(@Qualifier("taskExecutor") ScheduledExecutorService scheduler,
                        DiceService diceService, ILogger logger, PluginAccess plugins) {
        this.diceService = diceService;
        this.logger = logger;
        this.scheduler = scheduler;
        this.plugins = plugins;
    }

    /** @return 2026/11/07：游戏插件的稳定标识。 */
    @Override
    public String pluginId() {
        return "general.games";
    }

    /** @return 2026/11/07：真心话独立倒计时资源标识。 */
    @Override
    public String id() {
        return "truth-rounds";
    }

    /** @return 2026/11/07：上一代排空后开放新的倒计时，不补发停止期间到期的通知。 */
    @Override
    public synchronized CompletionStage<Void> start() {
        if (!stopping.toCompletableFuture().isDone()) {
            return CompletableFuture.failedStage(new IllegalStateException("真心话上一代工作仍在停止。"));
        }
        if (lifecycle == null) {
            lifecycle = new PluginTaskScope();
        }
        return CompletableFuture.completedStage(null);
    }

    /** @return 2026/11/07：取消倒计时并等待已提交发送的真实回执，保留原游戏参与数据。 */
    @Override
    public synchronized CompletionStage<Void> stop() {
        if (lifecycle == null) {
            return stopping;
        }
        stopping = lifecycle.stop();
        lifecycle = null;
        List.copyOf(delays).forEach(ExecutionControl::cancel);
        return stopping;
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
     * @throws IllegalArgumentException 当自动结束时间超出零至一天时
     * @throws IllegalStateException 当所属插件尚未启用或本次代次已失效时
     */
    public synchronized CompletionStage<Void> start(ITeaNekoMessageData data, long autoEndTime) {
        // 2026/11/07：新倒计时登记与停止互斥，外部时长按现有可取消等待合同限制。
        if (autoEndTime < 0 || autoEndTime > Duration.ofDays(1).toSeconds()) {
            throw new IllegalArgumentException("真心话自动结束时间必须在零至一天之间。");
        }
        var scope = lifecycle;
        if (scope == null) {
            throw new IllegalStateException("真心话插件尚未启用。");
        }
        var token = plugins.captureOwner(pluginId());
        // 获取是否已经存在真心话数据
        var scopeId = data.getScopeId();
        // 先创建一个新的 TruthGroupData
        var newGroupData = new TruthGroupData(data);
        if (truthGroupDataMap.putIfAbsent(scopeId, newGroupData) != null) {
            newGroupData.delay.close();
            plugins.check(token);
            return data.getMessageSender().sendReplyMessageAsync("真心话已经在进行中喵！");
        }
        try {
            var diceData = diceService.getDiceData(scopeId);
            String opening = """
                    真心话开始喵！
                    玩法：输入"/d"投掷骰子，第一个数值最大的问最后一个数值最小的喵。
                    真心话将会在 %d 秒后自动结束喵。
                    具体规则请看群公告喵。
                    当前骰子最大值：%d""".formatted(autoEndTime, diceData.getDiceMaxValue());
            plugins.check(token);
            var announced = data.getMessageSender().sendTextMessageAsync(opening);
            var control = newGroupData.delay;
            delays.add(control);
            scope.executeAsync(() -> ExecutionDelays.waitFor(control, scheduler,
                    Duration.ofSeconds(autoEndTime)).thenCompose(ignored -> {
                        scope.checkActive();
                        return finishRound(scopeId, newGroupData, token);
                    }).whenComplete((ignored, failure) -> finishDelay(control, failure)));
            return announced;
        } catch (RuntimeException failure) {
            truthGroupDataMap.remove(scopeId, newGroupData);
            newGroupData.delay.close();
            throw failure;
        }
    }

    /**
     * 2026/11/07：实际等待与发送结束后释放自有控制，预期取消不记为业务错误。
     * @param control 本轮拥有的等待控制
     * @param failure 实际失败或正常完成时为空
     */
    private void finishDelay(ExecutionControl control, @Nullable Throwable failure) {
        try {
            Throwable cause = failure;
            while (cause instanceof CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            if (cause != null && !(cause instanceof ExecutionStoppedException)
                    && !(cause instanceof CancellationException)) {
                logger.error("真心话", "自动结束未完成。", cause);
            }
        } finally {
            delays.remove(control);
            control.close();
        }
    }

    /**
     * 加入真心话。
     *
     * @param data 消息数据
     * @param value 投掷的值
     * @param time 投掷的时间
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     * @throws IllegalStateException 当所属插件已停用或本次代次已失效时
     */
    public CompletionStage<Void> join(ITeaNekoMessageData data, int value, Instant time) {
        // 2026/11/07：直接业务入口同样拒绝已关闭插件，外层命令负责在途调用排空。
        var token = plugins.captureOwner(pluginId());
        var scopeId = data.getScopeId();
        var groupData = truthGroupDataMap.get(scopeId);
        // 判断真心话是否正在运行
        if (groupData == null) {
            plugins.check(token);
            return data.getMessageSender()
                    .sendAtReplyMessageAsync("真心话还没开始喵！");
        }
        // 判断用户是否已经加入
        var userId = data.getUserData().getUuid();
        if (groupData.userDataMap.putIfAbsent(userId,
                new TruthUserData(data.getUserData().getUserIdInPlatform(), value, time)) == null) {
            plugins.check(token);
            return data.getMessageSender()
                    .sendAtReplyMessageAsync("投掷成功喵！点数是：" + value);
        } else {
            plugins.check(token);
            return data.getMessageSender()
                    .sendAtReplyMessageAsync("你已经投掷过了喵！");
        }
    }

    /**
     * 结束真心话。
     * <br>2026/11/07：以原轮次身份移除，旧自动回调不能结算手动结束后创建的新轮次。
     *
     * @param data 消息数据。如果是自动结束，则为 null。
     * @param scopeId 本次操作所属作用域
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     * @throws IllegalStateException 当所属插件已停用或本次代次已失效时
     */
    public CompletionStage<Void> end(@Nullable ITeaNekoMessageData data, String scopeId) {
        var token = plugins.captureOwner(pluginId());
        // 检测游戏状态
        var groupData = truthGroupDataMap.get(scopeId);
        if (groupData == null) {
            // 如果是自动结束，则不需要发送消息；如果是手动结束，则需要发送消息
            if (data != null) {
                plugins.check(token);
                return data.getMessageSender()
                        .sendTextMessageAsync("真心话还没开始喵！");
            }
            return CompletableFuture.completedStage(null);
        }
        return finishRound(scopeId, groupData, token);
    }

    /**
     * 2026/11/07：仅结算指定轮次，取消其剩余等待；已开始的发送返回真实阶段。
     * @param scopeId 原聊天作用域
     * @param groupData 本次固定轮次
     * @param token 开始操作时固定的插件代次
     * @return 本轮实际发送结束的阶段
     */
    private CompletionStage<Void> finishRound(String scopeId, TruthGroupData groupData, PluginRegistry.Token token) {
        plugins.check(token);
        if (!truthGroupDataMap.remove(scopeId, groupData)) {
            return CompletableFuture.completedStage(null);
        }
        groupData.delay.cancel();
        // 获取数据
        var userDataMap = groupData.userDataMap;
        if (userDataMap.size() < 2) {
            plugins.check(token);
            return groupData.getMessageSender().sendTextMessageAsync("真心话人数不足喵！本次真心话取消喵！");
        }

        // 转化成 list 并进行排序
        var sortedUserList = userDataMap.values().stream().sorted(TruthUserData::compareTo).toList();

        // 获取第一个数值最大的用户
        var maxData = sortedUserList.getLast();

        // 获取最后一个数值最小的用户
        var minData = sortedUserList.getFirst();

        // 发送消息
        plugins.check(token);
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
     * <br>2026/11/07：独立控制用于取消本轮等待，不让旧回调影响下一轮。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.9.0-l
     */
    private static class TruthGroupData {
        /// 用户数据列表
        private final Map<UUID, TruthUserData> userDataMap = new ConcurrentHashMap<>();

        /// 开始时的 data
        private final ITeaNekoMessageData data;

        // 2026/11/07：每轮拥有独立可取消等待，释放由实际自动结束阶段负责。
        private final ExecutionControl delay = ExecutionControl.create();

        /**
         * 固定当前真心话轮次的原群消息来源，供参与者与结束通知使用。
         *
         * @param data 本次处理的输入数据
         */
        private TruthGroupData(ITeaNekoMessageData data) {
            this.data = data;
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
