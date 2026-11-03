package org.zexnocs.teanekoagent.memory.layered;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.personality.StyleObservations;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * 可替换的中期到长期语义选择端口，不自行写库或生成授权身份。
 * <br>2026/11/03：增加联合观察入口，旧函数式实现继续只生成长期事实。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.1-l
 * @version 4.6.1-l
 */
@FunctionalInterface
public interface ILongTermMemoryConsolidator {
    /**
     * 选择值得长期保留的事实、偏好和经历。
     * @param batch 宿主固定的中期片段与真实证据
     * @param control 借用背景控制
     * @return 有界候选列表
     */
    CompletionStage<List<LongMemories.Candidate>> consolidate(LongMemories.Batch batch, ExecutionControl control);
    /**
     * 2026/11/03：同一模型调用可同时观察表达，旧适配器不自动产生风格。
     * @param batch 固定来源
     * @param target 可选宿主人格目标
     * @param control 原控制
     * @return 分离的事实与表达候选
     */
    default CompletionStage<StyleObservations.Analysis> analyze(LongMemories.Batch batch, StyleObservations.@Nullable Target target, ExecutionControl control) {
        return consolidate(batch, control).thenApply(values -> new StyleObservations.Analysis(values, List.of()));
    }
}
