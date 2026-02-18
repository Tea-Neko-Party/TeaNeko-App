package org.zexnocs.teanekocore.command.core;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 指令解析器注解。
 * 用于注册指令解析器给 scanner。
 *
 * @author zExNocs
 * @date 2026/02/18
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Component
public @interface CommandParser {
    /// 解析器的唯一标识
    @AliasFor(annotation = Component.class, attribute = "value")
    String value();
}
