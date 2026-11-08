package org.zexnocs.teanekoplugin.agent.expression.expression;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

import org.zexnocs.teanekoagent.runtime.delivery.ReplyCoordinator;

// 2026/11/07：表达业务归入插件，通用交付能力独立于业务组件。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.runtime.delivery.IAgentDeliveryLedger;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoagent.runtime.AgentRunResult;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * 将冻结计划交给原守卫、账本和类型发送器，不重新生成或解析。
 * <br>2026/11/03：异步资源交付继续携带原目标与守卫。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.9.0-l
 */
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.expression")
@Component
public final class ExpressionDelivery implements org.zexnocs.teanekoagent.runtime.delivery.IAgentReplyDelivery {
    private final ExpressionRuntime runtime;
    private final ReplyCoordinator coordinator;
    private final ObjectProvider<IAgentDeliveryLedger> receipts;
    /**
     * 借用已有服务，账本在实际发送时才取得。
     * @param runtime 表达运行时
     * @param coordinator 顺序协调器
     * @param receipts 原回执服务
     */
    public ExpressionDelivery(ExpressionRuntime runtime, ReplyCoordinator coordinator, ObjectProvider<IAgentDeliveryLedger> receipts) {
        this.runtime=runtime;this.coordinator=coordinator;this.receipts=receipts;
    }
    /**
     * 每段等待结束及账本占用后都由原宿主复核，未决不补发。
     * @param data 原发送目标
     * @param request 原请求
     * @param result 原成功结果
     * @param control 协调控制
     * @param guard 原目标、资料和授权检查
     * @return 全部真实交付阶段
     * @throws IllegalArgumentException 当计划不属于原请求时
     */
    public CompletionStage<Void> send(ITeaNekoMessageData data, AgentRunRequest request, AgentRunResult result, ExecutionControl control,
            Supplier<CompletionStage<Void>> guard) {
        if (!(result.replyPlan() instanceof ReplyPlans.Approved plan)
                || !plan.requestId().equals(request.requestId())){
                    throw new IllegalArgumentException("表达发送计划不属于原请求。");
                }
        runtime.verify(plan);
        return coordinator.send(control, plan.steps(), ReplyPlans.Step::waitMillis,(index,step)->{
            // 2026/11/03：资源读取完成后也复核原计划、账号和授权，不把预读取当作可发送许可。
            Supplier<CompletionStage<Void>> checking=()->guard.get().thenRun(()->{
                control.checkActive();runtime.verify(plan);
                coordinator.verifyTarget(control,request.actor().scope(),data.getClient().getTeaNekoToolbox().getSelfPlatformId().orElse(""));
            });
            Supplier<CompletionStage<Void>> sending=()->runtime.registry().send(new ReplySendContext(data,request,index,control,checking),step);
            var ledger=receipts.getIfAvailable();
            if(ledger==null && result.memoryReference()!=null){return CompletableFuture.failedStage(new IllegalStateException("持久化表达计划缺少平台回执服务。"));}
            return ledger==null?sending.get():ledger.sendRecorded(result.memoryReference(),request.actor(),request.requestId(),index,plan.steps().size(),step.text(),step.digest(),sending);
        });
    }
}
