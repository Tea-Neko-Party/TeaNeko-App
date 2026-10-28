package org.zexnocs.teanekoagent.llm.framework.model;

import org.zexnocs.teanekoagent.llm.framework.message.LLMProtocolSnapshots;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelOptions;

/**
 * 可放入组件的只读模型参数快照，内部容器不向调用者暴露，嵌套数据也已冻结。
 * 工具执行绑定由交互调用单独提供，不随模型参数保存。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class LLMModelOptionsSnapshot {
    private final LLMModelOptions value;

    /**
     * 固定参数及透明数据，拒绝嵌套凭据和运行对象。
     * @param source 原始参数，空值表示无覆盖项
     */
    public LLMModelOptionsSnapshot(ILLMModelOptions source) {
        value = LLMProtocolSnapshots.options(source);
    }

    /**
     * 返回独立可调整的参数容器，修改它不影响本快照；嵌套集合继续保持只读。
     * @return 供模型调用或可信覆盖使用的参数副本
     */
    public LLMModelOptions toOptions() { return LLMModelOptions.copyOf(value); }
}
