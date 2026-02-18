package org.zexnocs.teanekocore.command.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 默认值注解。
 * 请将该注解放在靠前的参数上，以指定默认值。
 * 优先跳过最前面带有该注解的参数。
 *
 * @author zExNocs
 * @date 2026/02/18
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface DefaultValue {
    /// 默认值。
    String value();
}
