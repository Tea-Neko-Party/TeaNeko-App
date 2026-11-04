package org.zexnocs.teanekoagent.component;

import org.zexnocs.teanekocore.framework.composition.IEntitySystem;
import org.zexnocs.teanekocore.framework.composition.SystemContext;
import org.zexnocs.teanekocore.framework.composition.SystemDescriptor;
import org.zexnocs.teanekocore.framework.composition.SystemResult;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * 固定宿主系统的声明与执行函数，执行资源仍由宿主拥有，不序列化业务服务。
 *
 * 修改记录：<br>
 * 2026/11/04：从轮次管线移入组件目录，统一系统声明的分类。
 *
 * @param descriptor 数据依赖和写入范围
 * @param action 单个可等待步骤
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
public record AgentPipelineStep(SystemDescriptor descriptor,
                                Function<SystemContext, CompletionStage<SystemResult>> action) implements IEntitySystem {
    /**
     * 将只读上下文交给显式步骤，不直接修改实体。
     * @param context 当前快照及执行控制
     * @return 由 Core 校验并提交的结果
     */
    @Override
    public CompletionStage<SystemResult> execute(SystemContext context) {
        return action.apply(context);
    }
}
