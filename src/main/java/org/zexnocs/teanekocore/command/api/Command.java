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
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Component
public @interface Command {
    /**
     * 指令名称，例如 "{/help, /帮助}"
     * 取第一个元素作为主要指令名称。
     * 如果指令名称第一个元素改变，其 ScopeManager 中白名单/黑名单需要重新设置。
     * @return 指令名称
     */
    String[] value();

    /**
     * 指令的作用范围。
     * 默认为 ALL。
     */
    CommandPermission permission() default CommandPermission.ALL;

    /**
     * 指令的作用域。
     * 默认群聊。
     */
    CommandScope scope() default CommandScope.GROUP;

    /**
     * 是否启动该指令。
     */
    boolean enable() default true;

    /**
     * 指令的模式。
     * 默认前缀模式。
     */
    CommandMode mode() default CommandMode.PREFIX;

    /**
     * 指令作用包。
     * 拥有其中任意一个指令包的用户都可以使用该指令。
     * 是指令的作用范围的一个额外补充。
     */
    String[] permissionPackage() default {};

    /**
     * 指令执行的命名空间。
     */
    String taskNamespace() default "";

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
