package org.zexnocs.teanekocore.framework.composition;

import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;

import java.util.Objects;

/**
 * 不含可变实体句柄或服务定位器的单步骤上下文。
 *
 * @param snapshot 本步骤读取的不可变实体快照
 * @param control 借用的取消、截止时间和预算控制
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record SystemContext(EntitySnapshot snapshot, ExecutionControl control) {
    /**
     * 将当前不可变快照与借用执行控制交给单个步骤，不暴露实体的直接写入口。
     *
     * @param snapshot 本步骤读取的不可变实体快照
     * @param control 借用的取消、截止时间和预算控制
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    public SystemContext {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(control, "control");
    }
}
