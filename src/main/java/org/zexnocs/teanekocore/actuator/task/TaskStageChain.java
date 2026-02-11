package org.zexnocs.teanekocore.actuator.task;

/**
 * 基础的任务执行阶段链。
 * 阶段连的执行应该在一个线程中，不应该横跨线程。
 * 执行顺序为 阶段A → 阶段B → ... → 阶段N → 任务 → 阶段N → ... → 阶段B → 阶段A。
 * @author zExNocs
 * @date 2026/02/11
 */
public class TaskStageChain {
}
