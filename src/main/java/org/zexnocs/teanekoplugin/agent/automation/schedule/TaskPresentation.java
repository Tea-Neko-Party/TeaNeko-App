package org.zexnocs.teanekoplugin.agent.automation.schedule;

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.zexnocs.teanekoplugin.agent.automation.schedule.AgentScheduleService;
import org.zexnocs.teanekoplugin.agent.automation.schedule.AgentScheduledTask;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;

/**
 * 面向聊天的普通文本任务卡片和分页列表，不依赖平台客户端。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.automation")
public final class TaskPresentation {
    /** 工具类不创建实例。 */
    private TaskPresentation() { }

    /**
     * 展示明确时区、重复规则和最近状态，不暴露动作参数或创建者凭据。
     * @param task 已经完成读取授权的任务
     * @return 普通消息卡片
     */
    public static String card(AgentScheduledTask task) {
        var time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX")
                .withZone(ZoneId.of(task.spec().zoneId())).format(task.nextFire());
        var state = switch (task.status()) {
            case ACTIVE -> "等待执行"; case RUNNING -> "执行中"; case COMPLETED -> "已完成";
            case CANCELLED -> "已取消"; case EXPIRED -> "已过期"; case PAUSED -> "已暂停";
            case INDETERMINATE -> "结果不明，已停止";
        };
        return "任务：" + task.spec().title() + "\nID：" + task.id() + "\n状态：" + state
                + "\n计划时间：" + time + "（" + task.spec().zoneId() + "）\n重复："
                + (task.spec().intervalSeconds() == 0 ? "单次" : "每 " + task.spec().intervalSeconds() + " 秒")
                + "\n最近结果：" + task.lastOutcome();
    }

    /**
     * 本页按计划时间展示，保留空页的继续游标，避免其他 scope 记录使用户误判列表结束。
     * @param page 当前授权页面
     * @return 任务列表文本，可由宿主拆为转发消息
     */
    public static String list(AgentScheduleService.Page page) {
        return (page.tasks().isEmpty() ? "本页没有可见任务。" : String.join("\n\n", page.tasks().stream()
                .sorted(Comparator.comparing(AgentScheduledTask::nextFire).thenComparing(AgentScheduledTask::id)).map(TaskPresentation::card).toList()))
                + (page.nextCursor().isEmpty() ? "\n已到最后一页。" : "\n下一页游标：" + page.nextCursor());
    }
}
