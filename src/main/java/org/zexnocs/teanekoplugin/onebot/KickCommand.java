package org.zexnocs.teanekoplugin.onebot;

import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotMessageData;
import org.zexnocs.teanekoclient.onebot.sender.group.GetGroupMemberInfoSender;
import org.zexnocs.teanekoclient.onebot.sender.group.GroupKickSender;
import org.zexnocs.teanekocore.cache.ConcurrentMapCacheContainer;
import org.zexnocs.teanekocore.cache.interfaces.ICacheService;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.framework.description.Description;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 群成员踢出命令。
 *
 * @author zExNocs
 * @date 2026/03/08
 * @since 4.1.1
 */
@Description("将群成员踢出群聊。")
@Command(value = {"/kick", "/踢出"},
        permission = CommandPermission.ADMIN,
        scope = CommandScope.GROUP,
        permissionPackage = "permission.group.kick",
        supportedClients = {OnebotTeaNekoClient.class})
public class KickCommand {
    // 群号 → 用户ID → 踢出数据
    private final ConcurrentMapCacheContainer<Long, Map<Long, KickData>> kickDataMap;
    private final GroupKickSender groupKickSender;
    private final GetGroupMemberInfoSender groupMemberInfoSender;

    @Autowired
    public KickCommand(GroupKickSender groupKickSender,
                       GetGroupMemberInfoSender groupMemberInfoSender,
                       ICacheService iCacheService) {
        this.groupKickSender = groupKickSender;
        this.groupMemberInfoSender = groupMemberInfoSender;
        this.kickDataMap = ConcurrentMapCacheContainer.of(iCacheService, Duration.ofMinutes(5).toMillis());
    }

    @Description("""
            将群成员踢出群聊。需要进行二次确认。
            格式：/kick <用户ID> <理由>
            理由可以为空，默认为空字符串。""")
    @DefaultCommand()
    public void kick(CommandData<OnebotMessageData> commandData, long userId, @DefaultValue("") String reason) {
        var data = commandData.getRawData();
        var onebotData = data.getOnebotRawMessageData();
        var groupId = onebotData.getGroupId();
        var groupData = kickDataMap.computeIfAbsent(
                groupId, k -> new ConcurrentHashMap<>());
        var kickData = groupData.get(userId);
        if(kickData == null) {
            groupMemberInfoSender.get(CommandData.getCommandToken(),
                    String.valueOf(groupId), String.valueOf(userId))
                    .thenAccept(userData -> {
                if(userData == null) {
                    data.getMessageSender(CommandData.getCommandToken())
                            .sendReplyMessage("不存在用户 %d".formatted(userId));
                    return;
                }
                // 用户是管理员
                if( (userData.getRole() != null) &&
                    (userData.getRole().equalsIgnoreCase("admin") ||
                     userData.getRole().equalsIgnoreCase("owner"))) {
                    data.getMessageSender(CommandData.getCommandToken())
                            .sendReplyMessage("用户 %d 是管理员，无法踢出。".formatted(userId));
                    return;
                }
                // 用户存在
                groupData.put(userId, new KickData(reason));
                data.getMessageSender(CommandData.getCommandToken())
                        .sendReplyMessage("""
                    请输入 /kick %d 确认提出该成员：
                    理由：%s""".formatted(userId, reason));
            }).finish();
            return;
        }
        // 如果已经存在踢出数据，则直接执行踢出操作
        groupKickSender.kick(CommandData.getCommandToken(), groupId, userId);
        if(kickData.reason != null && !kickData.reason.isEmpty()) {
            data.getMessageSender(CommandData.getCommandToken())
                    .sendReplyMessage("""
                    成功将用户 %d 踢出群聊。
                    理由：%s""".formatted(userId, kickData.reason));
        }
    }

    @AllArgsConstructor
    private static class KickData {
        private final String reason;
    }
}
