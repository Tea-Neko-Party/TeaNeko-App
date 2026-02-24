package org.zexnocs.teanekocore.command.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 默认指令注解，用于标识一个方法为默认指令，即不需要任何的子指令即可执行的指令。
 * 即只需要 "主指令 参数" 的形式就能执行的指令。
 *
 * @author zExNocs
 * @date 2026/02/18
 * @since 4.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface DefaultCommand {
    /**
     * 指令的作用范围。
     * 默认与主指令相同。
     *
     * @return {@link CommandPermission }
     */
    CommandPermission permission() default CommandPermission.DEFAULT;

    /**
     * 指令的作用域。
     * 默认与主指令相同。
     * 如果与主指令不同，则只遵循该指令的作用域，包括在 Scope Manager 中的定义域也是独立的。
     *
     * @return {@link CommandScope }
     */
    CommandScope scope() default CommandScope.DEFAULT;

    /**
     * 指令作用包。
     * 如果为空则遵循主指令的作用包。
     * 如果不为空，则只遵循该指令的作用包。
     *
     * @return {@link String[] }
     */
    String[] permissionPackage() default {};
}
