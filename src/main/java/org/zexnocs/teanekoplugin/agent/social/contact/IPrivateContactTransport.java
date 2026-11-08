package org.zexnocs.teanekoplugin.agent.social.contact;

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.zexnocs.teanekoagent.runtime.AgentScope;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * 只解析真实发起者在原客户端的已有私聊映射，插件适配器复用客户端通用发送能力。
 * <br>2026/11/06：模型不能填写任意收件人或切换客户端。
 * <br>2026/11/07：业务归属与通用底座按职责分离，保留持久身份及完成边界。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.9.0-l
 */
public interface IPrivateContactTransport {
    /**
     * @param source 原聊天
     * @param user 真实用户
     * @return 已有目标，不创建用户
     */
    Optional<Target> resolve(AgentScope source, UUID user);
    /**
     * @param scope 已确认私聊
     * @param platformId 原平台用户
     * @return 已有且匹配该私聊的真实用户
     */
    Optional<UUID> user(AgentScope scope, String platformId);
    /**
     * @param target 固定目标
     * @param text 正文
     * @param guard 紧邻发送的配置和取消复核
     * @return 平台实际确认
     */
    CompletionStage<Void> send(Target target, String text, Runnable guard);

    /**
     * 已有私聊目标的不可变数据。
     * @param scope 私聊作用域
     * @param user 用户 UUID
     * @param platformId 原平台账号
     * @param self 机器人账号
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.6-l
     * @version 4.9.0-l
     */
    record Target(AgentScope scope, UUID user, String platformId, String self) {}
}
