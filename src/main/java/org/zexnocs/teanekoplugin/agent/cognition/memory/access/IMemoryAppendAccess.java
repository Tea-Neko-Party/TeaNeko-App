package org.zexnocs.teanekoplugin.agent.cognition.memory.access;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoplugin.agent.cognition.memory.fact.MemoryFactValue;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;

import java.util.concurrent.CompletionStage;

/**
 * 可信客户端使用的明确保存端口，业务授权和事务仍由记忆插件复核。
 * <br>2026/11/07：客户端依赖稳定契约，不依赖业务插件实现。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public interface IMemoryAppendAccess {
    /**
     * 保存真实用户当前消息明确提供的原文，完成阶段对应实际提交结果。
     * @param context 可信身份与记忆空间
     * @param actionId 宿主固定的动作标识
     * @param intent 当前原文中的明确保存意图
     * @param value 经校验的正文与时间
     * @param control 本阶段借用的执行控制
     * @return 真实提交回执
     */
    CompletionStage<MemoryAddResult> append(MemoryAccessContext context, String actionId,
            @Nullable MemoryAddIntent intent, MemoryFactValue value, ExecutionControl control);
}
