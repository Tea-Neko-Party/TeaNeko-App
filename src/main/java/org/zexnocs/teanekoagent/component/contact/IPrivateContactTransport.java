package org.zexnocs.teanekoagent.component.contact;

import org.zexnocs.teanekoagent.runtime.AgentScope;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * 只解析真实发起者在原客户端的已有私聊映射；具体发送由 Client 层适配。
 * <br>2026/11/06：模型不能填写任意收件人或切换客户端。
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.8.6-l
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
     * @version 4.8.6-l
     */
    record Target(AgentScope scope, UUID user, String platformId, String self) {}
}
