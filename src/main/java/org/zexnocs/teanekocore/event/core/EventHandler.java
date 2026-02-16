package org.zexnocs.teanekocore.event.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 事件的处理器注解。
 * 获取 IEventListener 中含有该注解的方法，并注册到 EventScanner 中。
 * <p>
 * 异步取消问题：
 * 1. 如果派发前事件被取消 (事件通知前 或者 高优先级事件处理时)，则该处理器不会被调用。
 * 2. 如果派发后事件被取消，则该处理器不会受到影响，仍然会被调用。
 *
 * @author zExNocs
 * @date 2026/02/17
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EventHandler {
    /**
     * 任务处理的阶段链命名空间
     * 只有当开启多线程时才会使用
     */
    String taskChainNamespace() default "";

    /**
     * 是否使用多线程处理该事件。
     * 如果为 true，则会使用多线程处理该事件。
     * 默认不使用多线程，而是在与事件通知的同一线程中处理。
     * 如果启动，则会使用 taskChainNamespace 作为任务阶段链的命名空间，但优先级会被忽略。
     * 如果不启动，则会使用优先级来决定事件的处理顺序，但是阶段链会被忽略。
     */
    boolean async() default false;

    /**
     * 事件的优先级。
     * 数字越大，优先级越高。
     * 默认值为 0。
     * 如果使用多线程，事件会被优先派发，但顺序不保证。
     */
    int priority() default 0;

    /**
     * 是否忽略事件被取消。
     * 如果为 true，则事件被取消后仍然会执行该方法。
     * 默认不忽略。
     */
    boolean ignoreCancelled() default false;
}
