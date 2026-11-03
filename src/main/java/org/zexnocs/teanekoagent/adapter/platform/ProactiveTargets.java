package org.zexnocs.teanekoagent.adapter.platform;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekoapp.client.TeaNekoClientScanner;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserService;
import org.zexnocs.teanekoapp.utils.TeaNekoScopeService;
import org.zexnocs.teanekocore.framework.pair.Pair;

import java.util.concurrent.CompletionStage;

/**
 * 自主发送的可信目标解析，不从模型输出接收客户端、群号或用户身份。
 * <br>2026/11/03：发送完成必须包含实际平台消息 ID。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.6.2-l
 */
@Service
public final class ProactiveTargets {
    private final TeaNekoClientScanner clients;
    private final TeaNekoScopeService scopes;
    private final ITeaUserService users;

    /**
     * 借用现有原生目录，构造不访问平台。
     * @param clients 客户端登记
     * @param scopes 原生 scope 解析
     * @param users 当前用户目录
     */
    public ProactiveTargets(TeaNekoClientScanner clients, TeaNekoScopeService scopes, ITeaUserService users) {
        this.clients = clients; this.scopes = scopes; this.users = users;
    }

    /**
     * 当前私聊必须有真实来源，不能从只有 UUID 的配置键猜测客户端。
     * @param data 已通过原生入口的消息
     * @return 固定目标
     */
    public Target from(ITeaNekoMessageData data) {
        var client = data.getClient(); var user = data.getUserData(); var scope = new AgentScope(client.getClientId(), data.getScopeId());
        if (scopes.getPrivateScopeId(user.getUuid()).equals(scope.scopeId())) {
            return new Target(scope, user.getUserIdInPlatform(), false, client.getTeaNekoToolbox().getSelfPlatformId().orElseThrow());
        }
        return group(scope.scopeId());
    }

    /**
     * 只有原框架能解析且自身身份已知的群配置才可重建定时目标。
     * @param scopeId 已登记的群 scope
     * @return 原群目标
     */
    public Target group(String scopeId) {
        var pair = scopes.fromGroupScopeId(scopeId); var client = pair.first();
        return new Target(new AgentScope(client.getClientId(), scopeId), pair.second(), true,
                client.getTeaNekoToolbox().getSelfPlatformId().orElseThrow());
    }

    /**
     * 每次使用前复核当前客户端、自身 ID 和私聊映射，旧目标不可改投新账号。
     * @param target 原固定目标
     * @return 当前同一客户端
     * @throws IllegalStateException 当目标不再有效时
     */
    public ITeaNekoClient verify(Target target) {
        var found = clients.getHandlerMap().values().stream().map(Pair::second)
                .filter(client -> client.getClientId().equals(target.scope().clientId())).distinct().toList();
        if (found.size() != 1) { throw new IllegalStateException("自主发言客户端未唯一登记。"); }
        var client = found.getFirst();
        if (client.getTeaNekoToolbox().getSelfPlatformId().filter(target.self()::equals).isEmpty()) { throw new IllegalStateException("自主发言自身身份已变化。"); }
        if (target.group()) {
            var pair = scopes.fromGroupScopeId(target.scope().scopeId());
            if (pair.first() != client || !pair.second().equals(target.id())) { throw new IllegalStateException("自主发言群组来源已变化。"); }
        } else {
            var user = users.get(client, target.id());
            if (user == null || !scopes.getPrivateScopeId(user).equals(target.scope().scopeId())) { throw new IllegalStateException("自主发言私聊映射已变化。"); }
        }
        return client;
    }

    /**
     * 使用原发送链且不自动重试或持久化补发，发送前再调用宿主当前守卫。
     * @param target 固定目标
     * @param text 已校验正文
     * @param guard 当前策略及取消检查
     * @return 真实平台回执阶段
     */
    public CompletionStage<Void> send(Target target, String text, Runnable guard) {
        guard.run(); var client = verify(target); var tools = client.getTeaNekoToolbox().getMessageSenderTools();
        var sender = target.group() ? tools.getGroupBuilder(target.id()) : tools.getPrivateBuilder(target.id());
        // 2026/11/03：自主发言也要求平台返回实际消息标识。
        sender.setRetryCount(0).setRecordFailed(false).addTextMessage(text); guard.run(); return AgentReplySender.confirm(sender.sendWithFuture());
    }

    /**
     * 仅保存数据的发送边界，不携带原始消息、客户端或借来的用户权限。
     * @param scope 实际作用域
     * @param id 原生目标
     * @param group 是否群目标
     * @param self 当时确认的机器人自身 ID
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public record Target(AgentScope scope, String id, boolean group, String self) { }
}
