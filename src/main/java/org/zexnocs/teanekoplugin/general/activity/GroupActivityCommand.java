package org.zexnocs.teanekoplugin.general.activity;

import lombok.RequiredArgsConstructor;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.utils.TeaNekoScopeService;
import org.zexnocs.teanekocore.actuator.task.TaskStages;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.database.configdata.exception.ConfigDataNotFoundException;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekoplugin.general.MainGroupConfigManager;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 群活跃度指令
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.3.4
 * @version 4.5.0-l
 */
@Description("""
        管理群活跃度相关指令。
        只能在监控群里使用这些指令。""")
@Command(
        value = {"/ga", "/活跃度"},
        scope = CommandScope.GROUP,
        permission = CommandPermission.ALL
)
@RequiredArgsConstructor
public class GroupActivityCommand {
    private final GroupActivityService groupActivityService;
    private final MainGroupConfigManager mainGroupConfigManager;
    private final TeaNekoScopeService teaNekoScopeService;
    private final IConfigDataService iConfigDataService;
    private final GroupActivityExemptionService groupActivityExemptionService;
    private final GroupActivityPunishService groupActivityPunishService;
    private final GroupActivityQueryService groupActivityQueryService;

    /**
     * 以转发的形式查看当前群成员的活跃度具体信息；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param groupId 目标群组标识
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            以转发的形式查看当前群成员的活跃度具体信息。
            规格：/ga <群ID>。
            群 ID 可选，默认查询配置中的第一个群。""")
    @DefaultCommand
    public CompletionStage<Void> allInfo(CommandData<ITeaNekoMessageData> commandData,
                        @DefaultValue("") String groupId) {
        var data = commandData.getRawData();
        return withValidGroupContext(data, groupId, context -> {
            var map = groupActivityService.getActivityDataMap().get(context.scopeId());
            var details = groupActivityQueryService.getAllDetail(data.getClient(), context.groupId(), map);
            return data.getForwardMessageSender().addBotAllList(details).sendByPartAsync(8);
        });
    }

    /**
     * 扫描被监控群的所有成员的活跃度。是线程安全的；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param groupId 目标群组标识
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            扫描被监控群的所有成员的活跃度。是线程安全的。
            使用方法：/scan <?groupId>
            如果没有 <groupId>，则使用默认的主群
            扫描前提是当前群处于扫描群的监控列表中""")
    @SubCommand(value = {"scan", "扫描"}, permission = CommandPermission.ADMIN)
    public CompletionStage<Void> scan(CommandData<ITeaNekoMessageData> commandData, @DefaultValue("") String groupId) {
        var data = commandData.getRawData();
        return withValidGroupContext(data, groupId, context -> {
            try {
                var task = groupActivityService.scanWithFuture(context.scopeId());
                if (task == null) return CompletableFuture.failedStage(new IllegalStateException("无法解析被扫描区域"));
                return task.asStage().thenApply(ignored -> null);
            } catch (ConfigDataNotFoundException e) {
                return data.getMessageSender().sendReplyMessageAsync("发生了未知错误喵！")
                        .thenCompose(ignored -> CompletableFuture.failedStage(e));
            }
        });
    }

    /**
     * 规格：/ga add-white &lt;用户ID&gt; &lt;群ID?&gt; &lt;天数?&gt;；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param userId 原业务边界中的目标用户标识
     * @param groupId 目标群组标识
     * @param day 用于本次处理的 {@code day} 数据
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            管理员指令。将用户添加到扫描白名单。
            规格：/ga add-white <用户ID> <群ID?> <天数?>
            群ID可选，默认查询主群。
            天数可选，默认 30 天；不可超过 90 天。
            优先使用默认群ID。也就是说如果参数只有两个，第二个参数表示天数。""")
    @SubCommand(value = {"add-white", "添加白名单"}, permission = CommandPermission.ADMIN)
    public CompletionStage<Void> addWhite(CommandData<ITeaNekoMessageData> commandData,
                         String userId,
                         @DefaultValue("") String groupId,
                         @DefaultValue("-1") int day) {
        var data =  commandData.getRawData();
        // 计算 interval
        long interval;
        if(day == -1) {
            // 没有设置则默认 30 天
            interval = Duration.ofDays(30).toMillis();
        } else {
            // 有设置则使用设置的天数，但不超过 90 天
            interval = Math.min(
                    Duration.ofDays(day).toMillis(),
                    Duration.ofDays(90).toMillis()
            );
        }
        return withValidGroupContext(data, groupId, context ->
                TaskStages.successful(groupActivityExemptionService.addWithFuture(context.scopeId(), userId, Duration.ofMillis(interval)),
                        "群活跃度白名单").thenCompose(ignored -> {
                    groupActivityService.getActivityDataMap().computeIfPresent(context.scopeId(), (key, values) -> {
                        values.remove(userId);
                        return values;
                    });
                    groupActivityPunishService.getPunishCache().computeIfPresent(context.scopeId(), (key, values) -> {
                        values.remove(userId);
                        return values;
                    });
                    return data.getMessageSender().sendReplyMessageAsync("""
                            已经将用户 %s 添加到群 %s 的白名单中。
                            时间：%s 天""".formatted(userId, context.groupId(), Duration.ofMillis(interval).toDays()));
                }));
    }

    /**
     * 以踢出的方式惩罚活跃度不足的用户；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param groupId 目标群组标识
     * @param userId 原业务边界中的目标用户标识
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            以踢出的方式惩罚活跃度不足的用户。
            规格：/ga 踢出 <群ID?> <用户ID>
            群ID可选，默认查询配置中的主群。""")
    @SubCommand(value = {"kick", "踢出"})
    public CompletionStage<Void> kick(CommandData<ITeaNekoMessageData> commandData,
                     @DefaultValue("") String groupId,
                     String userId) {
        var data = commandData.getRawData();
        return withValidGroupContext(data, groupId, context -> {
            var map = groupActivityService.getActivityDataMap().get(context.scopeId());
            var pair = map == null ? null : map.get(userId);
            if (pair == null) return data.getMessageSender().sendTextMessageAsync("该用户不属于低活跃度用户喵");
            return groupActivityPunishService.kick(context.config(), pair, data.getClient(),
                    data.getScopeId(), data.getUserData().getUserIdInPlatform(), context.scopeId(), context.groupId(), userId)
                    .thenCompose(messages -> data.getMessageSender().addMessages(messages).sendAsync());
        });
    }

    /**
     * 以提醒的方式惩罚活跃度不足的用户；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param groupId 目标群组标识
     * @param userId 原业务边界中的目标用户标识
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            以提醒的方式惩罚活跃度不足的用户。
            规格：/ga 提醒 <群ID?> <用户ID>
            群ID可选，默认查询配置中的第一个群。""")
    @SubCommand(value = {"remind", "提醒"})
    public CompletionStage<Void> remind(CommandData<ITeaNekoMessageData> commandData,
                       @DefaultValue("") String groupId,
                       String userId) {
        var data = commandData.getRawData();
        return withValidGroupContext(data, groupId, context -> {
            var map = groupActivityService.getActivityDataMap().get(context.scopeId());
            var pair = map == null ? null : map.get(userId);
            if (pair == null) return data.getMessageSender().sendTextMessageAsync("该用户不属于低活跃度用户喵");
            return groupActivityPunishService.remind(context.config(), pair, data.getClient(),
                    data.getScopeId(), data.getUserData().getUserIdInPlatform(), context.scopeId(), context.groupId(), userId)
                    .thenCompose(messages -> data.getMessageSender().addMessages(messages).sendAsync());
        });
    }

    /**
     * 获取主群聊 ID。
     * <br>如果为 null，说明失败。
     *
     * @param groupId 目标群组标识
     * @param scopeId 本次操作所属作用域
     * @param config 用于本次处理的 {@code config} 数据
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record GroupContext(String groupId, String scopeId, GroupActivityConfigData config) {}

    /**
     * 解析并校验群上下文后调用异步业务，拒绝反馈也进入同一完成链。
     *
     * @param data 本次处理的输入数据
     * @param groupId 目标群组标识
     * @param callback 用于本次处理的 {@code callback} 数据
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    private CompletionStage<Void> withValidGroupContext(ITeaNekoMessageData data, String groupId,
            java.util.function.Function<GroupContext, CompletionStage<Void>> callback) {
        if (groupId.isBlank()) {
            var main = mainGroupConfigManager.getConfig(data.getScopeId()).orElse(null);
            if (main == null) return data.getMessageSender().sendReplyMessageAsync("未知群聊喵！");
            groupId = main.getValue();
        }
        var scopeId = teaNekoScopeService.getGroupScopeId(data.getClient(), groupId);
        var config = iConfigDataService.getConfigData(groupActivityService, GroupActivityConfigData.class, scopeId).orElse(null);
        if (config == null) return data.getMessageSender().sendReplyMessageAsync("该群并没有注册活跃度规则喵！");
        if (!config.getGroups().contains(data.getUserData().getGroupId())) {
            return data.getMessageSender().sendReplyMessageAsync("不支持的群聊喵！");
        }
        return callback.apply(new GroupContext(groupId, scopeId, config));
    }
}
