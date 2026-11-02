package org.zexnocs.teanekoagent.adapter.schedule;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.agent.AgentActorContext;
import org.zexnocs.teanekoagent.agent.access.IAgentScopePolicyPort;
import org.zexnocs.teanekoagent.schedule.AgentScheduleService;
import org.zexnocs.teanekoagent.schedule.AgentScheduledTask;
import org.zexnocs.teanekoapp.client.TeaNekoClientScanner;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserService;
import org.zexnocs.teanekoapp.utils.TeaNekoScopeService;
import org.zexnocs.teanekocore.framework.pair.Pair;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 原生提醒动作与当前身份策略；目标只由可信 scope 解析，不从动作参数接受客户端或权限。
 * 群身份每次查询平台，私聊核对原用户目录，不构造系统 DEBUG 或原生消息替身。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service
public final class NativeScheduleAdapter implements AgentScheduleService.Policy, AgentScheduleService.Action {
    private final IAgentScopePolicyPort policies;
    private final TeaNekoClientScanner clients;
    private final TeaNekoScopeService scopes;
    private final ITeaUserService users;
    private final Executor executor;

    /**
     * 借用现有目录、平台工具及执行器，构造不访问平台。
     * @param policies 当前 Agent 开关
     * @param clients 原客户端目录
     * @param scopes 原生 scope 解析
     * @param users 实际用户目录
     * @param executor 借用业务执行器
     */
    public NativeScheduleAdapter(IAgentScopePolicyPort policies, TeaNekoClientScanner clients, TeaNekoScopeService scopes,
            ITeaUserService users, @Qualifier("virtualExecutor") Executor executor) {
        this.policies = policies; this.clients = clients; this.scopes = scopes; this.users = users; this.executor = executor;
    }

    /**
     * 管理在禁用后仍可达，创建与执行必须当前启用；平台身份未知时拒绝。
     * @param task 固定任务
     * @param operation 管理或执行操作
     * @param actor 真实操作人，执行时为待重新验证的原创建者
     * @return 当前准入阶段
     */
    @Override public CompletionStage<Void> check(AgentScheduledTask task, AgentScheduleService.Operation operation, AgentActorContext actor) {
        return CompletableFuture.supplyAsync(() -> {
            validate(task, actor, operation);
            return destination(actor);
        }, executor).thenCompose(target -> {
            if (target.group()) {
                return target.client().getTeaNekoToolbox().getGroupMemberInfoSender().get(target.id(), actor.platformUserId())
                        .asStage().toCompletableFuture().orTimeout(10, TimeUnit.SECONDS).thenAcceptAsync(member -> {
                            var role = member == null ? null : member.getRole();
                            if (member == null || !actor.platformUserId().equals(member.getUserId()) || role == null
                                    || !Set.of("owner", "admin", "member").contains(role)) {
                                throw new IllegalStateException("无法确认当前群成员身份。");
                            }
                            validate(task, actor, operation);
                            requireOwnership(task, actor, operation, "owner".equals(role) || "admin".equals(role));
                            requireIdentity(target.client(), actor);
                        }, executor);
            }
            requireOwnership(task, actor, operation, false);
            return CompletableFuture.completedFuture(null);
        });
    }

    /**
     * 仅发送普通提醒；关闭发送重试和失败补发记录，真实回执未知则交由调度服务停止。
     * @param task 已认领的提醒
     * @param guard 每次副作用前的当前取消及权限检查
     * @return 实际平台发送结束后的结果
     */
    @Override public CompletionStage<AgentScheduleService.Outcome> execute(AgentScheduledTask task, Supplier<CompletionStage<Void>> guard) {
        return guard.get().thenComposeAsync(ignored -> {
            var actor = task.spec().creator(); var target = destination(actor);
            var tools = target.client().getTeaNekoToolbox().getMessageSenderTools();
            var sender = target.group() ? tools.getGroupBuilder(target.id()) : tools.getPrivateBuilder(target.id());
            sender.setRetryCount(0).setRecordFailed(false).addTextMessage("提醒：" + task.spec().arguments().get("text"));
            return guard.get().thenCompose(checked -> {
                var current = destination(actor);
                if (current.client() != target.client() || current.group() != target.group() || !current.id().equals(target.id())) {
                    throw new IllegalStateException("提醒发送目标已变更。");
                }
                return sender.sendAsync();
            })
                    .thenApply(sent -> AgentScheduleService.Outcome.COMPLETED);
        }, executor);
    }

    /**
     * 保持目标及操作来源固定，提醒参数不允许携带身份或目标覆盖项。
     * @param task 当前任务
     * @param actor 原生操作来源
     * @param operation 当前操作
     * @throws IllegalStateException 当来源、规格或当前开关失效时
     */
    private void validate(AgentScheduledTask task, AgentActorContext actor, AgentScheduleService.Operation operation) {
        var spec = task.spec(); var text = spec.arguments().get("text");
        if (actor.origin() != AgentActorContext.Origin.USER || !actor.scope().equals(spec.creator().scope())
                || !spec.creator().targetId().equals(spec.creator().scope().scopeId())
                || !"remind".equals(spec.action()) || !spec.arguments().keySet().equals(Set.of("text"))
                || text == null || text.isBlank() || text.length() > 2000) {
            throw new IllegalStateException("提醒任务的来源或参数无效。");
        }
        if (operation == AgentScheduleService.Operation.CREATE || operation == AgentScheduleService.Operation.EXECUTE) {
            var policy = policies.read(actor.scope());
            if (!policy.registered() || !policy.enabled() || !actor.equals(spec.creator())) {
                throw new IllegalStateException("提醒任务所在作用域未启用或创建者不匹配。");
            }
        }
    }

    /**
     * 管理只授予本人或实际群管理员，群管理员身份不成为 DEBUG。
     * @param task 当前任务
     * @param actor 当前操作人
     * @param operation 操作
     * @param administrator 本次平台查询确认的管理员
     * @throws IllegalStateException 当越权查看或取消时
     */
    private static void requireOwnership(AgentScheduledTask task, AgentActorContext actor, AgentScheduleService.Operation operation, boolean administrator) {
        if ((operation == AgentScheduleService.Operation.READ || operation == AgentScheduleService.Operation.CANCEL)
                && !task.spec().creator().userId().equals(actor.userId()) && !administrator) {
            throw new IllegalStateException("仅任务创建者或当前群管理员可查看和取消提醒。");
        }
    }

    /**
     * 原 scope 解析目标，核对 clientId 和用户目录；不按模型传入的群号发送。
     * @param actor 固定原生来源
     * @return 当前客户端和真实目标
     * @throws IllegalStateException 当客户端、身份或私聊边界不可确认时
     */
    private Destination destination(AgentActorContext actor) {
        if (actor.origin() != AgentActorContext.Origin.USER) { throw new IllegalStateException("提醒需要真实用户来源。"); }
        var candidates = clients.getHandlerMap().values().stream().map(Pair::second)
                .filter(client -> client.getClientId().equals(actor.scope().clientId())).distinct().toList();
        if (candidates.size() != 1) { throw new IllegalStateException("提醒客户端不可用或登记不唯一。"); }
        var client = candidates.getFirst(); requireIdentity(client, actor);
        if (scopes.getPrivateScopeId(actor.userId().orElseThrow()).equals(actor.scope().scopeId())) {
            return new Destination(client, actor.platformUserId(), false);
        }
        var group = scopes.fromGroupScopeId(actor.scope().scopeId());
        if (group.first() != client || !scopes.getGroupScopeId(client, group.second()).equals(actor.scope().scopeId())) {
            throw new IllegalStateException("提醒群组与原客户端不匹配。");
        }
        return new Destination(client, group.second(), true);
    }

    /**
     * 当前平台映射必须仍属于原 UUID，不自动创建未知用户。
     * @param client 当前客户端
     * @param actor 原生来源
     * @throws IllegalStateException 当映射失效时
     */
    private void requireIdentity(ITeaNekoClient client, AgentActorContext actor) {
        if (actor.platformUserId().isBlank() || !actor.userId().orElseThrow().equals(users.get(client, actor.platformUserId()))) {
            throw new IllegalStateException("提醒创建者的当前用户映射不可确认。");
        }
    }

    /**
     * 仅适配器短暂持有实际客户端，不写入任务或组件。
     * @param client 当前客户端
     * @param id 原生发送目标
     * @param group 是否群聊
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record Destination(ITeaNekoClient client, String id, boolean group) { }
}
