package org.zexnocs.teanekocore.framework.composition;

import java.util.List;
import java.util.Objects;

/**
 * 顺序管线正常完成或被步骤主动停止的结果；异常/取消保持异常完成。
 *
 * @param snapshot 本步骤读取的不可变实体快照
 * @param stopped 是否由步骤主动停止后续执行
 * @param executedSystems 已经执行的步骤标识
 * @param skippedSystems 已经跳过的步骤标识
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record PipelineResult(
        EntitySnapshot snapshot,
        boolean stopped,
        List<String> executedSystems,
        List<String> skippedSystems
) {
    /**
     * 校验并固定当前实例的初始数据；集合在构造时复制，避免外部修改影响本次状态。
     *
     * @param snapshot 本步骤读取的不可变实体快照
     * @param stopped 是否由步骤主动停止后续执行
     * @param executedSystems 已经执行的步骤标识
     * @param skippedSystems 已经跳过的步骤标识
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    public PipelineResult {
        Objects.requireNonNull(snapshot, "snapshot");
        executedSystems = List.copyOf(executedSystems);
        skippedSystems = List.copyOf(skippedSystems);
    }
}
