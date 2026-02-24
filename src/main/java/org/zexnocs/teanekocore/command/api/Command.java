package org.zexnocs.teanekocore.command.api;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 指令注解，用于标记一个类为指令解释器。
 *
 * @author zExNocs
 * @date 2026/02/18
 * @since 4.0.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Component
public @interface Command {
    /**
     * 指令名称，例如 "{/help, /帮助}"
     * <p>取第一个元素作为主要指令名称。
     *
     * <p>如果指令名称第一个元素改变，则
     * {@link org.zexnocs.teanekocore.command.interfaces.ICommandPermissionManager}
     * 与
     * {@link org.zexnocs.teanekocore.command.interfaces.ICommandScopeManager}
     * 中与该指令相关的权限需要重新设置
     *
     * @return 指令名称
     */
    String[] value();

    /**
     * 指令的作用范围。
     * <p>默认为 DEBUG，表示只有调试权限的用户可以使用该指令。
     * <p>不要设置为 DEFAULT，否则所有用户都无法使用该指令。
     *
     * @return {@link CommandPermission }
     */
    CommandPermission permission() default CommandPermission.DEBUG;

    /**
     * 指令的作用域。
     * <p>默认所有作用域都可以使用该指令。
     * <p>不要设置为 DEFAULT，否则所有作用域都无法使用该指令。
     *
     * @return {@link CommandScope }
     */
    CommandScope scope() default CommandScope.ALL;

    /**
     * 是否启动该指令。
     *
     * @return boolean
     */
    boolean enable() default true;

    /**
     * 指令的模式。
     * 默认前缀模式。
     *
     * @return {@link CommandMode }
     */
    CommandMode mode() default CommandMode.PREFIX;

    /**
     * 指令作用包。
     * 拥有其中任意一个指令包的用户都可以使用该指令。
     * 是指令的作用范围的一个额外补充。
     *
     * @return {@link String[] }
     */
    String[] permissionPackage() default {};

    /**
     * 指令执行的命名空间。
     *
     * @return {@link String }
     */
    String taskNamespace() default "";

    /**
     * 指令模式枚举类，定义了指令的两种模式：前缀模式和正则模式。
     *
     * @author zExNocs
     */
    enum CommandMode {
        /**
         * 前缀模式。
         * 指令以前缀开头，例如 "/help"。
         */
        PREFIX,


        /**
         * 正则模式。
         */
        REGEX,
    }
}
