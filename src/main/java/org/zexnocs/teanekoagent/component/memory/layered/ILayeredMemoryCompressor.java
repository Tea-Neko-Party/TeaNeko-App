package org.zexnocs.teanekoagent.component.memory.layered;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import java.util.concurrent.CompletionStage;

/**
 * 可替换的中期生成端口，模型只提供摘要，不决定身份、覆盖范围或提交水位。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.1-l
 * @version 4.8.0-l
 */
@FunctionalInterface
public interface ILayeredMemoryCompressor {
    /**
     * 在原控制和有界输入下生成压缩候选。
     * @param batch 宿主固定输入
     * @param control 借用工作控制
     * @return 模型实际完成后的摘要候选
     */
    CompletionStage<MemoryLayers.Digest> compress(MemoryLayers.Batch batch, ExecutionControl control);
}
