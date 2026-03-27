package org.zexnocs.teanekoplugin.general.activity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.client.tools.ITeaNekoToolbox;
import org.zexnocs.teanekoapp.config.TeaNekoConfigNamespaces;
import org.zexnocs.teanekoapp.response.api.IGroupMemberResponseData;
import org.zexnocs.teanekoapp.utils.TeaNekoScopeService;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.database.configdata.exception.ConfigDataNotFoundException;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.framework.pair.HashPair;
import org.zexnocs.teanekocore.framework.pair.Pair;
import org.zexnocs.teanekocore.logger.ILogger;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 群活跃度总调度器
 *
 * @author zExNocs
 * @date 2026/03/17
 * @since 4.3.4
 */
@RequiredArgsConstructor
@ConfigManager(
        value = "group-activity",
        description = """
                群活跃度检测。监控该群中成员活跃度情况，并发送到指定的监控群中。
                请在被监控的群中注册。""",
        configType = GroupActivityConfigData.class,
        namespaces = {TeaNekoConfigNamespaces.GROUP},
        fieldChecker = GroupActivityFieldChecker.class
)
public class GroupActivityService {
    private final TeaNekoScopeService teaNekoScopeService;
    private final ILogger logger;
    private final IConfigDataService iConfigDataService;
    private final GroupActivityExemptionService groupActivityExemptionService;

    /// scopeID → (userID → (活跃度数据, 违反的活跃度规则))
    @Getter
    private final Map<String, Map<String, Pair<GroupActivityData, GroupActivityRule>>> activityDataMap = new ConcurrentHashMap<>();

    /// expression → rule 缓存
    private final Map<String, GroupActivityRule> cache = new ConcurrentHashMap<>();

    /**
     * 线程安全的扫描指定群中所有成员活跃度，扫描完成后会将结果保存在 activityDataMap 中
     *
     * @param scopeId 区域 ID
     * @return {@link TaskFuture }<{@link ? }> 扫描完 future，包含 (userID → (活跃度数据, 违反的活跃度规则))
     * @throws ConfigDataNotFoundException 当未找到指定的ConfigData时抛出此异常。
     */
    @Nullable
    public TaskFuture<Map<String, Pair<GroupActivityData, GroupActivityRule>>> scanWithFuture(String scopeId) throws ConfigDataNotFoundException {
        long currentTimeMs = System.currentTimeMillis();
        var activityMap = activityDataMap.computeIfAbsent(scopeId, _ -> new ConcurrentHashMap<>());
        synchronized (activityMap) {
            return _scan(scopeId, currentTimeMs, activityMap);
        }
    }

    /**
     * 正式扫描方法
     *
     * @param scopeId       区域 ID
     * @return {@link TaskFuture }<{@link ? }> 扫描完 future
     * @throws ConfigDataNotFoundException 当未找到指定的ConfigData时抛出此异常。
     */
    @Nullable
    public TaskFuture<Map<String, Pair<GroupActivityData, GroupActivityRule>>> _scan(String scopeId,
                      long currentTimeMs,
                      Map<String, Pair<GroupActivityData, GroupActivityRule>> map)
            throws ConfigDataNotFoundException {
        map.clear();
        // 根据 scope ID 获取到 client
        final Pair<ITeaNekoClient, String> clientPair;
        try {
            clientPair = teaNekoScopeService.fromGroupScopeId(scopeId);
        } catch (IllegalArgumentException e) {
            logger.error(this.getClass().getName(), "解析失败的 scopeID" + scopeId, e);
            return null;
        }
        var client = clientPair.first();
        var groupId = clientPair.second();
        var tools = client.getTeaNekoToolbox();
        var config = iConfigDataService.getConfigData(this, GroupActivityConfigData.class, scopeId)
                .orElseThrow(() -> new ConfigDataNotFoundException("该 scope 未注册: %s".formatted(scopeId)));
        // 获取缓存 rules
        var rules = getRules(scopeId, config, tools);

        // 报告扫描开始
        for(var monitorGroupId : config.getGroups()) {
            tools.getMessageSenderTools().getGroupBuilder(monitorGroupId)
                    .sendTextMessage("开始扫描群 %s 中的成员活跃度...".formatted(groupId));
        }

        // 开始扫描
        return tools.getGroupMemberListSender().get(groupId)
                .thenAccept(list -> processList(list, scopeId, currentTimeMs, rules, map))
                .thenApply(_ -> {
                    for(var monitorGroupId : config.getGroups()) {
                        tools.getMessageSenderTools().getGroupBuilder(monitorGroupId)
                                .sendTextMessage("扫描完毕，扫描出 %d 低活跃度成员".formatted(map.size()));
                    }
                    return map;
                });
    }

    /**
     * 处理 list
     */
    private void processList(List<? extends IGroupMemberResponseData> list,
                             String scopeId,
                             long currentTimeMs,
                             List<GroupActivityRule> rules,
                             Map<String, Pair<GroupActivityData, GroupActivityRule>> map) {
        for (var member : list) {
            // 如果 member 在豁免名单中，则跳过
            if (groupActivityExemptionService.isExempted(scopeId, member.getUserId(), currentTimeMs)) {
                continue;
            }
            // 构造信息
            long joinTimeMs = member.getJoinTimeMs() == null ? 0 : member.getJoinTimeMs();
            long lastSpeakTimeMs = member.getLastSentTimeMs() == null ? 0 : member.getLastSentTimeMs();
            int joinTime = member.getJoinTimeMs() == null ? 0 :
                    Math.toIntExact(Duration.ofMillis(currentTimeMs - member.getJoinTimeMs()).toDays());
            int speak = member.getLastSentTimeMs() == null ? 0 :
                    Math.toIntExact(Duration.ofMillis(currentTimeMs - member.getLastSentTimeMs()).toDays());
            var data = GroupActivityData.builder()
                    .nickname(member.getNickname())
                    .card(member.getCard())
                    .join(joinTime)
                    .speak(speak)
                    .level(member.getLevel() == null ? 0 : member.getLevel())
                    .hasTitle(member.getTitle() != null)
                    .joinTimeMs(joinTimeMs)
                    .lastSpeakTimeMs(lastSpeakTimeMs)
                    .title(member.getTitle())
                    .build();
            // 开始检测
            for(var rule : rules) {
                if(rule.isValid(data)) {
                    map.put(member.getUserId(), HashPair.of(data, rule));
                    break;
                }
            }
        }
    }

    /**
     * 缓存所有的 rules；同时检测不合法的 rule 并删除
     *
     */
    private List<GroupActivityRule> getRules(String scopeId, GroupActivityConfigData config, ITeaNekoToolbox tools) {
        // 提前缓存所有的 rule
        List<GroupActivityRule> rules = new ArrayList<>();

        // 用于删除不符合的 rule
        List<Pair<Integer, String>> removedList = new ArrayList<>();
        var ruleStrList = config.getRules();
        for(int i = 0; i < ruleStrList.size(); i++) {
            var expression = ruleStrList.get(i);
            var rule = cache.computeIfAbsent(expression, exp -> {
                try {
                    return new GroupActivityRule(exp);
                } catch (Exception e) {
                    // 鲁棒性
                    return null;
                }
            });
            if(rule == null) {
                removedList.add(HashPair.of(i, expression));
                continue;
            }
            // 构造一个假 GroupActivityData 对象用于检测是否能够检测通过
            var fakeData = GroupActivityData.getFakeData();
            try {
                rule.isValid(fakeData);
                // 能够通过
                rules.add(rule);
            } catch (Exception e) {
                // 不能通过
                removedList.add(HashPair.of(i, expression));
            }
        }
        // 如果有不合法的，则删除
        if(!removedList.isEmpty()) {
            for(var pair : removedList) {
                try {
                    iConfigDataService.removeFromRuleConfigListFiled(
                            this.getClass().getAnnotation(ConfigManager.class),
                            scopeId,
                            "rules",
                            pair.first());
                } catch (NoSuchFieldException | IllegalAccessException | ConfigDataNotFoundException e) {
                    logger.errorWithReport(getClass().getSimpleName(),
                            "未知的错误", e);
                }
            }
            for(var monitorGroupId : config.getGroups()) {
                tools.getMessageSenderTools().getGroupBuilder(monitorGroupId)
                        .sendTextMessage("存在不合理的活跃度规则，已自动删除:\n%s".formatted(
                                removedList.stream().map(Pair::second).reduce((a, b) -> a + "\n" + b).orElse("")));
            }
        }
        return rules;
    }
}
