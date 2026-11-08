package org.zexnocs.teanekoagent.runtime.delivery;

import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoagent.runtime.AgentRunResult;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * 把已冻结计划交付到可信平台目标的可选端口，不赋予额外目标或发送权限。
 * <br>2026/11/07：具体内容处理、逐段复核与回执保持在计划所属插件。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public interface IAgentReplyDelivery {
    /**
     * @param data 平台绑定的真实目标
     * @param request 原请求
     * @param result 原成功结果及固定计划
     * @param control 借用的交付控制
     * @param guard 实际副作用前的宿主复核
     * @return 全部实际发送阶段
     */
    CompletionStage<Void> send(ITeaNekoMessageData data, AgentRunRequest request, AgentRunResult result,
            ExecutionControl control, Supplier<CompletionStage<Void>> guard);
}
