package org.zexnocs.teanekocore.actuator.task.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 将一个方法强行设置为一个任务阶段。
 * 会跳过显式的阶段设置，直接将当前阶段设置为注解中指定的阶段。
 *
 * @author zExNocs
 * @date 2026/02/18
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SetTaskStage {
    /**
     * 任务阶段的名称。
     *
     * @return 任务阶段的名称
     */
    String value();
}
