package org.zexnocs.teanekoplugin.onebot.common;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import java.util.concurrent.CompletableFuture;

import org.zexnocs.teanekoplugin.support.PluginTaskScope;

import org.zexnocs.teanekocore.plugin.IPluginLifecycle;

import org.jspecify.annotations.Nullable;

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginAccess;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotMessageData;
import org.zexnocs.teanekoclient.onebot.sender.private_.LikeSender;
import org.zexnocs.teanekocore.actuator.task.EmptyTaskResult;
import org.zexnocs.teanekocore.actuator.timer.TimerDisplay;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerService;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.database.easydata.cleanable.CleanableEasyData;
import org.zexnocs.teanekocore.database.easydata.general.GeneralEasyData;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.utils.ChinaDateUtil;

import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 点赞指令，允许用户给自己的资料卡点赞，并且可以选择加入到每天凌晨1点的定时点赞队列中。
 *
 * <br>2026/11/04：声明定时任务分类及网页控制边界。
 * <br>2026/11/04：补全定时任务的详细中文介绍，原调度和控制意图保持不变。
 *
 * <br>2026/11/04：增加中文指令显示名，供控制台目录与选择卡片使用。
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 *
 * <br>2026/11/07：异步副作用前复核固定插件代次，停用后拒绝迟到业务。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.1.0
 * @version 4.9.0-l
 */
@Description("""
        让机器人给你个人资料卡点赞。
        可以选择手动点赞，也可以选择加入到每天凌晨1点的定时点赞队列中。
        点赞每天凌晨1点刷新。""")
// 2026/11/04：显示名与稳定指令前缀分别维护。
@Command(displayName = "名片点赞", value = {"/like", "like"},
        scope = CommandScope.ALL,
        permission = CommandPermission.ALL,
        supportedClients = {OnebotTeaNekoClient.class})
// 2026/11/04：展示声明不改变原任务调度和生命周期。
// 2026/11/04：补充任务用途、执行边界与网页控制说明。
@TimerDisplay(category = "plugin.business", categoryName = "业务插件", name = "定时点赞",
        description = "每天按当前 cron 规则处理已登记的定时点赞用户列表，每位用户提交十次点赞。"
        + "执行时刷新当日去重集合，并记录本轮处理的用户名单；平台是否接受仍取决于连接状态和真实接口结果。"
        + "允许暂停或恢复后续调度，暂停不撤销已经发出的点赞请求；登记名单由原点赞功能维护。")
@PluginOwned("onebot.common")
public class LikeCommand implements IPluginLifecycle {
    // 2026/11/07：异步业务固定入口代次，停用后不启动后续副作用。
    private final PluginAccess plugins;
    // 2026/11/07：生命周期代次与已经入队的回调绑定，重新启用不会复活旧任务。
    private @Nullable PluginTaskScope lifecycle;

    /// 数据库相关常量
    public static final String DATABASE_NAMESPACE = "like_command";
    public static final String DATABASE_LIST_TARGET = "timed_like";
    public static final String DATABASE_LIST_KEY = "list";
    public static final String DATABASE_LIST_RECORD_KEY = "record_";

    private final LikeSender likeSender;

    /// 这个队列用于存储用户的点赞请求
    private final Queue<Long> userIdQueue = new ConcurrentLinkedQueue<>();

    /// 用于防止重复点赞的用户ID集合
    private final Set<Long> likedUserIds = ConcurrentHashMap.newKeySet();

    private final ITimerService iTimerService;

    /**
     * 装配点赞发送与定时服务，当前点赞动作等待实际回执，未来队列任务独立运行。
     *
     * @param likeSender 由装配方提供的 {@code LikeSender} 协作依赖
     * @param iTimerService 本次装配使用的 {@code ITimerService} 服务
     * <br>2026/11/07：生命周期失败由宿主统一报告，不保留未使用的日志依赖。
     * @param plugins 统一插件代次访问入口
     */
    @Autowired
    public LikeCommand(LikeSender likeSender,
                       ITimerService iTimerService, PluginAccess plugins) {
        this.plugins = plugins;
        this.likeSender = likeSender;
        this.iTimerService = iTimerService;
    }

    /**
     * 2026/11/07：从原持久队列恢复启用状态，不补跑停用期间错过的点赞。
     * @return 本代定时任务登记完成的阶段
     */
    @Override
    public synchronized CompletionStage<Void> start() {
        if (lifecycle != null) {
            return CompletableFuture.completedStage(null);
        }
        var scope = new PluginTaskScope();
        lifecycle = scope;
        var dto = GeneralEasyData.of(DATABASE_NAMESPACE).get(DATABASE_LIST_TARGET);
        List<String> userIdList = dto.get(DATABASE_LIST_KEY, new ArrayList<>());
        userIdQueue.clear();
        for (var userId : userIdList) {
            userIdQueue.add(Long.parseLong(userId));
        }
        scope.own(iTimerService.registerByCron("定时点赞任务", "auto-like",
                () -> scope.execute(() -> processLike(scope)), "0 0 1 * * ?", EmptyTaskResult.getResultType()));
        return CompletableFuture.completedStage(null);
    }

    /** @return 2026/11/07：点赞与常用 OneBot 业务共用插件归属。 */
    @Override
    public String pluginId() {
        return "onebot.common";
    }

    /** @return 2026/11/07：本插件的点赞调度标识。 */
    @Override
    public String id() {
        return "scheduled-likes";
    }

    /**
     * 2026/11/07：撤销本代新增提交，并等待已经开始的真实平台及数据库操作。
     * @return 后台任务实际结束的阶段
     */
    @Override
    public synchronized CompletionStage<Void> stop() {
        var scope = lifecycle;
        lifecycle = null;
        return scope == null ? CompletableFuture.completedStage(null) : scope.stop();
    }

    /**
     * 2026/11/07：顺序等待真实点赞回执，停止后不再提交后续用户的动作。
     * @param scope 本轮启用的生命周期
     * @return 全部点赞及记录提交完成后的任务结果
     */
    private EmptyTaskResult processLike(PluginTaskScope scope) {
        likedUserIds.clear();
        var copy = new ArrayList<>(userIdQueue);
        for (long userId : copy) {
            scope.checkActive();
            var sending = likeSender.like(userId, 10);
            var result = sending.physicalCompletion().thenCompose(ignored -> sending.asStage())
                    .toCompletableFuture().join();
            if (!result.isSuccess()) {
                throw new IllegalStateException("定时点赞未获得成功回执，剩余用户未继续处理。");
            }
            likedUserIds.add(userId);
        }
        scope.checkActive();
        var date = ChinaDateUtil.Instance.getNowDateString();
        var dto = CleanableEasyData.of(DATABASE_NAMESPACE).get(DATABASE_LIST_TARGET);
        dto.getTaskConfig("点赞记录").set(DATABASE_LIST_RECORD_KEY + date, copy)
                .pushAsync().toCompletableFuture().join();
        return EmptyTaskResult.INSTANCE;
    }

    /**
     * &quot;直接进行点赞；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param times 请求次数，实际允许范围仍由业务校验
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            "直接进行点赞。
            参数：1. 次数，默认 10 次。
            例如 "/like 20" 为自己点赞 20 次。""")
    @DefaultCommand
    public CompletionStage<Void> like(CommandData<OnebotMessageData> commandData,
                     @DefaultValue("10") int times) {
        // 2026/11/07：固定本次业务代次，异步恢复时必须复核。
        var pluginToken = plugins.captureOwner("onebot.common");
        var data = commandData.getRawData();
        var userId = data.getOnebotRawMessageData().getUserId();

        if(!likedUserIds.add(userId)) {
            return data.getMessageSender()
                    .sendAtReplyMessageAsync("你已经点赞过了喵！");
        }

        if(times < 1) {
            times = 1;
        } else if(times > 10) {
            times = 10;
        }
        return likeSender.like(userId, times)
                .thenCompose(result -> {
                    plugins.check(pluginToken);
                    if(result.isSuccess()) {
                        return data.getMessageSender()
                                        .sendAtReplyMessageAsync("点赞成功了喵！");
                    } else {
                        return data.getMessageSender()
                                .sendAtReplyMessageAsync("已经点赞过了喵！");
                    }
                }).asStage();
    }

    /**
     * 将用户添加到定时凌晨1点点赞队列；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param userId 原业务边界中的目标用户标识
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            管理员指令
            将用户添加到定时凌晨1点点赞队列。
            参数：1. 用户QQ号
            例如"/like add 123" 将 qq号为 123 用户添加到点赞队列。""")
    @SubCommand(value = "add", permission = CommandPermission.ADMIN)
    public CompletionStage<Void> addToQueue(CommandData<OnebotMessageData> commandData, long userId) {
        // 2026/11/07：固定本次业务代次，异步恢复时必须复核。
        var pluginToken = plugins.captureOwner("onebot.common");
        var data = commandData.getRawData();
        if(userIdQueue.contains(userId)) {
            return data.getMessageSender()
                    .sendAtReplyMessageAsync("该用户已经在队列中了喵！");
        }
        var dto = GeneralEasyData.of(DATABASE_NAMESPACE).get(DATABASE_LIST_TARGET);
        var list = dto.get(DATABASE_LIST_KEY, new ArrayList<String>());
        list.add(String.valueOf(userId));
        var task = dto.getTaskConfig("用户添加到点赞队列的数据库");
        task.set(DATABASE_LIST_KEY, list);
        return task.addTransactionTask(() -> plugins.check(pluginToken)).pushAsync().thenCompose(ignored -> {
            userIdQueue.add(userId);
            plugins.check(pluginToken);
            return data.getMessageSender()
                .sendAtReplyMessageAsync("添加成功了喵！");
        });
    }

    /**
     * 将用户从定时凌晨1点点赞队列中移除；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param userId 原业务边界中的目标用户标识
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            管理员指令
            将用户从定时凌晨1点点赞队列中移除。
            参数：1. 用户QQ号
            例如"/like remove 123" 将 qq号为 123 用户从点赞队列中移除。""")
    @SubCommand(value = "remove", permission = CommandPermission.ADMIN)
    public CompletionStage<Void> removeFromQueue(CommandData<OnebotMessageData> commandData, long userId) {
        // 2026/11/07：固定本次业务代次，异步恢复时必须复核。
        var pluginToken = plugins.captureOwner("onebot.common");
        var data = commandData.getRawData();
        if(!userIdQueue.contains(userId)) {
            return data.getMessageSender()
                    .sendAtReplyMessageAsync("该用户已经不在队列中了喵！");
        }
        var dto = GeneralEasyData.of(DATABASE_NAMESPACE).get(DATABASE_LIST_TARGET);
        var list = dto.get(DATABASE_LIST_KEY, new ArrayList<String>());
        list.remove(String.valueOf(userId));
        var task = dto.getTaskConfig("用户从点赞队列移除的数据库");
        task.set(DATABASE_LIST_KEY, list);
        return task.addTransactionTask(() -> plugins.check(pluginToken)).pushAsync().thenCompose(ignored -> {
            userIdQueue.remove(userId);
            plugins.check(pluginToken);
            return data.getMessageSender()
                .sendAtReplyMessageAsync("移除成功了喵！");
        });
    }

    /**
     * 用户自己可以添加到点赞队列中。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            将自己添加到定时凌晨1点点赞队列。
            例如"/like join" 将自己添加到点赞队列。""")
    @SubCommand(value = "join")
    public CompletionStage<Void> joinQueue(CommandData<OnebotMessageData> commandData) {
        // 2026/11/07：固定本次业务代次，异步恢复时必须复核。
        var pluginToken = plugins.captureOwner("onebot.common");
        var data = commandData.getRawData();
        long userId = data.getOnebotRawMessageData().getUserId();
        if(userIdQueue.contains(userId)) {
            return data.getMessageSender()
                    .sendAtReplyMessageAsync("你已经在队列中了喵！");
        }
        var dto = GeneralEasyData.of(DATABASE_NAMESPACE).get(DATABASE_LIST_TARGET);
        var list = dto.get(DATABASE_LIST_KEY, new ArrayList<String>());
        list.add(String.valueOf(userId));
        var task = dto.getTaskConfig("用户添加到点赞队列的数据库");
        task.set(DATABASE_LIST_KEY, list);
        return task.addTransactionTask(() -> plugins.check(pluginToken)).pushAsync().thenCompose(ignored -> {
            userIdQueue.add(userId);
            plugins.check(pluginToken);
            return data.getMessageSender()
                .sendAtReplyMessageAsync("添加成功了喵！");
        });
    }
}
