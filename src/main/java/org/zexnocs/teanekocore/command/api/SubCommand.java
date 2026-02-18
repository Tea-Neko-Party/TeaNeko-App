package org.zexnocs.teanekocore.command.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 子指令注解，用于标识一个方法为子指令。
 * 简单说就是需要 "主指令 子指令 参数" 的形式才能执行的指令。
 *
 * @author zExNocs
 * @date 2026/02/18
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SubCommand {
    /**
     * 子指令的名称。
     */
    String[] value();

    /**
     * 指令的作用范围。
     * 默认为 ALL。
     */
    CommandPermission permission() default CommandPermission.DEFAULT;

    /**
     * 指令的作用域。
     * 默认群聊。
     */
    CommandScope scope() default CommandScope.DEFAULT;

    /**
     * 指令作用包。
     * 如果为空则遵循主指令的作用包。
     * 如果不为空，则只遵循该指令的作用包。
     */
    String[] permissionPackage() default {};
}
