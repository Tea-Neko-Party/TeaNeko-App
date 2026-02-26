package org.zexnocs.teanekocore.actuator.task.api;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 储存任务阶段的命名空间列表。
 * 使用该注解必须要实现 {@link ITaskStage} 接口；否则会报错
 *
 * @since 4.0.0
 * @see ITaskStage
 * @author zExNocs
 * @date 2026/02/12
 */
@Component
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TaskStage {
    /**
     * 任务阶段的命名空间列表。
     *
     * @return 任务阶段的命名空间列表。
     */
    String[] value();

    /**
     * 任务阶段的优先级。
     * 越大的值表示优先级越高，越先被执行 (外皮层)。
     *
     * @return 任务阶段的优先级。
     */
    int priority() default 0;
}