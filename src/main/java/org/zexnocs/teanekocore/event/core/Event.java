package org.zexnocs.teanekocore.event.core;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 事件注解。
 * 用于获取被扫描注册的 key 和 用于获取事件执行的命名空间。
 * 因为是数据类，所以注册为 bean 的作用就是被扫描到以及注入需要用到的 bean。
 * 因此需要至少一个支持注入(没有参数或者参数都是 bean)的构造函数来进行初始化，并标记 @Autowired。
 * <p>
 * 该注解的唯一作用是可以被 scanner 扫描到。
 * 如果不需要被扫描，则可以不使用该标记。
 *
 * @author zExNocs
 * @date 2026/02/17
 * @since 4.0.0
 */
@Component
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Event {
    /**
     * 被扫描注册的 key。
     * 如果为空则不会被注册。
     * @return key
     */
    @AliasFor(annotation = Component.class, attribute = "value")
    String value();

    /**
     * 事件的执行阶段命名空间。
     * 不是事件的名称。
     * 如果你不知道这个参数的作用，请使用默认值。
     * @return 命名空间
     */
    String namespace() default "";
}
