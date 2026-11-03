package org.zexnocs.teanekoagent.adapter.command;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.adapter.skill.AgentSkills;
import org.zexnocs.teanekoagent.agent.access.IAgentScopePolicyPort;
import org.zexnocs.teanekoagent.skill.ISkillCatalog;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ResourceBudget;
import org.zexnocs.teanekocore.command.api.CommandPermission;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 原生 Skill 查询和 DEBUG 重载边界，本地文件更新不会授予工具或执行任意代码。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.5.0-l
 */
@Service
public final class AgentSkillCommands {
    private final AgentSkills skills;
    private final ISkillCatalog catalogs;
    private final IAgentScopePolicyPort scopes;
    private final Executor executor;
    private final ScheduledExecutorService scheduler;
    /**
     * 借用现有业务域和截止时间设施，构造不加载目录。
     * @param skills Skill 运行适配
     * @param catalogs 可信目录
     * @param scopes 当前作用域准入
     * @param executor 借用业务执行器
     * @param scheduler 借用计时器
     */
    public AgentSkillCommands(AgentSkills skills, ISkillCatalog catalogs, IAgentScopePolicyPort scopes,
            @Qualifier("virtualExecutor") Executor executor, @Qualifier("taskExecutor") ScheduledExecutorService scheduler) {
        this.skills = skills; this.catalogs = catalogs; this.scopes = scopes; this.executor = executor; this.scheduler = scheduler;
    }
    /**
     * 读取当前允许列表或由真实 DEBUG 重载；重载在发布前复核授权，失败保留旧目录。
     * @param data 原生来源
     * @param reload 是否重载
     * @param parent 借用父控制
     * @return 等待操作和资源观察结束的反馈
     */
    public CompletionStage<String> execute(ITeaNekoMessageData data, boolean reload, @Nullable ExecutionControl parent) {
        var clock = Clock.systemUTC(); var control = parent == null
                ? ExecutionControl.withDeadline(new ResourceBudget(Map.of()), clock.instant().plusSeconds(30), clock, scheduler) : parent;
        try {
            guard(data, reload, control);
            var work = CompletableFuture.supplyAsync(() -> {
                guard(data, reload, control);
                if (!reload) { var text = skills.describe(NativeCommandBridge.actor(data).scope()); guard(data, false, control); return text; }
                var snapshot = catalogs.reload(() -> guard(data, true, control));
                return "Skill 完整目录已更新；数量=" + snapshot.skills().size() + "；指纹=" + snapshot.fingerprint()
                        + "。新轮使用新快照，已开始的旧轮会复核版本；没有工具权限变更。";
            }, executor);
            return control.guard(work).whenComplete((ignored, failure) -> { if (parent == null) { control.close(); } });
        } catch (RuntimeException failure) { if (parent == null) { control.close(); } return CompletableFuture.failedStage(failure); }
    }
    /**
     * 当前角色及作用域是唯一管理依据，不信任模型或 Skill 中声明的权限。
     * @param data 真实消息
     * @param reload 是否要求 DEBUG
     * @param control 当前执行控制
     * @throws IllegalStateException 当当前准入或管理权限失效时
     */
    private void guard(ITeaNekoMessageData data, boolean reload, ExecutionControl control) {
        control.checkActive();
        if (!scopes.read(NativeCommandBridge.actor(data).scope()).enabled()
                || reload && data.getUserData().getRole().getLevel() > CommandPermission.DEBUG.getLevel()) {
            throw new IllegalStateException("Skill 查询需要启用作用域，目录重载仅允许真实 DEBUG。");
        }
    }
}
