package org.zexnocs.teanekoplugin.agent.automation.integration.command;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.CommandExtension;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.command.api.DefaultValue;
import org.zexnocs.teanekocore.command.api.SubCommand;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.plugin.PluginOwned;

import java.util.List;
import java.util.concurrent.CompletionStage;

import static org.zexnocs.teanekoagent.integration.command.AgentCommandSupport.respond;

/**
 * 自动化插件贡献的 Agent 子命令，插件关闭时统一退出目录与执行。
 * <br>2026/11/07：子命令使用真实插件 Bean 和原权限事务。
 *
 * <br>2026/11/07：业务归属与通用底座按职责分离，保留持久身份及完成边界。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
// 2026/11/07：共享命令路由与 Spring Bean 名分离，多个插件可以贡献同一前缀。
@CommandExtension(prefix = "/agent")
@PluginOwned("agent.automation")
public final class AutomationCommands {
    private final @Nullable IAgentSkillCommands skillCommands;
    private final @Nullable IAgentTaskCommands taskCommands;

    /**
     * @param skillCommands 可选技能入口
     * @param taskCommands 可选计划任务入口
     */
    public AutomationCommands(@Nullable IAgentSkillCommands skillCommands, @Nullable IAgentTaskCommands taskCommands) {
        this.skillCommands = skillCommands;
        this.taskCommands = taskCommands;
    }

    /**
     * 查看当前允许的 Skill 和工具依赖，不执行工作流程。
     * @param command 原生来源
     * @return 实际查询与平台反馈阶段
     */
    // 2026/11/02：为 subhelp 注册该入口的中文用途说明。
    @Description("用法：/agent skills\n查看当前作用域允许使用的 Skill 及其工具依赖，不执行 Skill 工作流。是否可用仍取决于本地目录、当前 scope 的 Skill 配置和启用开关。\n示例：/agent skills")
    @SubCommand("skills") public CompletionStage<Void> skills(CommandData<ITeaNekoMessageData> command) {
        return respond(command, () -> skillCommands().execute(command.getRawData(), false, command.getParentExecution()));
    }

    /**
     * 仅真实 DEBUG 可发布本地安装或更新的 Skill，所有文件成功校验才替换快照。
     * @param command DEBUG 来源
     * @return 重载和实际反馈阶段
     */
    // 2026/11/02：为 subhelp 注册该入口的中文用途说明。
    @Description("用法：/agent reloadskills\n需要 DEBUG 权限。校验并重新加载本地 Skill 目录，所有文件校验成功后才发布新快照；不会下载或安装远程 Skill，也不自动修改当前 scope 的 Skill 开关和白名单。\n示例：/agent reloadskills\n本地新增或修改 Skill 后执行，再用 /agent skills 检查当前作用域可见内容。")
    @SubCommand(value = "reloadskills", permission = CommandPermission.DEBUG)
    public CompletionStage<Void> reloadSkills(CommandData<ITeaNekoMessageData> command) {
        return respond(command, () -> skillCommands().execute(command.getRawData(), true, command.getParentExecution()));
    }

    /**
     * 创建当前来源提醒，等待持久化及原反馈发送。
     * @param command 真实命令
     * @param time ISO 偏移时间
     * @param zone 明确时区
     * @param intervalSeconds 固定重复秒数
     * @param text 提醒正文
     * @return 创建及反馈完成阶段
     */
    // 2026/11/02：为 subhelp 注册该入口的中文用途说明。
    @Description("用法：/agent remind <ISO偏移时间> <时区> <重复秒数> <正文>\n在当前会话创建提醒并等待保存回执。时间须包含日期、时分秒和 UTC 偏移，偏移必须与时区一致；重复秒数填 0 为单次，60–31536000 为固定间隔重复秒数，正文支持空格、长度 1–2000 字符。实际执行受任务配置和权限控制。\n单次示例：/agent remind 2026-11-03T09:00:00+08:00 Asia/Shanghai 0 开会\n每日固定间隔示例：/agent remind 2026-11-03T09:00:00+08:00 Asia/Shanghai 86400 喝水\n请将时间替换为期望执行时间；86400 表示每隔 24 小时，不表示按日历重新计算。")
    @SubCommand("remind") public CompletionStage<Void> remind(CommandData<ITeaNekoMessageData> command,
            String time, String zone, long intervalSeconds, List<String> text) {
        return respond(command, () -> taskCommands().create(command.getRawData(), time, zone, intervalSeconds, text, command.getParentExecution()));
    }

    /**
     * 在禁用后仍允许管理已保存任务，执行开关由动作策略独立控制。
     * @param command 真实命令
     * @param after 上页游标
     * @return 分页及反馈完成阶段
     */
    // 2026/11/02：为 subhelp 注册该入口的中文用途说明。
    @Description("用法：/agent tasks [游标]\n分页查看当前 scope 内自己有权读取的任务，普通用户查看本人任务，具备相应管理权限者按原任务策略查看。首次省略游标，续页使用结果返回的游标；游标不是页码。\n示例：/agent tasks\n续页示例：/agent tasks 00000000-0000-4000-8000-000000000001\n示例 UUID 须替换为上一页返回的实际游标。")
    @SubCommand("tasks") public CompletionStage<Void> tasks(CommandData<ITeaNekoMessageData> command, @DefaultValue("") String after) {
        return respond(command, () -> taskCommands().list(command.getRawData(), after, command.getParentExecution()));
    }

    /**
     * 查看已授权的单任务卡片。
     * @param command 真实命令
     * @param id 任务 UUID
     * @return 查询及反馈完成阶段
     */
    // 2026/11/02：为 subhelp 注册该入口的中文用途说明。
    @Description("用法：/agent task <任务UUID>\n查看当前 scope 内有权读取的单个任务卡片，包括计划与执行状态；任务 UUID 从创建回执或 tasks 列表获取，不是聊天请求 UUID。\n示例：/agent task 00000000-0000-4000-8000-000000000001\n示例 UUID 须替换为实际任务 ID。")
    @SubCommand("task") public CompletionStage<Void> task(CommandData<ITeaNekoMessageData> command, String id) {
        return respond(command, () -> taskCommands().inspect(command.getRawData(), id, false, command.getParentExecution()));
    }

    /**
     * 取消本人或当前群管理员可管理的任务，等待真实状态提交。
     * @param command 真实命令
     * @param id 任务 UUID
     * @return 取消及反馈完成阶段
     */
    // 2026/11/02：为 subhelp 注册该入口的中文用途说明。
    @Description("用法：/agent canceltask <任务UUID>\n取消本人或当前 scope 内按原管理策略有权管理的任务，并等待状态提交。任务 UUID 从创建回执或 tasks 列表获取；取消后可用 task 核对最新状态，已经发生的发送不会撤回。\n示例：/agent canceltask 00000000-0000-4000-8000-000000000001\n示例 UUID 须替换为实际任务 ID。")
    @SubCommand("canceltask") public CompletionStage<Void> cancelTask(CommandData<ITeaNekoMessageData> command, String id) {
        return respond(command, () -> taskCommands().inspect(command.getRawData(), id, true, command.getParentExecution()));
    }

    /**
     * 未装配时明确拒绝，不退回任意文件读取。
     * @return 实际 Skill 命令边界
     * @throws IllegalStateException 当当前宿主未装配时
     */
    private IAgentSkillCommands skillCommands() {
        if (skillCommands == null) {
            throw new IllegalStateException("当前宿主未装配 Skill 能力。");
        }
        return skillCommands;
    }

    /**
     * 未装配时明确拒绝，不自行创建无权限策略的任务服务。
     * @return 已安装任务入口
     * @throws IllegalStateException 当宿主缺少任务服务时
     */
    private IAgentTaskCommands taskCommands() {
        if (taskCommands == null) {
            throw new IllegalStateException("当前宿主未装配提醒任务。");
        }
        return taskCommands;
    }
}
