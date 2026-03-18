package org.zexnocs.teanekoplugin.general.activity;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.TriConsumer;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.utils.TeaNekoScopeService;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.database.configdata.exception.ConfigDataNotFoundException;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekoplugin.general.MainGroupConfigManager;

import java.time.Duration;

/**
 * 群活跃度指令
 *
 * @author zExNocs
 * @date 2026/03/18
 * @since 4.3.4
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

    @Description("""
            以转发的形式查看当前群成员的活跃度具体信息。
            规格：/ga <群ID>。
            群 ID 可选，默认查询配置中的第一个群。""")
    @DefaultCommand
    public void allInfo(CommandData<ITeaNekoMessageData> commandData,
                        @DefaultValue("") String groupId) {
        var data = commandData.getRawData();
        withValidGroupContext(data, groupId, (finalGroupId, targetScopeId, config) -> {
            var map = groupActivityService.getActivityDataMap().get(targetScopeId);
            var allDatils = groupActivityQueryService
                    .getAllDetail(data.getClient(), groupId, map);
            data.getForwardMessageSender().addBotAllList(allDatils)
                    .sendByPart(8);
        });
    }

    @Description("""
            扫描被监控群的所有成员的活跃度。是线程安全的。
            使用方法：/scan <?groupId>
            如果没有 <groupId>，则使用默认的主群
            扫描前提是当前群处于扫描群的监控列表中""")
    @SubCommand(value = {"scan", "扫描"}, permission = CommandPermission.ADMIN)
    public void scan(CommandData<ITeaNekoMessageData> commandData, @DefaultValue("") String groupId) {
        var data = commandData.getRawData();
        withValidGroupContext(data, groupId, (finalGroupId, targetScopeId, config) -> {
            try {
                var future = groupActivityService.scanWithFuture(targetScopeId);
                if(future != null) {
                    future.finish();
                }
            } catch (ConfigDataNotFoundException e) {
                // 不可能发生
                data.getMessageSender().sendReplyMessage("发生了未知错误喵！");
                throw new RuntimeException(e);
            }
        });
    }

    @Description("""
            管理员指令。将用户添加到扫描白名单。
            规格：/ga add-white <用户ID> <群ID?> <天数?>
            群ID可选，默认查询主群。
            天数可选，默认 30 天；不可超过 90 天。
            优先使用默认群ID。也就是说如果参数只有两个，第二个参数表示天数。""")
    @SubCommand(value = {"add-white", "添加白名单"}, permission = CommandPermission.ADMIN)
    public void addWhite(CommandData<ITeaNekoMessageData> commandData,
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
        withValidGroupContext(commandData.getRawData(), groupId, (finalGroupId, targetScopeId, config) ->
                groupActivityExemptionService.addWithFuture(targetScopeId, userId, interval)
                .thenAccept(ignored -> {
                    groupActivityService.getActivityDataMap().computeIfPresent(targetScopeId, (k, v) -> {
                        v.remove(userId);
                        return v;
                    });
                    groupActivityPunishService.getPunishCache().computeIfPresent(targetScopeId, (k, v) -> {
                        v.remove(userId);
                        return v;
                    });
                    data.getMessageSender().sendReplyMessage("""
                    已经将用户 %s 添加到群 %s 的白名单中。
                    时间：%s 天""".formatted(
                            userId,
                            finalGroupId,
                            Duration.ofMillis(interval).toDays()));
                }).finish());
    }

    @Description("""
            以踢出的方式惩罚活跃度不足的用户。
            规格：/ga 踢出 <群ID?> <用户ID>
            群ID可选，默认查询配置中的主群。""")
    @SubCommand(value = {"kick", "踢出"})
    public void kick(CommandData<ITeaNekoMessageData> commandData,
                     @DefaultValue("") String groupId,
                     String userId) {
        var data = commandData.getRawData();
        withValidGroupContext(data, groupId, (finalGroupId, targetScopeId, config) -> {
            var map = groupActivityService.getActivityDataMap().get(targetScopeId);
            if(map == null) {
                data.getMessageSender().sendTextMessage("该用户不属于低活跃度用户喵");
                return;
            }
            var pair = map.get(userId);
            if(pair == null) {
                data.getMessageSender().sendTextMessage("该用户不属于低活跃度用户喵");
                return;
            }
            var messageList = groupActivityPunishService.kick(
                    config,
                    pair,
                    data.getClient(),
                    data.getScopeId(),
                    data.getUserData().getUserIdInPlatform(),
                    targetScopeId,
                    finalGroupId,
                    userId);
            data.getMessageSender().addMessages(messageList).send();
        });
    }

    @Description("""
            以提醒的方式惩罚活跃度不足的用户。
            规格：/ga 提醒 <群ID?> <用户ID>
            群ID可选，默认查询配置中的第一个群。""")
    @SubCommand(value = {"remind", "提醒"})
    public void remind(CommandData<ITeaNekoMessageData> commandData,
                       @DefaultValue("") String groupId,
                       String userId) {
        var data = commandData.getRawData();
        withValidGroupContext(data, groupId, (finalGroupId, targetScopeId, config) -> {
            var map = groupActivityService.getActivityDataMap().get(targetScopeId);
            if(map == null) {
                data.getMessageSender().sendTextMessage("该用户不属于低活跃度用户喵");
                return;
            }
            var pair = map.get(userId);
            if(pair == null) {
                data.getMessageSender().sendTextMessage("该用户不属于低活跃度用户喵");
                return;
            }
            var messageList = groupActivityPunishService.remind(
                    config,
                    pair,
                    data.getClient(),
                    data.getScopeId(),
                    data.getUserData().getUserIdInPlatform(),
                    targetScopeId,
                    finalGroupId,
                    userId);
            data.getMessageSender().addMessages(messageList).send();
        });
    }

    /**
     * 获取主群聊 ID。
     * <br>如果为 null，说明失败。
     */
    private String getMainGroup(ITeaNekoMessageData data) {
        // 尝试解析出默认主群
        var mainGroupConfig = mainGroupConfigManager.getConfig(data.getScopeId()).orElse(null);
        if(mainGroupConfig != null) {
            return mainGroupConfig.getValue();
        } else {
            data.getMessageSender().sendReplyMessage("未知群聊喵！");
            return null;
        }
    }

    /**
     * check config。
     * <br>如果失败返回 true
     * <br>否则返回 false
     */
    private boolean checkConfig(ITeaNekoMessageData data, GroupActivityConfigData config) {
        if(config == null) {
            data.getMessageSender().sendReplyMessage("该群并没有注册活跃度规则喵！");
            return true;
        }

        // 如果该群的监视规则里没有当前群
        if(!config.getGroups().contains(data.getUserData().getGroupId())) {
            data.getMessageSender().sendReplyMessage("不支持的群聊喵！");
            return true;
        }
        return false;
    }

    /**
     * 提取出的公共方法。
     *
     * @param data        message data
     * @param groupId     输入的 group ID，不是 final Group ID
     * @param consumer    验证成功后
     */
    private void withValidGroupContext(
            ITeaNekoMessageData data,
            String groupId,
            TriConsumer<String, String, GroupActivityConfigData> consumer
    ) {
        // 解析 group ID
        if (groupId.isBlank()) {
            groupId = getMainGroup(data);
            if (groupId == null) {
                return;
            }
        }
        // 解析 scopeId
        var targetScopeId = teaNekoScopeService.getGroupScopeId(data.getClient(), groupId);
        // 解析 config
        var config = iConfigDataService
                .getConfigData(groupActivityService, GroupActivityConfigData.class, targetScopeId)
                .orElse(null);
        if (checkConfig(data, config)) {
            return;
        }
        consumer.accept(groupId, targetScopeId, config);
    }
}
