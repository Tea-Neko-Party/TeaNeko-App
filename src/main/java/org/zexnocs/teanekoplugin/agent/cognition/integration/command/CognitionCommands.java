package org.zexnocs.teanekoplugin.agent.cognition.integration.command;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.CommandExtension;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.command.api.DefaultValue;
import org.zexnocs.teanekocore.command.api.SubCommand;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekoplugin.agent.cognition.personality.PersonalityManagementCommand.Action;

import java.util.List;
import java.util.concurrent.CompletionStage;

import static org.zexnocs.teanekoagent.integration.command.AgentCommandSupport.respond;

/**
 * 认知插件贡献的 Agent 子命令，插件关闭时统一退出目录与执行。
 * <br>2026/11/07：子命令使用真实插件 Bean 和原权限事务。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
// 2026/11/07：共享命令路由与 Spring Bean 名分离，多个插件可以贡献同一前缀。
@CommandExtension(prefix = "/agent")
@PluginOwned("agent.cognition")
public final class CognitionCommands {
    private final AgentNamespaceCommands namespaces;
    private final AgentMemoryManagementCommands memoryManagement;
    private final AgentPersonalityManagementCommands personalityManagement;

    /**
     * @param namespaces 已授权的空间管理入口
     * @param memoryManagement 记忆管理事务
     * @param personalityManagement 人格管理事务
     */
    public CognitionCommands(AgentNamespaceCommands namespaces, AgentMemoryManagementCommands memoryManagement,
            AgentPersonalityManagementCommands personalityManagement) {
        this.namespaces = namespaces;
        this.memoryManagement = memoryManagement;
        this.personalityManagement = personalityManagement;
    }

    /**
     * 分页列出本人创建的空间，不能指定其他用户标识。
     * @param command 原真实来源
     * @param offset 非负分页偏移
     * @return 原分页反馈完成信号
     */
    // 2026/11/02：为 subhelp 注册该入口的中文用途说明。
    @Description("用法：/agent namespaces [偏移]\n分页列出本人创建的记忆空间 ID 和创建时间；偏移为非负整数，默认 0，续页使用结果中的下一页偏移。共享默认空间不一定出现在自建列表中，当前绑定请用 /agent status 查看。\n示例：/agent namespaces\n续页示例：/agent namespaces 5")
    @SubCommand("namespaces") public CompletionStage<Void> namespaces(CommandData<ITeaNekoMessageData> command, @DefaultValue("0") int offset) {
        return respond(command, () -> namespaces.namespaces(command.getRawData(), offset));
    }

    /**
     * 创建并切换新空记忆空间，旧空间和人格补正保留。
     * @param command 已经原 OWNER 链授权的真实来源
     * @return 实际提交及同步观察反馈
     */
    // 2026/11/02：为 subhelp 注册该入口的中文用途说明。
    @Description("用法：/agent reset\n需要 OWNER 权限。为当前作用域创建新的空记忆空间并切换绑定；旧空间保留，人格补正不受影响。适合开始使用独立记忆；若要清空共享空间中的数据，请查看 clear 的说明。\n示例：/agent reset")
    @SubCommand(value = "reset", permission = CommandPermission.OWNER) public CompletionStage<Void> reset(CommandData<ITeaNekoMessageData> command) {
        return respond(command, () -> namespaces.change(command.getRawData(), null, command.getParentExecution()));
    }

    /**
     * 绑定已有共享空间，遵守原 OWNER 权限，不要求目标必须属于本人。
     * @param command 原授权来源
     * @param namespace 已登记空间标识
     * @return 实际提交及同步观察反馈
     */
    // 2026/11/02：为 subhelp 注册该入口的中文用途说明。
    @Description("用法：/agent setnamespace <已有空间ID>\n需要 OWNER 权限。将当前作用域绑定到已登记的记忆空间，不要求目标空间由本人创建；不会复制或清空记录。同一 namespace 的记忆可跨 scope 共享，人格仍按 scope 绑定。\n示例：/agent setnamespace default\n操作后可用 /agent status 确认绑定；default 为默认共享空间。")
    @SubCommand(value = "setnamespace", permission = CommandPermission.OWNER) public CompletionStage<Void> setNamespace(CommandData<ITeaNekoMessageData> command, String namespace) {
        return respond(command, () -> namespaces.change(command.getRawData(), namespace, command.getParentExecution()));
    }

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
            if (!"true".equals(locked) && !"false".equals(locked)) {
                throw new IllegalArgumentException("锁定状态必须为 true 或 false。");
            }
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
            if (!"true".equals(locked) && !"false".equals(locked)) {
                throw new IllegalArgumentException("锁定状态必须为 true 或 false。");
            }
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
     * 旧宿主缺少人格管理时明确拒绝，不将普通入口升级为任意 key 写入。
     * @return 实际管理边界
     * @throws IllegalStateException 当宿主未装配人格管理时
     */
    private AgentPersonalityManagementCommands personalityManagement() {
        if (personalityManagement == null) {
            throw new IllegalStateException("当前宿主未装配人格管理。");
        }
        return personalityManagement;
    }

    /**
     * 旧宿主未装配管理端口时明确拒绝，不绕过原管理权限。
     * @return 已装配的管理服务
     * @throws IllegalStateException 当管理服务缺失时
     */
    private AgentMemoryManagementCommands management() {
        if (memoryManagement == null) {
            throw new IllegalStateException("当前宿主未装配记忆管理。");
        }
        return memoryManagement;
    }
}
