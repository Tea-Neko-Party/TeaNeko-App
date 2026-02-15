package org.zexnocs.teanekocore.database.easydata.core;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用于指定任务执行的 task stage chain。
 *
 * @author zExNocs
 * @date 2026/02/15
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface EasyData {
    /**
     * task stage chain 的 命名空间。
     * 注意不是 easy data 的命名空间。
     * 如果不知道这是什么，请不要设置，保持默认值即可。
     * @return stage chain 的命名空间。
     */
    String value() default "";
}
