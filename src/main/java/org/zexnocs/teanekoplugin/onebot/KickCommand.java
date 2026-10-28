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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 群成员踢出命令。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.1.1
 * @version 4.5.0-l
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

    /**
     * 装配成员查询、踢出和缓存服务，保留待确认步骤以避免误操作。
     *
     * @param groupKickSender 由装配方提供的 {@code GroupKickSender} 协作依赖
     * @param groupMemberInfoSender 由装配方提供的 {@code GetGroupMemberInfoSender} 协作依赖
     * @param iCacheService 缓存注册与生命周期服务
     */
    @Autowired
    public KickCommand(GroupKickSender groupKickSender,
                       GetGroupMemberInfoSender groupMemberInfoSender,
                       ICacheService iCacheService) {
        this.groupKickSender = groupKickSender;
        this.groupMemberInfoSender = groupMemberInfoSender;
        this.kickDataMap = ConcurrentMapCacheContainer.of(iCacheService, Duration.ofMinutes(5));
    }

    /**
     * 将群成员踢出群聊。需要进行二次确认；完成信号覆盖实际业务及其反馈，仍由原命令链校验权限。
     *
     * @param commandData 含可信来源和操作者的命令数据
     * @param userId 原业务边界中的目标用户标识
     * @param reason 首次生效的终止原因
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    @Description("""
            将群成员踢出群聊。需要进行二次确认。
            格式：/kick <用户ID> <理由>
            理由可以为空，默认为空字符串。""")
    @DefaultCommand()
    public CompletionStage<Void> kick(CommandData<OnebotMessageData> commandData, long userId, @DefaultValue("") String reason) {
        var data = commandData.getRawData();
        var groupId = data.getOnebotRawMessageData().getGroupId();
        var groupData = kickDataMap.computeIfAbsent(groupId, ignored -> new ConcurrentHashMap<>());
        var pending = groupData.get(userId);
        if (pending == null) {
            return groupMemberInfoSender.get(String.valueOf(groupId), String.valueOf(userId)).asStage()
                    .thenCompose(user -> {
                        if (user == null) return data.getMessageSender().sendReplyMessageAsync("不存在用户 %d".formatted(userId));
                        if (user.getRole() != null && (user.getRole().equalsIgnoreCase("admin")
                                || user.getRole().equalsIgnoreCase("owner"))) {
                            return data.getMessageSender().sendReplyMessageAsync("用户 %d 是管理员，无法踢出。".formatted(userId));
                        }
                        groupData.put(userId, new KickData(reason));
                        return data.getMessageSender().sendReplyMessageAsync("""
                                请输入 /kick %d 确认踢出该成员：
                                理由：%s""".formatted(userId, reason));
                    });
        }
        return groupKickSender.kickAsync(groupId, userId).thenCompose(ignored -> {
            groupData.remove(userId, pending);
            if (pending.reason == null || pending.reason.isEmpty()) return CompletableFuture.completedStage(null);
            return data.getMessageSender().sendReplyMessageAsync("""
                    成功将用户 %d 踢出群聊。
                    理由：%s""".formatted(userId, pending.reason));
        });
    }

    /**
     * 保存待确认踢出操作的上下文，避免一次命令误操作直接生效。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    @AllArgsConstructor
    private static class KickData {
        private final String reason;
    }
}
