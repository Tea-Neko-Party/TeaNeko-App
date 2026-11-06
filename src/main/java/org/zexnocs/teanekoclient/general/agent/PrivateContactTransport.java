package org.zexnocs.teanekoclient.general.agent;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.component.contact.IPrivateContactTransport;
import org.zexnocs.teanekoagent.component.expression.adapter.AgentReplySender;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoapp.client.TeaNekoClientScanner;
import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserService;
import org.zexnocs.teanekoapp.utils.TeaNekoScopeService;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * 通用客户端私聊适配，使用已发布注册表及原发送器，关闭自动重发。
 * <br>2026/11/06：发送前复核 UUID 映射及机器人账号，不跨平台猜测目标。
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.8.6-l
 */
@Service
public final class PrivateContactTransport implements IPrivateContactTransport {
    private final TeaNekoClientScanner clients;
    private final ITeaUserService users;
    private final TeaNekoScopeService scopes;

    /**
     * @param clients 客户端缓存
     * @param users 原映射服务
     * @param scopes 原作用域服务
     */
    public PrivateContactTransport(TeaNekoClientScanner clients, ITeaUserService users, TeaNekoScopeService scopes) {
        this.clients = clients;
        this.users = users;
        this.scopes = scopes;
    }

    /**
     * @param source 真实来源
     * @param user 原用户
     * @return 已登记且自身身份已知的目标
     */
    @Override public Optional<Target> resolve(AgentScope source, UUID user) {
        var entry = clients.getPair(source.clientId());
        if (entry == null) { return Optional.empty(); }
        var client = entry.second();
        var platform = users.getPlatformId(client, user);
        if (platform == null || platform.isBlank()) { return Optional.empty(); }
        return client.getTeaNekoToolbox().getSelfPlatformId()
                .map(self -> new Target(new AgentScope(source.clientId(), scopes.getPrivateScopeId(user)), user, platform, self));
    }

    /**
     * @param scope 原私聊
     * @param platformId 原平台用户
     * @return 不创建用户的映射结果
     */
    @Override public Optional<UUID> user(AgentScope scope, String platformId) {
        var entry = clients.getPair(scope.clientId());
        if (entry == null) { return Optional.empty(); }
        return Optional.ofNullable(users.get(entry.second(), platformId))
                .filter(id -> scopes.getPrivateScopeId(id).equals(scope.scopeId()));
    }

    /**
     * @param target 原目标
     * @param text 正文
     * @param guard 当前检查
     * @return 不重试的真实回执
     */
    @Override public CompletionStage<Void> send(Target target, String text, Runnable guard) {
        guard.run();
        if (resolve(target.scope(), target.user()).filter(target::equals).isEmpty()) {
            throw new IllegalStateException("私聊目标的客户端、用户映射或机器人身份已变化。");
        }
        var entry = clients.getPair(target.scope().clientId());
        if (entry == null) { throw new IllegalStateException("私聊客户端已离线。"); }
        var sender = entry.second().getTeaNekoToolbox().getMessageSenderTools().getPrivateBuilder(target.platformId());
        sender.setRetryCount(0).setRecordFailed(false).addTextMessage(text);
        guard.run();
        return AgentReplySender.confirm(sender.sendWithFuture());
    }
}
