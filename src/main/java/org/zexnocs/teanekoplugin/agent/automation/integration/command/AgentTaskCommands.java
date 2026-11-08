package org.zexnocs.teanekoplugin.agent.automation.integration.command;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.integration.config.AgentComponentAccess;
import org.zexnocs.teanekoagent.integration.message.NativeMessageSources;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ResourceBudget;
import org.zexnocs.teanekocore.utils.StableDigest;
import org.zexnocs.teanekoplugin.agent.automation.schedule.AgentScheduleService;
import org.zexnocs.teanekoplugin.agent.automation.schedule.AgentScheduledTask;
import org.zexnocs.teanekoplugin.agent.automation.schedule.ITaskIntent;
import org.zexnocs.teanekoplugin.agent.automation.schedule.NaturalTaskParser;
import org.zexnocs.teanekoplugin.agent.automation.schedule.TaskPresentation;
import org.zexnocs.teanekoplugin.agent.automation.schedule.adapter.AgentScheduleLifecycle;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。
// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 原生提醒任务入口，身份和幂等 ID 均由真实消息产生；模型不能覆盖目的地。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：未安装定时任务组件时不截获自然聊天为提醒。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * <br>2026/11/07：业务归属与通用底座按职责分离，保留持久身份及完成边界。
 *
 * <br>2026/11/08：自然任务入口只读取有效提醒组件，未装配时保留普通聊天。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@Service
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.automation")
public final class AgentTaskCommands implements IAgentTaskCommands {
    private org.zexnocs.teanekocore.plugin.PluginRegistry plugins;

    /**
     * 2026/11/07：自然语言捷径直接调用时也受插件启停与代次约束。
     * @param plugins 统一插件目录
     */
    @Autowired
    public void installPlugins(org.zexnocs.teanekocore.plugin.PluginRegistry plugins) {
        this.plugins = plugins;
    }
    private final AgentScheduleService schedules;
    private final AgentScheduleLifecycle lifecycle;
    private final Executor executor;
    private final ScheduledExecutorService scheduler;
    private final AgentComponentAccess components;

    /**
     * 2026/11/08：借用调度器及当前组件配置，构造不启动或恢复任务。
     * @param schedules 实际调度服务
     * @param lifecycle 启动边界
     * @param executor 借用业务执行器
     * @param scheduler 借用时限设施
     * @param components 当前有效组件参数
     */
    public AgentTaskCommands(AgentScheduleService schedules, AgentScheduleLifecycle lifecycle,
            @Qualifier("virtualExecutor") Executor executor, @Qualifier("taskExecutor") ScheduledExecutorService scheduler,
            AgentComponentAccess components) {
        this.schedules = schedules;
        this.lifecycle = lifecycle;
        this.executor = executor;
        this.scheduler = scheduler;
        this.components = components;
    }

    /**
     * 只消费宿主提取的当前原文，识别后不调用模型；派生命令不提供此捷径。
     * @param data 原生消息及固定发送时刻
     * @param original 当前真实正文
     * @return 已识别意图的实际处理阶段；普通对话或旧宿主为空
     */
    public Optional<CompletionStage<String>> natural(ITeaNekoMessageData data, String original) {
        if (plugins != null && !plugins.isEnabled("agent.automation")) {
            return Optional.empty();
        }
        if (NaturalTaskParser.parse(original, data.getTime(), "").isEmpty()) {
            return Optional.empty();
        }
        var scope = NativeMessageSources.actor(data).scope();
        if (components.settings(scope, "schedule").isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(run(null, control -> {
            var parameters = components.settings(scope, "schedule").orElseThrow(
                    () -> new IllegalStateException("当前聊天的提醒组件已停用。"));
            var intent = NaturalTaskParser.parse(original, data.getTime(),
                    parameters.path("taskTimeZone").asString("")).orElseThrow();
            return switch (intent) {
                case ITaskIntent.Create candidate -> create(data, candidate.firstFire().atZone(ZoneId.of(candidate.zone())).toOffsetDateTime().toString(),
                        candidate.zone(), candidate.intervalSeconds(), List.of(candidate.text()), control);
                case ITaskIntent.Inspect candidate -> inspect(data, candidate.id().toString(), candidate.cancel(), control);
                case ITaskIntent.ListTasks candidate -> {
                    var actor = NativeMessageSources.actor(data);
                    yield schedules.list(actor, candidate.after(), control::checkActive).thenApply(page -> TaskPresentation.list(
                            candidate.onlyMine() ? new AgentScheduleService.Page(page.tasks().stream()
                                    .filter(task -> task.spec().creator().userId().equals(actor.userId())).toList(), page.nextCursor()) : page));
                }
                case ITaskIntent.Clarify candidate -> CompletableFuture.completedFuture(candidate.reason());
            };
        }));
    }

    /**
     * 明确偏移与 ZoneId 必须一致，夏令时空洞拒绝，重复请求使用相同绝对时间及规格。
     * @param data 真实消息
     * @param time ISO 偏移时间
     * @param zone 明确时区
     * @param intervalSeconds 固定重复秒数，零为单次
     * @param words 提醒正文
     * @param parent 借用父控制
     * @return 持久化后的任务卡片
     */
    public CompletionStage<String> create(ITeaNekoMessageData data, String time, String zone, long intervalSeconds,
            List<String> words, @Nullable ExecutionControl parent) {
        return run(parent, control -> {
            var actor = NativeMessageSources.actor(data);
            OffsetDateTime local;
            ZoneId zoneId;
            try { local = OffsetDateTime.parse(time); zoneId = ZoneId.of(zone); }
            catch (DateTimeException invalid) {
                return CompletableFuture.completedFuture("请提供明确的偏移时间和时区，例如 2026-11-03T09:00:00+08:00 Asia/Shanghai；含糊时间不能创建任务。");
            }
            if (!zoneId.getRules().getValidOffsets(local.toLocalDateTime()).contains(local.getOffset())) {
                return CompletableFuture.completedFuture("时间偏移与时区不一致或该本地时间不存在，请明确时间后重试。");
            }
            var text = String.join(" ", words);
            if (text.isBlank() || text.length() > 2000) { return CompletableFuture.completedFuture("提醒正文需要包含 1 至 2000 个字符。"); }
            var title = text.substring(0, text.offsetByCodePoints(0, Math.min(40, text.codePointCount(0, text.length()))));
            var spec = new AgentScheduledTask.Spec(title, actor, "remind", Map.of("text", text), local.toInstant(), zoneId.getId(), intervalSeconds);
            var id = UUID.nameUUIDFromBytes(StableDigest.sha256("agent-reminder-v1", actor.scope().clientId(), actor.scope().scopeId(),
                    actor.userId().orElseThrow().toString(), actor.sourceId()).getBytes(StandardCharsets.UTF_8));
            control.checkActive();
            return schedules.create(id, spec).thenApply(TaskPresentation::card);
        });
    }

    /**
     * 查询当前来源中本人或当前群管理员可见的任务，游标不接受任意无界文本。
     * @param data 原生消息
     * @param after 上页游标，空字符串表示首页
     * @param parent 借用父控制
     * @return 文本列表
     */
    public CompletionStage<String> list(ITeaNekoMessageData data, String after, @Nullable ExecutionControl parent) {
        return run(parent, control -> {
            if (!after.isEmpty() && !UUID.fromString(after).toString().equals(after)) { throw new IllegalArgumentException("任务分页游标不合法。"); }
            control.checkActive();
            return schedules.list(NativeMessageSources.actor(data), after, control::checkActive).thenApply(TaskPresentation::list);
        });
    }

    /**
     * 查看或取消固定任务，不从参数接受创建者或 scope。
     * @param data 原生消息
     * @param id 任务 UUID
     * @param cancel 是否取消
     * @param parent 借用父控制
     * @return 当前任务卡片
     */
    public CompletionStage<String> inspect(ITeaNekoMessageData data, String id, boolean cancel, @Nullable ExecutionControl parent) {
        return run(parent, control -> {
            var actor = NativeMessageSources.actor(data);
            var taskId = UUID.fromString(id);
            control.checkActive();
            return (cancel ? schedules.cancel(taskId, actor) : schedules.detail(taskId, actor)).thenApply(TaskPresentation::card);
        });
    }

    /**
     * 就绪、业务及父取消共用同一观察边界，仅关闭本方法创建的控制。
     * @param parent 借用父控制
     * @param action 已固定真实来源的业务
     * @return 包含真实提交或查询的完成阶段
     */
    private CompletionStage<String> run(@Nullable ExecutionControl parent, Function<ExecutionControl, CompletionStage<String>> action) {
        var clock = Clock.systemUTC(); var control = parent == null
                ? ExecutionControl.withDeadline(new ResourceBudget(Map.of()), clock.instant().plusSeconds(60), clock, scheduler) : parent;
        try {
            control.checkActive();
            var token = plugins == null ? null : plugins.capture("agent.automation");
            var work = lifecycle.ready().thenComposeAsync(ignored -> {
                control.checkActive();
                if (token != null) {
                    plugins.check(token);
                }
                return action.apply(control);
            }, executor).thenApply(text -> {
                control.checkActive();
                if (token != null) {
                    plugins.check(token);
                }
                return text;
            });
            return control.guard(work).whenComplete((ignored, failure) -> { if (parent == null) { control.close(); } });
        } catch (RuntimeException failure) { if (parent == null) { control.close(); } return CompletableFuture.failedFuture(failure); }
    }
}
