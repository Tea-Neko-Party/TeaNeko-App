package org.zexnocs.teanekoagent.llm.framework.model;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.llm.framework.message.LLMProtocolSnapshots;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelOptions;

/**
 * 可放入组件的只读模型参数快照，内部容器不向调用者暴露，嵌套数据也已冻结。
 * 工具执行绑定由交互调用单独提供，不随模型参数保存。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.9.0-l
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
