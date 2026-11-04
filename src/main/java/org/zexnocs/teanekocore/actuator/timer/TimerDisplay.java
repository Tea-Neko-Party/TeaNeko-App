package org.zexnocs.teanekocore.actuator.timer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明定时来源的展示与管理意图，方法声明优先于所属类型，不改变原调度行为。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.1-l
 * @version 4.7.1-l
 */
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface TimerDisplay {
    /** @return 稳定分类标识，同一标识必须使用相同中文分类名 */
    String category();

    /** @return 中文分类名 */
    String categoryName();

    /** @return 中文任务名称，留空时保留原任务名 */
    String name() default "";

    /** @return 任务用途与执行边界 */
    String description() default "";

    /** @return 是否进入管理目录，不影响实际执行 */
    boolean visible() default true;

    /** @return 是否允许原 Core Timer 网页门禁；Spring 来源始终只读 */
    boolean controllable() default true;
}
