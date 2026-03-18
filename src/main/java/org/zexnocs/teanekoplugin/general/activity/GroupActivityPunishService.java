package org.zexnocs.teanekoplugin.general.activity;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessage;
import org.zexnocs.teanekocore.actuator.task.EmptyTaskResult;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerService;
import org.zexnocs.teanekocore.framework.pair.Pair;
import org.zexnocs.teanekoplugin.general.servant.GroupSeniorServantRule;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 惩罚地活跃度的服务。
 *
 * @author zExNocs
 * @date 2026/03/18
 * @since 4.3.4
 */
@Service
@RequiredArgsConstructor
public class GroupActivityPunishService {
    /// 惩罚的豁免时间。
    private final static long EXEMPTION_TIME = Duration.ofDays(7).toMillis();

    /// 惩罚的 cache，scopeID → (userID → punish value)
    @Getter
    private final Map<String, Map<String, PunishValue>> punishCache = new ConcurrentHashMap<>();

    /// 高级公务员
    private final GroupSeniorServantRule groupSeniorServantRule;
    private final ITimerService iTimerService;
    private final GroupActivityService groupActivityService;
    private final GroupActivityExemptionService groupActivityExemptionService;

    @PostConstruct
    public void init() {
        iTimerService.registerByRate("清理惩罚缓存",
                "group-activity-punish-service-cleaner",
                () -> {
                    punishCache.entrySet().removeIf(entry -> {
                        // 如果该 scope 没有任何低活跃度成员，则删除
                        var map = groupActivityService.getActivityDataMap().get(entry.getKey());
                        if(map == null || map.isEmpty()) {
                            return true;
                        }
                        entry.getValue().entrySet().removeIf(subEntry -> {
                            // 如果该用户不在地活跃度名单中，或者已经处理过了，则删除
                            var subValue = subEntry.getValue();
                            var subKey =  subEntry.getKey();
                            return subValue.isProcessed.get() || !map.containsKey(subKey);
                        });
                        return false;
                    });
                    return EmptyTaskResult.INSTANCE;
                },
                Duration.ofHours(1),
                EmptyTaskResult.getResultType());
    }


    /**
     * 尝试踢出惩罚用户
     *
     * @param activityConfigData   被惩罚用户所属 scope 的 activity config data
     * @param activityDataRulePair 被惩罚用户所触发的活跃度规则
     * @param client               用于获取操作器的 client
     * @param opScopeId            操作人的 scope id
     * @param opId                 操作人 id
     * @param scopeId              被惩罚用户的 scope id
     * @param groupId              被惩罚用户的 group id
     * @param userId               被惩罚用户的平台 id
     * @return 给管理群发送的提醒消息
     */
    public List<ITeaNekoMessage> kick(
            GroupActivityConfigData activityConfigData,
            Pair<GroupActivityData, GroupActivityRule> activityDataRulePair,
            ITeaNekoClient client,
            String opScopeId,
            String opId,
            String scopeId,
            String groupId,
            String userId) {
        var rule = activityDataRulePair.second();
        var punishMap = punishCache.computeIfAbsent(scopeId, ignored -> new ConcurrentHashMap<>());
        var punishValue = punishMap.computeIfAbsent(userId,
                user -> new PunishValue(rule.getExpressionString(),
                        activityConfigData.getKick(),
                        activityConfigData.getRemind()));
        var tools = client.getTeaNekoToolbox();
        var builder = tools.getMessageSenderTools().getMsgListBuilder();
        // 如果已经处理过，则不处理
        if (punishValue.isOped(opId)) {
            return builder.addTextMessage("您已经处理过该用户了喵!").build();
        }
        // 处理该用户
        if (punishValue.kick(opScopeId, opId)) {
            groupActivityService.getActivityDataMap().computeIfPresent(scopeId, (k, v) -> {
                v.remove(userId);
                return v;
            });
            punishMap.remove(userId);
            tools.getGroupKickSender().kick(groupId, userId);
            return builder.addTextMessage("将用户" + userId + "踢出群聊" + groupId + "成功喵").build();
        }
        return builder.addTextMessage("""
                        处理用户 %s 在群聊 %s 成功喵！
                        还需要 %d 人选择踢出该用户喵
                        还需要 %d 人选择提醒该用户喵"""
                        .formatted(userId, groupId, punishValue.getKickCount(), punishValue.getRemindCount()))
                .build();
    }

    /**
     * 直接提醒用户，并加入到免惩罚名单一周。
     * @param opId 操作人 id
     * @param groupId 群号
     * @param userId 用户号
     * @return 给管理群发送的提醒消息
     */
    public List<ITeaNekoMessage> remind(
            GroupActivityConfigData activityConfigData,
            Pair<GroupActivityData, GroupActivityRule> activityDataRulePair,
            ITeaNekoClient client,
            String opScopeId,
            String opId,
            String scopeId,
            String groupId,
            String userId) {
        var rule = activityDataRulePair.second();
        var punishMap = punishCache.computeIfAbsent(scopeId, ignored -> new ConcurrentHashMap<>());
        var punishValue = punishMap.computeIfAbsent(userId,
                user -> new PunishValue(rule.getExpressionString(),
                        activityConfigData.getKick(),
                        activityConfigData.getRemind()));
        var tools = client.getTeaNekoToolbox();
        var builder = tools.getMessageSenderTools().getMsgListBuilder();
        // 如果已经处理过，则不处理
        if (punishValue.isOped(opId)) {
            return builder.addTextMessage("您已经处理过该用户了喵!").build();
        }
        if(punishValue.remind(opScopeId, opId)) {
            groupActivityExemptionService.addWithFuture(scopeId, userId, EXEMPTION_TIME)
                    .thenAccept(ignored -> {
                        groupActivityService.getActivityDataMap().computeIfPresent(scopeId, (k, v) -> {
                            v.remove(userId);
                            return v;
                        });
                        punishMap.remove(userId);
                        tools.getMessageSenderTools().getGroupBuilder(groupId)
                                .addAtMessage(userId)
                                .addTextMessage("""
                             检测到你在群内活跃度过低，经过管理群决定对你进行提醒，并在一周后重新审核。
                            被检测低活跃度原因：%s
                            请在一周内多发言，避免被踢出群聊。""".formatted(punishValue.reason))
                                .send();
                    }).finish();
            return builder.addTextMessage("将用户" + userId + "提醒成功喵").build();
        }
        return builder.addTextMessage("""
                        处理用户 %s 在群聊 %s 成功喵！
                        还需要 %d 人选择踢出该用户喵
                        还需要 %d 人选择提醒该用户喵"""
                        .formatted(userId, groupId, punishValue.getKickCount(), punishValue.getRemindCount()))
                .build();
    }

    /**
     * 惩罚值
     *
     * @author zExNocs
     */
    @RequiredArgsConstructor
    public class PunishValue {
        /// 原因
        private final String reason;

        /// 最大 kick 人数
        private final int maxKickCount;

        /// 最大 remind 人数
        private final int maxRemindCount;

        /// 选择 kick 的人数
        private final AtomicInteger kickCount = new AtomicInteger(0);

        /// 选择 remind 的人数
        private final AtomicInteger remindCount = new AtomicInteger(0);

        /// 是否正在处理
        private final AtomicBoolean isProcessed = new AtomicBoolean(false);

        /// 操作人员
        private final Set<String> opMap = ConcurrentHashMap.newKeySet();

        /**
         * 判断是否已经操作过。
         *
         * @param opId 操作人 id
         * @return 是否操作过
         */
        public boolean isOped(String opId) {
            return opMap.contains(opId);
        }

        /**
         * 获取还剩下多少人同意踢出。
         * @return 剩下多少人同意踢出
         */
        public int getKickCount() {
            return maxKickCount - kickCount.get();
        }

        /**
         * 获取还剩下多少人同意提醒。
         * @return 剩下多少人同意提醒
         */
        public int getRemindCount() {
            return maxRemindCount - remindCount.get();
        }

        /**
         * 同意踢出群聊。
         *
         * @param scopeId 操作人所属的 scope ID
         * @param opId    操作人 id
         * @return 是否踢出达到条件
         */
        public boolean kick(String scopeId, String opId) {
            // 已经结束了
            if(isProcessed.get()) {
                return false;
            }

            // 已经操作过了
            if(!opMap.add(opId)) {
                // 已经操作过了
                return false;
            }

            // 如果是管理员
            if(groupSeniorServantRule.isAdmin(scopeId, opId)) {
                return isProcessed.compareAndSet(false, true);
            }

            // 如果达到踢出条件
            if(kickCount.incrementAndGet() == maxKickCount) {
                return isProcessed.compareAndSet(false, true);
            }

            return false;
        }

        /**
         * 同意提醒用户。
         *
         * @param scopeId 操作人所属的 scope ID
         * @param opId    操作人 id
         * @return 是否提醒达到条件
         */
        public boolean remind(String scopeId, String opId) {
            // 已经结束了
            if(isProcessed.get()) {
                return false;
            }

            // 已经操作过了
            if(!opMap.add(opId)) {
                // 已经操作过了
                return false;
            }

            // 如果是管理员
            if(groupSeniorServantRule.isAdmin(scopeId, opId)) {
                return isProcessed.compareAndSet(false, true);
            }

            // 如果达到提醒条件
            if(remindCount.incrementAndGet() == maxRemindCount) {
                return isProcessed.compareAndSet(false, true);
            }

            return false;
        }
    }
}
