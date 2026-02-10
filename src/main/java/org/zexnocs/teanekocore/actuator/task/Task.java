package org.zexnocs.teanekocore.actuator.task;

/**
 * 由 TaskService 管理的异步 Supplier 任务，只能执行一次的任务。
 * TaskConfig 可以生成多个 Task 实例，每个实例只能执行一次。
 * 不允许外部直接创建 Task 实例，必须通过 TaskConfig 创建。
 * 具体请看 TaskConfig 文档。
 * @param <T> 任务结果的类型
 * @author zExNocs
 * @date 2026/02/10
 */
public class Task<T> {
}
