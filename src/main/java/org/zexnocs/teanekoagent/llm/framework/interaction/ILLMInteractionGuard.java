package org.zexnocs.teanekoagent.llm.framework.interaction;

import org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationContext;

/**
 * 每次实际模型准入前的宿主守卫；运行策略不属于可导出模型选项或组件。
 * 工具副作用通过工具绑定的守卫独立检查，不从模型参数推导授权。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
@FunctionalInterface
public interface ILLMInteractionGuard {
    /**
     * 在阶段计时范围和生命周期锁外复核准入；禁止阻塞等待同一交互。
     * @param context 固定宿主绑定及当前模型阶段控制
     * @throws org.zexnocs.teanekoagent.llm.framework.LLMOperationRejectedException 当宿主拒绝继续时
     */
    void check(ToolInvocationContext context);
}
