package org.zexnocs.teanekocore.command.api;

import java.lang.annotation.*;

/**
 * 明确允许管理来源的方法，仍受原命令权限、客户端、作用域和插件约束。
 * 必须接收绑定管理上下文的 CommandData，不以原生消息替身获取权限。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ManagementCommand {
    /** @return 管理调用协议版本 */
    int version() default 1;

    /** @return 是否明确允许通过管理上下文向真实目标发送消息 */
    boolean sendsMessages() default false;
}
