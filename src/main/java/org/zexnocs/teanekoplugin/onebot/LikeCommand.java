package org.zexnocs.teanekoplugin.onebot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotMessageData;
import org.zexnocs.teanekoclient.onebot.sender.private_.LikeSender;
import org.zexnocs.teanekocore.actuator.task.EmptyTaskResult;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerService;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.database.easydata.cleanable.CleanableEasyData;
import org.zexnocs.teanekocore.database.easydata.general.GeneralEasyData;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.ChinaDateUtil;

import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 点赞指令，允许用户给自己的资料卡点赞，并且可以选择加入到每天凌晨1点的定时点赞队列中。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.1.0
 * @version 4.5.0-l
 */
@Description("""
        让机器人给你个人资料卡点赞。
        可以选择手动点赞，也可以选择加入到每天凌晨1点的定时点赞队列中。
        点赞每天凌晨1点刷新。""")
@Command(value = {"/like", "like"},
        scope = CommandScope.ALL,
        permission = CommandPermission.ALL,
        supportedClients = {OnebotTeaNekoClient.class})
public class LikeCommand {
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
    private final ILogger logger;

    /**
     * 装配点赞发送与定时服务，当前点赞动作等待实际回执，未来队列任务独立运行。
     *
     * @param likeSender 由装配方提供的 {@code LikeSender} 协作依赖
     * @param iTimerService 本次装配使用的 {@code ITimerService} 服务
     * @param logger 项目日志接口
     */
    @Autowired
    public LikeCommand(LikeSender likeSender,
                       ITimerService iTimerService, ILogger logger) {
        this.likeSender = likeSender;
        this.iTimerService = iTimerService;
        this.logger = logger;
    }

    /**
     * 在应用程序准备就绪后执行初始化操作。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        init();
    }

    /**
     * 初始化方法，用于从数据库中加载用户ID列表并设置定时器。
     */
    public void init() {
        var dto = GeneralEasyData.of(DATABASE_NAMESPACE).get(DATABASE_LIST_TARGET);
        List<String> userIdList = dto.get(DATABASE_LIST_KEY, new ArrayList<>());
        // 将用户ID转换为Long类型并添加到队列
        userIdList.stream()
                .map(userId -> {
                    try {
                        return Long.parseLong(userId);
                    } catch (NumberFormatException e) {
                        logger.errorWithReport("LikeCommand", "用户ID转换错误: " + userId, e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .forEach(userIdQueue::add);
        // 注册一个定时器，每天凌晨1点执行一次点赞任务
        iTimerService.registerByCron(
                "定时点赞任务",
                "auto-like",
                this::processLike,
                "0 0 1 * * ?",
                EmptyTaskResult.getResultType());
    }

    /**
     * 处理点赞请求的方法。
     * 包括点赞和将点赞记录存储到数据库。
     *
     * @return 满足本方法约束的 {@code EmptyTaskResult} 结果
     */
    public EmptyTaskResult processLike() {
        // 清理已经点赞过的用户ID集合
        likedUserIds.clear();
        // 获得一个copy的队列
        var copy = new ArrayList<>(userIdQueue);
        // 遍历队列中的用户ID
        for (long userId : copy) {
            // 进行点赞
            likeSender.like(userId, 10);
            likedUserIds.add(userId);
        }
        // 将点赞成功记录到数据库
        var date = ChinaDateUtil.Instance.getNowDateString();
        var dto = CleanableEasyData.of(DATABASE_NAMESPACE).get(DATABASE_LIST_TARGET);
        var task = dto.getTaskConfig("点赞记录");
        task.set(DATABASE_LIST_RECORD_KEY + date, copy);
        task.push();
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
        return task.pushAsync().thenCompose(ignored -> {
            userIdQueue.add(userId);
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
        return task.pushAsync().thenCompose(ignored -> {
            userIdQueue.remove(userId);
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
        return task.pushAsync().thenCompose(ignored -> {
            userIdQueue.add(userId);
            return data.getMessageSender()
                .sendAtReplyMessageAsync("添加成功了喵！");
        });
    }
}
