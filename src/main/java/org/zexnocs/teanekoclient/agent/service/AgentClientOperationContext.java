package org.zexnocs.teanekoclient.agent.service;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.integration.message.NativeMessageInput;
import org.zexnocs.teanekoagent.runtime.AgentActorContext;
import org.zexnocs.teanekoagent.runtime.context.IAgentContextReference;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import java.util.UUID;

/**
 * 客户端固定的真实操作上下文；插件只借用来源、控制和可选业务引用。
 * <br>2026/11/07：集中保留身份、生命周期及插件代次检查，不进入传输或持久数据。
 *
 * @param id 稳定操作身份
 * @param source 真实原生来源
 * @param actor 固定来源身份
 * @param input 固定原文及图文
 * @param field 调用方传入的操作字段，由具体处理器校验
 * @param reference 可选业务上下文引用
 * @param control 客户端拥有的控制，插件不得关闭
 * @param inherited 是否继承调用方的执行预算
 * @param verification 当前身份、配置和插件代次复核
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@JsonIgnoreType
public record AgentClientOperationContext(UUID id, ITeaNekoMessageData source, AgentActorContext actor,
        NativeMessageInput input, String field, @Nullable IAgentContextReference reference,
        ExecutionControl control, boolean inherited, Runnable verification) {
    /** @throws RuntimeException 当前操作资格或执行控制已失效时 */
    public void check() {
        verification.run();
    }
}
