package org.zexnocs.teanekoagent.adapter.command;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekoagent.agent.monitor.AgentRuntimeMonitor;
import org.zexnocs.teanekoagent.personality.PersonalityManagementCommand.Action;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContent;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.message.api.content.ITextTeaNekoContentPart;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 新 Agent 的显式聊天、状态、本人停止及空间管理入口，统一等待业务和反馈。
 * <br>2026/11/02：为全部子命令提供 subhelp 可读取的用途和参数说明。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.6.0-l
 */
@Command(value = "/agent", permission = CommandPermission.ALL, scope = CommandScope.ALL)
@Description("Agent 显式聊天、空间绑定与 DEBUG 记忆/人格管理；使用前先登记当前作用域 agent 配置。")
public class AgentCommand {
    private final AgentCommandGateway gateway;
    private final @Nullable AgentMemoryManagementCommands memoryManagement;
    private final @Nullable AgentPersonalityManagementCommands personalityManagement;
    private final @Nullable AgentSkillCommands skillCommands;
    private final @Nullable AgentTaskCommands taskCommands;
    private final @Nullable AgentRuntimeMonitor monitor;
    /**
     * 保存应用边界，不创建实例或加载外部配置。
     * @param gateway 原生业务入口
     */
    public AgentCommand(AgentCommandGateway gateway) { this(gateway, null, null); }
    /**
     * 装配实际管理服务，旧宿主仅装配聊天入口时仍可使用单参数构造方法。
     * @param gateway 原生聊天及基本管理
     * @param memoryManagement DEBUG 记忆管理
     */
    public AgentCommand(AgentCommandGateway gateway, AgentMemoryManagementCommands memoryManagement) { this(gateway, memoryManagement, null); }
    /**
     * 装配聊天、记忆与人格管理，所有管理入口仍经过原生权限和当前启用复核。
     * @param gateway 原聊天入口
     * @param memoryManagement 可选记忆管理
     * @param personalityManagement 可选人格管理
     */
    public AgentCommand(AgentCommandGateway gateway, @Nullable AgentMemoryManagementCommands memoryManagement,
            @Nullable AgentPersonalityManagementCommands personalityManagement) {
        this(gateway, memoryManagement, personalityManagement, null);
    }
    /**
     * 组合所有实际管理边界，未装配的可选能力保持明确不可用。
     * @param gateway 原生聊天入口
     * @param memoryManagement 可选记忆管理
     * @param personalityManagement 可选人格管理
     * @param skillCommands 可选 Skill 查询及重载
     */
    public AgentCommand(AgentCommandGateway gateway, @Nullable AgentMemoryManagementCommands memoryManagement,
            @Nullable AgentPersonalityManagementCommands personalityManagement, @Nullable AgentSkillCommands skillCommands) {
        this(gateway, memoryManagement, personalityManagement, skillCommands, null);
    }
    /**
     * 接入原生任务管理，保留既有宿主构造入口。
     * @param gateway 原生聊天入口
     * @param memoryManagement 可选记忆管理
     * @param personalityManagement 可选人格管理
     * @param skillCommands 可选 Skill 管理
     * @param taskCommands 可选提醒任务
     */
    public AgentCommand(AgentCommandGateway gateway, @Nullable AgentMemoryManagementCommands memoryManagement,
            @Nullable AgentPersonalityManagementCommands personalityManagement, @Nullable AgentSkillCommands skillCommands,
            @Nullable AgentTaskCommands taskCommands) {
        this(gateway, memoryManagement, personalityManagement, skillCommands, taskCommands, null);
    }

    /**
     * 组合可选只读运行监控，旧宿主构造入口保持可用。
     * @param gateway 原生聊天
     * @param memoryManagement 记忆管理
     * @param personalityManagement 人格管理
     * @param skillCommands Skill 管理
     * @param taskCommands 任务管理
     * @param monitor 脱敏监控
     */
    @Autowired public AgentCommand(AgentCommandGateway gateway, @Nullable AgentMemoryManagementCommands memoryManagement,
            @Nullable AgentPersonalityManagementCommands personalityManagement, @Nullable AgentSkillCommands skillCommands,
            @Nullable AgentTaskCommands taskCommands, @Nullable AgentRuntimeMonitor monitor) {
        this.gateway = gateway; this.memoryManagement = memoryManagement; this.personalityManagement = personalityManagement; this.skillCommands = skillCommands;
        this.taskCommands = taskCommands;
        this.monitor = monitor;
    }
    /**
     * 展示实际已经提供的入口及权限，不把未实现的管理能力列成可用命令。
     * @param command 原可信命令
     * @return 原帮助反馈完成信号
     */
    // 2026/11/02：方法描述由既有 subhelp 注册流程读取。
    @Description("显示 Agent 聊天、记忆、人格、Skill 和任务命令帮助。")
    @DefaultCommand public CompletionStage<Void> help(CommandData<ITeaNekoMessageData> command) {
        return command.getRawData().getMessageSender().sendReplyMessageAsync("""
                /agent chat <文本>
                自然语言任务例：/agent chat 10分钟后提醒我喝水；/agent chat 查看我的任务；/agent chat 取消任务 <UUID>
                日历提醒需原文明示时区或由 OWNER 配置 taskTimeZone；含糊时间会要求补充。
                /agent status
                /agent monitor（DEBUG，只读当前作用域计数，不包含正文或凭据）
                /agent stop <请求UUID>
                /agent remind <ISO偏移时间> <时区> <重复秒数，单次填0> <正文>
                例：/agent remind 2026-11-03T09:00:00+08:00 Asia/Shanghai 0 开会
                /agent tasks [游标]；/agent task <任务UUID>；/agent canceltask <任务UUID>
                /agent namespaces [偏移]
                /agent reset（OWNER）
                /agent setnamespace <已有空间>（OWNER，与 /cfg set agent memoryNamespace 一致）
                DEBUG 记忆：memory / exportmemory / editmemory / lockmemory / clonememory / clear / purgememory；先用 memory 查询版本。
                DEBUG 人格命令均加 /agent 前缀：
                personality / exportpersonality / personalitybackups [key] [偏移]
                setcorrectionkey <目标key> <绑定版本> <目标版本，新建填-1>
                clonecorrections / backuppersonality <源key> <绑定版本> <源版本>
                addcorrection <当前key> <绑定版本> <补正版本> <字段> <正文>
                editcorrection <当前key> <绑定版本> <补正版本> <补正UUID> <正文>
                lockcorrection <当前key> <绑定版本> <补正版本> <补正UUID> <true或false>
                revokecorrection <当前key> <绑定版本> <补正版本> <补正UUID>
                先用 personality 查询版本与可用字段；字段中的空格用下划线。correctionKey 不经 /cfg 修改。
                /agent skills 查看当前 Skill；/agent reloadskills 仅 DEBUG 可更新本地目录快照。
                OWNER 可 /cfg set agent skills clear-explanation，再 /cfg set agent skillsEnabled true 启用示例。
                命令工具默认关闭，可由 OWNER 使用 /cfg set agent commandToolsEnabled true 启用。""");
    }
    /**
     * 接入真实文本请求和父链路，原命令 Stage 包含最终回复。
     * @param command 真实来源及可选父控制
     * @param text 本次完整词列表
     * @return 对话和平台发送完成阶段
     * @throws IllegalArgumentException 当原消息与匹配命令不一致时
     */
    // 2026/11/02：为 subhelp 注册该入口的中文用途说明。
    @Description("用法：/agent chat <文本>\n与当前作用域的 Agent 对话，正文可包含空格；使用当前绑定 namespace 的记忆和按 scope 配置的人格。须先登记并启用当前作用域的 agent 配置。\n示例：/agent chat 你好，请介绍一下自己\n明确保存记忆示例：/agent chat 记住：我喜欢喝绿茶\n自然语言提醒示例：/agent chat 10分钟后提醒我喝水")
    @SubCommand("chat") public CompletionStage<Void> chat(CommandData<ITeaNekoMessageData> command, List<String> text) {
        var original = chatText(command, text);
        if (command.getParentExecution() == null && taskCommands != null) {
            var task = taskCommands.natural(command.getRawData(), original);
            if (task.isPresent()) { return respond(command, task::orElseThrow); }
        }
        return gateway.chat(command.getRawData(), original, command.getParentExecution());
    }
    /**
     * 查看启用、空间及本人在途请求，不列出他人的消息。
     * @param command 原真实来源
     * @return 状态反馈完成信号
     */
    // 2026/11/02：为 subhelp 注册该入口的中文用途说明。
    @Description("用法：/agent status\n查看当前作用域 Agent 是否登记并启用、当前记忆空间，以及本人尚未完成的聊天请求 UUID。需要停止请求时，将这里返回的请求 UUID 交给 stop。\n示例：/agent status")
    @SubCommand("status") public CompletionStage<Void> status(CommandData<ITeaNekoMessageData> command) { return respond(command, () -> gateway.status(command.getRawData())); }

    /**
     * 只允许实际 DEBUG 查看当前 scope 的统计，不列出其他作用域或原始消息。
     * @param command 原生管理命令
     * @return 查询及平台反馈完成阶段
     */
    // 2026/11/02：为 subhelp 注册该入口的中文用途说明。
    @Description("用法：/agent monitor\n需要 DEBUG 权限。只读查看当前作用域最近最多 100 个轮次的成功及终止计数、模型请求数、工具结果数、报告与估算 token、未知用量次数和累计耗时，以及平台交付统计；不包含对话正文或凭据。\n示例：/agent monitor")
    @SubCommand(value = "monitor", permission = CommandPermission.DEBUG)
    public CompletionStage<Void> monitor(CommandData<ITeaNekoMessageData> command) {
        return respond(command, () -> {
            var current = monitor;
            if (current == null || command.getRawData().getUserData().getRole() != CommandPermission.DEBUG) {
                throw new IllegalStateException("监控尚未装配或当前来源不是 DEBUG。");
            }
            return CompletableFuture.completedFuture(current.describe(NativeCommandBridge.actor(command.getRawData()).scope()));
        });
    }
    /**
     * 只停止当前用户自己的请求。
     * @param command 原真实来源
     * @param requestId 待停止 UUID
     * @return 取消接收反馈完成信号
     */
    // 2026/11/02：为 subhelp 注册该入口的中文用途说明。
    @Description("用法：/agent stop <请求UUID>\n停止本人在当前作用域发起的 Agent 请求；请求 UUID 从 /agent status 获取，不是平台消息 ID。反馈表示取消请求的接收结果，已发生的外部操作不会因此撤销。\n示例：/agent stop 00000000-0000-4000-8000-000000000001\n示例 UUID 须替换为本人在途请求的实际值。")
    @SubCommand("stop") public CompletionStage<Void> stop(CommandData<ITeaNekoMessageData> command, String requestId) { return respond(command, () -> CompletableFuture.completedStage(gateway.stop(command.getRawData(), requestId))); }
    /**
     * 分页列出本人创建的空间，不能指定其他用户标识。
     * @param command 原真实来源
     * @param offset 非负分页偏移
     * @return 原分页反馈完成信号
     */
    // 2026/11/02：为 subhelp 注册该入口的中文用途说明。
    @Description("用法：/agent namespaces [偏移]\n分页列出本人创建的记忆空间 ID 和创建时间；偏移为非负整数，默认 0，续页使用结果中的下一页偏移。共享默认空间不一定出现在自建列表中，当前绑定请用 /agent status 查看。\n示例：/agent namespaces\n续页示例：/agent namespaces 5")
    @SubCommand("namespaces") public CompletionStage<Void> namespaces(CommandData<ITeaNekoMessageData> command, @DefaultValue("0") int offset) { return respond(command, () -> gateway.namespaces(command.getRawData(), offset)); }
    /**
     * 创建并切换新空记忆空间，旧空间和人格补正保留。
     * @param command 已经原 OWNER 链授权的真实来源
     * @return 实际提交及同步观察反馈
     */
    // 2026/11/02：为 subhelp 注册该入口的中文用途说明。
    @Description("用法：/agent reset\n需要 OWNER 权限。为当前作用域创建新的空记忆空间并切换绑定；旧空间保留，人格补正不受影响。适合开始使用独立记忆；若要清空共享空间中的数据，请查看 clear 的说明。\n示例：/agent reset")
    @SubCommand(value = "reset", permission = CommandPermission.OWNER) public CompletionStage<Void> reset(CommandData<ITeaNekoMessageData> command) { return respond(command, () -> gateway.changeNamespace(command.getRawData(), null, command.getParentExecution())); }
    /**
     * 绑定已有共享空间，遵守原 OWNER 权限，不要求目标必须属于本人。
     * @param command 原授权来源
     * @param namespace 已登记空间标识
     * @return 实际提交及同步观察反馈
     */
    // 2026/11/02：为 subhelp 注册该入口的中文用途说明。
    @Description("用法：/agent setnamespace <已有空间ID>\n需要 OWNER 权限。将当前作用域绑定到已登记的记忆空间，不要求目标空间由本人创建；不会复制或清空记录。同一 namespace 的记忆可跨 scope 共享，人格仍按 scope 绑定。\n示例：/agent setnamespace default\n操作后可用 /agent status 确认绑定；default 为默认共享空间。")
    @SubCommand(value = "setnamespace", permission = CommandPermission.OWNER) public CompletionStage<Void> setNamespace(CommandData<ITeaNekoMessageData> command, String namespace) { return respond(command, () -> gateway.changeNamespace(command.getRawData(), namespace, command.getParentExecution())); }
    /**
     * 查看空间版本及事实预览，后继页固定原代次。
     * @param command 真实 DEBUG 命令
     * @param namespace 空间
     * @param generation 原代次，首次 -1 查询当前值
     * @param after 上页游标
     * @return 原权限链及平台反馈完成信号
     */
    // 2026/11/02：为 subhelp 注册该入口的中文用途说明。
    @Description("用法：/agent memory <namespace> [generation] [游标]\n需要 DEBUG 权限。分页查看指定记忆空间的事实预览、记录 UUID、记录 revision，以及空间 generation/revision。generation 为数据代次，首次省略或填 -1 查询当前代次；每页最多 5 条。\n首次示例：/agent memory default\n续页示例：/agent memory default 0 00000000-0000-4000-8000-000000000001\n续页必须使用上页实际 generation 和下一页游标；编辑用记录 revision，清空或克隆用空间 revision，不可混用。")
    @SubCommand(value = "memory", permission = CommandPermission.DEBUG)
    public CompletionStage<Void> memory(CommandData<ITeaNekoMessageData> command, String namespace,
            @DefaultValue("-1") long generation, @DefaultValue("") String after) {
        return respond(command, () -> management().page(command.getRawData(), namespace, generation, after, false, command.getParentExecution()));
    }
    /**
     * 导出一个完整 JSON 证据页，长内容由原转发发送器分段交付。
     * @param command DEBUG 命令
     * @param namespace 空间
     * @param generation 固定代次，首次可为 -1
     * @param after 前页游标
     * @return 完整导出反馈阶段
     */
    // 2026/11/02：为 subhelp 注册该入口的中文用途说明。
    @Description("用法：/agent exportmemory <namespace> [generation] [游标]\n需要 DEBUG 权限。导出指定记忆空间的一页完整 JSON 证据，包含空间元数据、固定代次和记录；每页最多 1 条，不会一次导出整个空间。首次省略 generation 或填 -1，续页固定原代次。\n示例：/agent exportmemory default\n续页示例：/agent exportmemory default 0 00000000-0000-4000-8000-000000000001\n续页的代次和游标须替换为上页实际值。")
    @SubCommand(value = "exportmemory", permission = CommandPermission.DEBUG)
    public CompletionStage<Void> exportMemory(CommandData<ITeaNekoMessageData> command, String namespace,
            @DefaultValue("-1") long generation, @DefaultValue("") String after) {
        return respond(command, () -> management().page(command.getRawData(), namespace, generation, after, true, command.getParentExecution()));
    }
    /**
     * 按预期事实版本修改正文，保留来源、主体及事件时间。
     * @param command DEBUG 命令
     * @param namespace 空间
     * @param generation 原代次
     * @param record 记录 UUID
     * @param revision 原事实版本
     * @param text 新正文词列表
     * @return 实际编辑反馈
     */
    // 2026/11/02：为 subhelp 注册该入口的中文用途说明。
    @Description("用法：/agent editmemory <namespace> <generation> <记录UUID> <记录revision> <新正文>\n需要 DEBUG 权限。按预期记录版本修改事实正文，保留原主体、来源和事件时间；正文可包含空格。先用 memory 获取当前代次、记录 UUID 和记录 revision，版本变化后须重新查询。\n示例：/agent editmemory default 0 00000000-0000-4000-8000-000000000001 2 我现在更喜欢乌龙茶\n示例中的代次、UUID 和版本须替换为查询结果。")
    @SubCommand(value = "editmemory", permission = CommandPermission.DEBUG)
    public CompletionStage<Void> editMemory(CommandData<ITeaNekoMessageData> command, String namespace, long generation,
            String record, long revision, List<String> text) {
        return respond(command, () -> management().edit(command.getRawData(), namespace, generation, record, revision, String.join(" ", text), null, command.getParentExecution()));
    }
    /**
     * 按原版本锁定或解锁事实，模型普通新增不能修改锁定记录。
     * @param command DEBUG 命令
     * @param namespace 空间
     * @param generation 原代次
     * @param record 记录 UUID
     * @param revision 原事实版本
     * @param locked 必须为 true 或 false 的目标锁定状态
     * @return 实际锁定反馈
     */
    // 2026/11/02：为 subhelp 注册该入口的中文用途说明。
    @Description("用法：/agent lockmemory <namespace> <generation> <记录UUID> <记录revision> <true或false>\n需要 DEBUG 权限。true 锁定指定记忆，false 解锁；模型普通新增不能修改锁定记录。先用 memory 获取原记录版本，每次操作后重新查询最新 revision。\n锁定示例：/agent lockmemory default 0 00000000-0000-4000-8000-000000000001 2 true\n解锁时将末尾改为 false，并使用最新记录版本；示例标识须替换为实际值。")
    @SubCommand(value = "lockmemory", permission = CommandPermission.DEBUG)
    public CompletionStage<Void> lockMemory(CommandData<ITeaNekoMessageData> command, String namespace, long generation,
            String record, long revision, String locked) {
        return respond(command, () -> {
            if (!"true".equals(locked) && !"false".equals(locked)) { throw new IllegalArgumentException("锁定状态必须为 true 或 false。"); }
            return management().edit(command.getRawData(), namespace, generation, record, revision, null, Boolean.valueOf(locked), command.getParentExecution());
        });
    }
    /**
     * 将当前代次克隆为独立空间，原绑定及人格保持独立。
     * @param command DEBUG 命令
     * @param namespace 源空间
     * @param generation 原代次
     * @param revision 原空间版本
     * @return 克隆目标及记录数量
     */
    // 2026/11/02：为 subhelp 注册该入口的中文用途说明。
    @Description("用法：/agent clonememory <源namespace> <generation> <空间revision>\n需要 DEBUG 权限。把源空间当前代次快照克隆到新空间，返回新空间 ID 和事实数量；不会自动切换当前绑定，也不修改原人格或历史。先用 memory 查询空间版本，需使用克隆结果时再调用 setnamespace。\n示例：/agent clonememory default 0 3\n示例中的代次和空间 revision 须替换为实际查询值。")
    @SubCommand(value = "clonememory", permission = CommandPermission.DEBUG)
    public CompletionStage<Void> cloneMemory(CommandData<ITeaNekoMessageData> command, String namespace, long generation, long revision) {
        return respond(command, () -> management().change(command.getRawData(), namespace, generation, revision, true, command.getParentExecution()));
    }
    /**
     * 先逻辑清空，再同步共享会话并分批回收旧代次，普通 OWNER 不能调用。
     * @param command DEBUG 命令
     * @param namespace 目标空间
     * @param generation 原代次
     * @param revision 原空间版本
     * @return 实际清空及后续处理状态
     */
    // 2026/11/02：为 subhelp 注册该入口的中文用途说明。
    @Description("用法：/agent clear <namespace> <generation> <空间revision>\n需要 DEBUG 权限。逻辑清空指定记忆空间：推进 generation，使共享该空间的旧上下文失效，并协调会话及分批回收旧数据。会影响所有绑定此 namespace 的区域，不只是当前 scope。\n示例：/agent clear default 0 3\n执行前先用 memory 核对空间、当前代次和空间 revision；示例版本须替换为实际值。")
    @SubCommand(value = "clear", permission = CommandPermission.DEBUG)
    public CompletionStage<Void> clearMemory(CommandData<ITeaNekoMessageData> command, String namespace, long generation, long revision) {
        return respond(command, () -> management().change(command.getRawData(), namespace, generation, revision, false, command.getParentExecution()));
    }
    /**
     * 继续回收已逻辑失效的事实数据，不删除命令全局防重账本。
     * @param command DEBUG 命令
     * @param namespace 空间
     * @param generation 已失效代次
     * @return 实际回收数量
     */
    // 2026/11/02：为 subhelp 注册该入口的中文用途说明。
    @Description("用法：/agent purgememory <namespace> <旧generation>\n需要 DEBUG 权限。有界回收指定空间已失效代次的数据，每次最多处理 100 条；用于逻辑清空后的旧数据清理，不代替 clear，也不接受当前有效代次作为清理目标。\n示例：/agent purgememory default 0\n须先确认 0 已是失效代次；数据较多时可根据实际回收结果继续清理。")
    @SubCommand(value = "purgememory", permission = CommandPermission.DEBUG)
    public CompletionStage<Void> purgeMemory(CommandData<ITeaNekoMessageData> command, String namespace, long generation) {
        return respond(command, () -> management().purge(command.getRawData(), namespace, generation, command.getParentExecution()));
    }

    /**
     * 查询当前或指定补正 key 的有界内容及版本，首次使用可初始化当前引用。
     * @param command DEBUG 来源
     * @param key 可选补正 key
     * @param offset 显示偏移
     * @return 完整查询反馈
     */
    // 2026/11/02：为 subhelp 注册该入口的中文用途说明。
    @Description("用法：/agent personality [key] [偏移]\n需要 DEBUG 权限。省略 key 查看当前 scope 的人格补正；指定 key 可查看对应补正或备份。显示 bindingRevision、stateRevision、补正 UUID、锁定状态和可新增字段，每页最多 5 条；偏移默认 0，范围 0–64。\n示例：/agent personality\n指定 key 续页：/agent personality my-persona 5\n管理操作前须先查询这里的实际版本和字段；my-persona 须替换为实际 key。")
    @SubCommand(value = "personality", permission = CommandPermission.DEBUG)
    public CompletionStage<Void> personality(CommandData<ITeaNekoMessageData> command, @DefaultValue("") String key, @DefaultValue("0") int offset) {
        return respond(command, () -> personalityManagement().page(command.getRawData(), key, offset, false, false, command.getParentExecution()));
    }
    /**
     * 导出有原来源的补正页面，长内容复用原分段发送。
     * @param command DEBUG 来源
     * @param key 可选补正 key
     * @param offset 页偏移
     * @return 完整导出反馈
     */
    // 2026/11/02：为 subhelp 注册该入口的中文用途说明。
    @Description("用法：/agent exportpersonality [key] [偏移]\n需要 DEBUG 权限。导出人格绑定、补正集合版本及记录的完整 JSON 页；key 省略时使用当前 scope 绑定，每页最多 2 条，偏移默认 0、范围 0–64。导出不会修改人格。\n示例：/agent exportpersonality\n续页示例：/agent exportpersonality my-persona 2\n使用结果中的 nextOffset 继续，-1 表示结束；示例 key 须替换为实际值。")
    @SubCommand(value = "exportpersonality", permission = CommandPermission.DEBUG)
    public CompletionStage<Void> exportPersonality(CommandData<ITeaNekoMessageData> command, @DefaultValue("") String key, @DefaultValue("0") int offset) {
        return respond(command, () -> personalityManagement().page(command.getRawData(), key, offset, false, true, command.getParentExecution()));
    }
    /**
     * 查询源 key 的最近备份引用，备份可通过 personality 或导出入口读取。
     * @param command DEBUG 来源
     * @param key 可选源 key
     * @param offset 页偏移
     * @return 备份查询反馈
     */
    // 2026/11/02：为 subhelp 注册该入口的中文用途说明。
    @Description("用法：/agent personalitybackups [源key] [偏移]\n需要 DEBUG 权限。分页列出源人格补正 key 的备份引用；省略 key 使用当前 scope 绑定，偏移默认 0、范围 0–64，每页最多 5 项。备份内容可交给 personality 或 exportpersonality 查看。\n示例：/agent personalitybackups\n续页示例：/agent personalitybackups my-persona 5\n示例 key 和后续偏移须使用实际查询结果。")
    @SubCommand(value = "personalitybackups", permission = CommandPermission.DEBUG)
    public CompletionStage<Void> personalityBackups(CommandData<ITeaNekoMessageData> command, @DefaultValue("") String key, @DefaultValue("0") int offset) {
        return respond(command, () -> personalityManagement().page(command.getRawData(), key, offset, true, false, command.getParentExecution()));
    }
    /**
     * 备份当前补正后只切换当前 scope，绑定新空 key 的状态版本为 -1。
     * @param command DEBUG 来源
     * @param key 目标 key
     * @param bindingRevision 原绑定版本
     * @param stateRevision 目标原状态版本
     * @return 真实绑定反馈
     */
    // 2026/11/02：为 subhelp 注册该入口的中文用途说明。
    @Description("用法：/agent setcorrectionkey <目标key> <bindingRevision> <目标stateRevision>\n需要 DEBUG 权限。备份当前补正后切换当前 scope 的人格补正绑定；新建目标 key 时目标版本填 -1，已有目标则使用其实际 stateRevision。记忆 namespace 不受影响。\n新建示例：/agent setcorrectionkey my-persona 2 -1\n先用 personality 查询当前绑定版本；绑定已有目标时另用 personality <目标key> 查询目标版本，示例 bindingRevision 须替换为实际值。")
    @SubCommand(value = "setcorrectionkey", permission = CommandPermission.DEBUG)
    public CompletionStage<Void> setCorrectionKey(CommandData<ITeaNekoMessageData> command, String key, long bindingRevision, long stateRevision) {
        return respond(command, () -> personalityManagement().change(command.getRawData(), Action.BIND, key, bindingRevision, stateRevision, null, null, null, null, command.getParentExecution()));
    }
    /**
     * 克隆指定补正或备份为独立可写 key，保存原来源，不自动改绑定。
     * @param command DEBUG 来源
     * @param key 源 key
     * @param bindingRevision 当前绑定版本
     * @param stateRevision 源状态版本
     * @return 新 key 和提交结果
     */
    // 2026/11/02：为 subhelp 注册该入口的中文用途说明。
    @Description("用法：/agent clonecorrections <源key> <bindingRevision> <源stateRevision>\n需要 DEBUG 权限。将指定补正或备份克隆为独立可写 key，保留原来源，返回新 key；不会自动切换当前 scope 绑定。需要使用新 key 时，再查询版本并调用 setcorrectionkey。\n示例：/agent clonecorrections my-persona 2 5\n先用 personality <源key> 获取当前绑定版本及源集合版本，示例值须替换为实际结果。")
    @SubCommand(value = "clonecorrections", permission = CommandPermission.DEBUG)
    public CompletionStage<Void> cloneCorrections(CommandData<ITeaNekoMessageData> command, String key, long bindingRevision, long stateRevision) {
        return respond(command, () -> personalityManagement().change(command.getRawData(), Action.CLONE, key, bindingRevision, stateRevision, null, null, null, null, command.getParentExecution()));
    }
    /**
     * 显式保存不可变补正备份，不修改现有 key。
     * @param command DEBUG 来源
     * @param key 源 key
     * @param bindingRevision 当前绑定版本
     * @param stateRevision 源状态版本
     * @return 完整备份反馈
     */
    // 2026/11/02：为 subhelp 注册该入口的中文用途说明。
    @Description("用法：/agent backuppersonality <源key> <bindingRevision> <源stateRevision>\n需要 DEBUG 权限。为指定人格补正创建不可变只读备份，不修改源 key 或当前绑定；可通过 personalitybackups 查询备份引用，再查看或导出备份。\n示例：/agent backuppersonality my-persona 2 5\n绑定版本和源集合版本先用 personality <源key> 查询，示例值须替换为实际结果。")
    @SubCommand(value = "backuppersonality", permission = CommandPermission.DEBUG)
    public CompletionStage<Void> backupPersonality(CommandData<ITeaNekoMessageData> command, String key, long bindingRevision, long stateRevision) {
        return respond(command, () -> personalityManagement().change(command.getRawData(), Action.BACKUP, key, bindingRevision, stateRevision, null, null, null, null, command.getParentExecution()));
    }
    /**
     * 新增有真实 DEBUG 来源的表达补正，字段空格可写为下划线。
     * @param command DEBUG 来源
     * @param key 当前补正 key
     * @param bindingRevision 原绑定版本
     * @param stateRevision 原集合版本
     * @param field 允许字段
     * @param text 完整补正词列表
     * @return 实际新增反馈
     */
    // 2026/11/02：为 subhelp 注册该入口的中文用途说明。
    @Description("用法：/agent addcorrection <当前key> <bindingRevision> <stateRevision> <字段> <正文>\n需要 DEBUG 权限。为当前人格添加表达补正；正文可包含空格，字段必须来自 personality 返回的可新增字段，字段内部空格用下划线代替。\n示例：/agent addcorrection my-persona 2 5 <可新增字段> 表达更简洁，减少重复说明\n请将 key、两个版本和字段占位符替换为 personality 的实际结果，每次修改后重新查询版本。")
    @SubCommand(value = "addcorrection", permission = CommandPermission.DEBUG)
    public CompletionStage<Void> addCorrection(CommandData<ITeaNekoMessageData> command, String key, long bindingRevision, long stateRevision, String field, List<String> text) {
        return respond(command, () -> personalityManagement().change(command.getRawData(), Action.ADD, key, bindingRevision, stateRevision, null, field, String.join(" ", text), null, command.getParentExecution()));
    }
    /**
     * 按集合版本编辑原记录，保留原主体、时刻、有效期和锁定状态。
     * @param command DEBUG 来源
     * @param key 当前 key
     * @param bindingRevision 原绑定版本
     * @param stateRevision 原集合版本
     * @param record 原补正 UUID
     * @param text 新正文词列表
     * @return 实际编辑反馈
     */
    // 2026/11/02：为 subhelp 注册该入口的中文用途说明。
    @Description("用法：/agent editcorrection <当前key> <bindingRevision> <stateRevision> <补正UUID> <新正文>\n需要 DEBUG 权限。按补正集合版本编辑已有记录正文，保留原主体、时刻、有效期和锁定状态；正文可包含空格。\n示例：/agent editcorrection my-persona 2 5 00000000-0000-4000-8000-000000000001 使用简洁自然的中文回答\n先用 personality 获取当前 key、绑定版本、集合版本和补正 UUID；示例值均须替换为实际结果。")
    @SubCommand(value = "editcorrection", permission = CommandPermission.DEBUG)
    public CompletionStage<Void> editCorrection(CommandData<ITeaNekoMessageData> command, String key, long bindingRevision, long stateRevision, String record, List<String> text) {
        return respond(command, () -> personalityManagement().change(command.getRawData(), Action.EDIT, key, bindingRevision, stateRevision, record, null, String.join(" ", text), null, command.getParentExecution()));
    }
    /**
     * 明确锁定或解锁原记录，普通学习不能授予或绕过锁定。
     * @param command DEBUG 来源
     * @param key 当前 key
     * @param bindingRevision 原绑定版本
     * @param stateRevision 原集合版本
     * @param record 原补正 UUID
     * @param locked 必须为 true 或 false
     * @return 实际锁定反馈
     */
    // 2026/11/02：为 subhelp 注册该入口的中文用途说明。
    @Description("用法：/agent lockcorrection <当前key> <bindingRevision> <stateRevision> <补正UUID> <true或false>\n需要 DEBUG 权限。true 锁定人格补正，false 解锁；普通学习不能绕过锁定。这里的 stateRevision 是补正集合版本，不是补正 UUID。\n锁定示例：/agent lockcorrection my-persona 2 5 00000000-0000-4000-8000-000000000001 true\n解锁将末尾改为 false；先用 personality 查询实际标识和版本，每次修改后重新查询。")
    @SubCommand(value = "lockcorrection", permission = CommandPermission.DEBUG)
    public CompletionStage<Void> lockCorrection(CommandData<ITeaNekoMessageData> command, String key, long bindingRevision, long stateRevision, String record, String locked) {
        return respond(command, () -> {
            if (!"true".equals(locked) && !"false".equals(locked)) { throw new IllegalArgumentException("锁定状态必须为 true 或 false。"); }
            return personalityManagement().change(command.getRawData(), Action.LOCK, key, bindingRevision, stateRevision, record, null, null, Boolean.valueOf(locked), command.getParentExecution());
        });
    }
    /**
     * 备份后撤销原补正，管理换代拒绝原快照的迟到学习。
     * @param command DEBUG 来源
     * @param key 当前 key
     * @param bindingRevision 原绑定版本
     * @param stateRevision 原集合版本
     * @param record 原补正 UUID
     * @return 实际撤销反馈
     */
    // 2026/11/02：为 subhelp 注册该入口的中文用途说明。
    @Description("用法：/agent revokecorrection <当前key> <bindingRevision> <stateRevision> <补正UUID>\n需要 DEBUG 权限。备份后撤销指定人格补正，并使原管理快照下的迟到学习失效；不会删除整个人格 key。\n示例：/agent revokecorrection my-persona 2 5 00000000-0000-4000-8000-000000000001\n先用 personality 获取实际 key、绑定版本、集合版本和补正 UUID；示例值须替换为查询结果。")
    @SubCommand(value = "revokecorrection", permission = CommandPermission.DEBUG)
    public CompletionStage<Void> revokeCorrection(CommandData<ITeaNekoMessageData> command, String key, long bindingRevision, long stateRevision, String record) {
        return respond(command, () -> personalityManagement().change(command.getRawData(), Action.REMOVE, key, bindingRevision, stateRevision, record, null, null, null, command.getParentExecution()));
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
     * 未装配时明确拒绝，不退回任意文件读取。
     * @return 实际 Skill 命令边界
     * @throws IllegalStateException 当当前宿主未装配时
     */
    private AgentSkillCommands skillCommands() {
        if (skillCommands == null) { throw new IllegalStateException("当前宿主未装配 Skill 能力。"); }
        return skillCommands;
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
     * 未装配时明确拒绝，不自行创建无权限策略的任务服务。
     * @return 已安装任务入口
     * @throws IllegalStateException 当宿主缺少任务服务时
     */
    private AgentTaskCommands taskCommands() {
        if (taskCommands == null) { throw new IllegalStateException("当前宿主未装配提醒任务。"); }
        return taskCommands;
    }

    /**
     * 旧宿主缺少人格管理时明确拒绝，不将普通入口升级为任意 key 写入。
     * @return 实际管理边界
     * @throws IllegalStateException 当宿主未装配人格管理时
     */
    private AgentPersonalityManagementCommands personalityManagement() {
        if (personalityManagement == null) { throw new IllegalStateException("当前宿主未装配人格管理。"); }
        return personalityManagement;
    }

    /**
     * 旧宿主未装配管理端口时明确拒绝，不绕过原管理权限。
     * @return 已装配的管理服务
     * @throws IllegalStateException 当管理服务缺失时
     */
    private AgentMemoryManagementCommands management() {
        if (memoryManagement == null) { throw new IllegalStateException("当前宿主未装配记忆管理。"); }
        return memoryManagement;
    }
    /**
     * 统一展开业务完成后发送反馈，异常正文及配置值不进入聊天。
     * @param command 原来源
     * @param work 非阻塞业务
     * @return 完整业务及实际发送阶段
     */
    private CompletionStage<Void> respond(CommandData<ITeaNekoMessageData> command, Supplier<CompletionStage<String>> work) {
        CompletionStage<String> stage;
        try { stage = work.get(); } catch (RuntimeException invalid) { stage = CompletableFuture.completedStage("操作未完成，请检查参数、权限、Agent 注册和当前状态。"); }
        return stage.exceptionally(failure -> "操作未完成；已开始的事务或外部动作不表示已回滚，请核对当前状态。")
                .thenCompose(text -> send(command, text));
    }
    /**
     * 长导出沿用原转发发送器，保持全部正文且不拆开 UTF-16 代理对。
     * @param command 原生来源
     * @param text 完整业务反馈
     * @return 全部分段发送完成信号
     */
    private CompletionStage<Void> send(CommandData<ITeaNekoMessageData> command, String text) {
        var data = command.getRawData();
        if (text.length() <= 3000) { return data.getMessageSender().sendReplyMessageAsync(text); }
        var parts = new java.util.ArrayList<String>();
        for (int start = 0; start < text.length();) {
            int end = Math.min(text.length(), start + 3000);
            if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) { end--; }
            parts.add(text.substring(start, end)); start = end;
        }
        return data.getClient().getTeaNekoToolbox().getMessageSenderTools().getForwardBuilder(data).addBotAllText(parts).sendByPartAsync(10);
    }
    /**
     * 原生聊天只去掉命令语法前缀，保留正文内部空白；派生命令不声明原文真实性。
     * @param command 真实原生数据及匹配参数
     * @param parsed 原分词结果，仅派生调用使用
     * @return 可作为当前原文的正文或显式派生文本
     * @throws IllegalArgumentException 当原消息与已匹配命令不一致时
     */
    private String chatText(CommandData<ITeaNekoMessageData> command, List<String> parsed) {
        if (command.getParentExecution() != null) { return String.join(" ", parsed); }
        var original = command.getRawData().getMessages().stream().map(ITeaNekoContent::getContentPart).filter(ITextTeaNekoContentPart.class::isInstance)
                .map(ITextTeaNekoContentPart.class::cast).map(ITextTeaNekoContentPart::getText).collect(Collectors.joining());
        var prefix = Pattern.compile("^\\s*" + Pattern.quote(command.getBody()) + " +chat(?: +|$)").matcher(original);
        if (!prefix.find()) { throw new IllegalArgumentException("原生聊天正文与已匹配命令不一致。"); }
        return original.substring(prefix.end());
    }
}
