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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 群活跃度总调度器
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.3.4
 * @version 4.5.0-l
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
     * @return 等待扫描结束的 {@code TaskFuture<?>}，结果按用户标识关联活跃度数据与违反的规则
     * @throws ConfigDataNotFoundException 当未找到指定的ConfigData时抛出此异常。
     */
    @Nullable
    public TaskFuture<Map<String, Pair<GroupActivityData, GroupActivityRule>>> scanWithFuture(String scopeId) throws ConfigDataNotFoundException {
        var currentTime = Instant.now();
        var activityMap = activityDataMap.computeIfAbsent(scopeId, _ -> new ConcurrentHashMap<>());
        synchronized (activityMap) {
            return _scan(scopeId, currentTime, activityMap);
        }
    }

    /**
     * 正式扫描方法
     *
     * @param scopeId       区域 ID
     * @param currentTime 用于本次处理的 {@code currentTime} 数据
     * @param map 按当前操作语义定位的输入映射
     * @return 等待扫描结束的 {@code TaskFuture<?>}
     * @throws ConfigDataNotFoundException 当未找到指定的ConfigData时抛出此异常。
     */
    @Nullable
    public TaskFuture<Map<String, Pair<GroupActivityData, GroupActivityRule>>> _scan(String scopeId,
                      Instant currentTime,
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
        var stage = getRules(scopeId, config, tools).thenCompose(rules ->
                notifyGroups(tools, config, "开始扫描群 %s 中的成员活跃度...".formatted(groupId))
                        .thenCompose(ignored -> tools.getGroupMemberListSender().get(groupId).asStage())
                        .thenApply(members -> {
                            processList(members, scopeId, currentTime, rules, map);
                            return map;
                        })
                        .thenCompose(result -> notifyGroups(tools, config,
                                "扫描完毕，扫描出 %d 低活跃度成员".formatted(result.size())).thenApply(ignored -> result)));
        return TaskFuture.fromStage(logger, "群活跃度扫描", stage);
    }

    /**
     * 处理 list
     *
     * @param list 按原始顺序处理的内容列表
     * @param scopeId 本次操作所属作用域
     * @param currentTime 用于本次处理的 {@code currentTime} 数据
     * @param rules 用于本次处理的 {@code rules} 数据
     * @param map 按当前操作语义定位的输入映射
     */
    private void processList(List<? extends IGroupMemberResponseData> list,
                             String scopeId,
                             Instant currentTime,
                             List<GroupActivityRule> rules,
                             Map<String, Pair<GroupActivityData, GroupActivityRule>> map) {
        for (var member : list) {
            // 如果 member 在豁免名单中，则跳过
            if (groupActivityExemptionService.isExempted(scopeId, member.getUserId(), currentTime)) {
                continue;
            }
            // 构造信息
            var joinInstant = member.getJoinInstant() == null ? Instant.EPOCH : member.getJoinInstant();
            var lastSpeakInstant = member.getLastSentInstant() == null
                    ? Instant.EPOCH : member.getLastSentInstant();
            int joinTime = member.getJoinInstant() == null ? 0 :
                    Math.toIntExact(Duration.between(joinInstant, currentTime).toDays());
            int speak = member.getLastSentInstant() == null ? 0 :
                    Math.toIntExact(Duration.between(lastSpeakInstant, currentTime).toDays());
            var data = GroupActivityData.builder()
                    .nickname(member.getNickname())
                    .card(member.getCard())
                    .join(joinTime)
                    .speak(speak)
                    .level(member.getLevel() == null ? 0 : member.getLevel())
                    .hasTitle(member.getTitle() != null)
                    .joinTime(joinInstant)
                    .lastSpeakTime(lastSpeakInstant)
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
     * @param scopeId 本次操作所属作用域
     * @param config 用于本次处理的 {@code config} 数据
     * @param tools 用于本次处理的 {@code tools} 数据
     * @return 承载本次异步处理实际结果的可组合完成阶段
     */
    private CompletionStage<List<GroupActivityRule>> getRules(String scopeId, GroupActivityConfigData config, ITeaNekoToolbox tools) {
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
        CompletionStage<Void> cleanup = CompletableFuture.completedStage(null);
        // 每次提交后再按递减索引删除下一项，避免索引移动或配置覆盖。
        for (var pair : removedList.reversed()) {
            cleanup = cleanup.thenCompose(ignored -> {
                try {
                    return iConfigDataService.removeFromRuleConfigListFiledAsync(
                            getClass().getAnnotation(ConfigManager.class), scopeId, "rules", pair.first());
                } catch (NoSuchFieldException | IllegalAccessException | ConfigDataNotFoundException e) {
                    return CompletableFuture.failedStage(e);
                }
            });
        }
        if (!removedList.isEmpty()) {
            var invalid = removedList.stream().map(Pair::second).reduce((first, second) -> first + "\n" + second).orElse("");
            cleanup = cleanup.thenCompose(ignored -> notifyGroups(tools, config, "存在不合理的活跃度规则，已自动删除:\n" + invalid));
        }
        return cleanup.thenApply(ignored -> rules);
    }

    /**
     * 按配置通知目标群组，组合全部发送阶段以便命令等待通知结束。
     *
     * @param tools 用于本次处理的 {@code tools} 数据
     * @param config 用于本次处理的 {@code config} 数据
     * @param text 待处理的业务文本
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    private CompletionStage<Void> notifyGroups(ITeaNekoToolbox tools, GroupActivityConfigData config, String text) {
        CompletionStage<Void> stage = CompletableFuture.completedStage(null);
        for (var group : config.getGroups()) {
            stage = stage.thenCompose(ignored -> tools.getMessageSenderTools().getGroupBuilder(group).sendTextMessageAsync(text));
        }
        return stage;
    }
}
