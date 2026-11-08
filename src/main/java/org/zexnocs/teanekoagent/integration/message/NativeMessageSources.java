package org.zexnocs.teanekoagent.integration.message;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.runtime.AgentActorContext;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 保存请求拥有的可信原生消息租约，供客户端和插件借用，不持有命令业务。
 * <br>2026/11/07：从命令适配中独立原生来源寿命，普通聊天不依赖自动化插件。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Service
public final class NativeMessageSources {
    private final Map<UUID, Lease> sources = new HashMap<>();

    /**
     * 可信宿主交接真实来源，租约必须保持到整个排队/执行阶段结束，容量不足明确失败。
     * @param request 尚未进入邮箱的原请求
     * @param data 实际原生消息对象
     * @return 不可序列化的来源拥有者
     * @throws IllegalArgumentException 当实际来源与请求不一致时
     * @throws IllegalStateException 当相同请求已绑定或来源容量已满时
     */
    public Lease open(AgentRunRequest request, ITeaNekoMessageData data) {
        if (!actor(data).equals(request.actor())) {
            throw new IllegalArgumentException("原生消息来源与请求不一致。");
        }
        synchronized (sources) {
            if (sources.containsKey(request.requestId()) || sources.size() >= 1000) {
                throw new IllegalStateException("原生消息来源已登记或容量已满。");
            }
            var lease = new Lease(request, data);
            sources.put(request.requestId(), lease);
            return lease;
        }
    }
    /**
     * 从真实原生消息形成数据身份，不允许模型指定用户、客户端或目标。
     * @param data 原消息
     * @return 固定发起者与作用域
     */
    public static AgentActorContext actor(ITeaNekoMessageData data) {
        return new AgentActorContext(new AgentScope(data.getClient().getClientId(), data.getScopeId()),
                AgentActorContext.Origin.USER, Optional.of(data.getUserData().getUuid()),
                data.getUserData().getUserIdInPlatform(), data.getMessageId(), data.getScopeId());
    }
    /**
     * 核对租约内原身份及原始文本，历史装配不会改变这两个来源数据。
     * @param request 本轮请求
     * @return 原来源仍被宿主持有时为真
     */
    public boolean available(AgentRunRequest request) {
        final Lease lease;
        synchronized (sources) {
            lease = sources.get(request.requestId());
        }
        return lease != null && !lease.closed && lease.request.actor().equals(request.actor())
                && Objects.equals(lease.request.userText(), request.userText())
                && actor(lease.data).equals(request.actor());
    }

    /**
     * 2026/11/02：仅向宿主平台适配器借出当前仍有效的原生来源，不进入组件或模型 JSON。
     * @param request 已固定的真实请求，不能由模型替换来源身份
     * @return 该请求仍拥有来源租约时的原始消息
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public Optional<ITeaNekoMessageData> source(AgentRunRequest request) {
        synchronized (sources) {
            var lease = sources.get(request.requestId());
            return lease != null && available(request) ? Optional.of(lease.data) : Optional.empty();
        }
    }
    /**
     * 原生来源拥有者，不保存到实体或数据库；关闭只移除自己的登记。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：租约随通用原生来源入口维护，插件只借用有效来源。
     *
     * @author zExNocs
     * @date 2026/11/01
     * @since 4.5.0-l
     * @version 4.9.0-l
     */
    @JsonIgnoreType
    public final class Lease implements AutoCloseable {
        private final AgentRunRequest request;
        private final ITeaNekoMessageData data;
        private volatile boolean closed;
        /**
         * 保存可信宿主的当前来源，不对外暴露消息或客户端。
         * @param request 原请求
         * @param data 原实际消息
         */
        private Lease(AgentRunRequest request, ITeaNekoMessageData data) {
            this.request = request;
            this.data = data;
        }
        /** 解除自身登记；不会取消已开始命令或关闭原客户端。 */
        @Override
        public void close() {
            synchronized (sources) {
                closed = true;
                sources.remove(request.requestId(), this);
            }
        }
    }
}
