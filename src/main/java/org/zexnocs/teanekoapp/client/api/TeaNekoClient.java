package org.zexnocs.teanekoapp.client.api;

import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个类为 TeaNeko 客户端组件。
 * 被标记的类将被自动扫描并注册到 TeaNeko 客户端中。
 *
 * @author zExNocs
 * @date 2026/02/26
 * @since 4.0.9
 */
@Component
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TeaNekoClient {
    /**
     * Tea Neko Client 的唯一标识符。
     * <p>用于作为其 web 的后缀。
     *
     * @return {@link String }
     */
    @AliasFor(annotation = Component.class, attribute = "value")
    String value();

    /**
     * Client 的描述信息。
     * 可选参数，用于提供关于 Client 的额外信息。
     *
     * @return {@link String }
     */
    String description() default "";

    /**
     * 是否启用该客户端。
     *
     * @return boolean
     */
    boolean enabled() default true;
}
