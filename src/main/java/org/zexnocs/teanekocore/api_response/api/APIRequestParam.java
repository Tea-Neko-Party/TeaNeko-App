package org.zexnocs.teanekocore.api_response.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 用于构造 URL 请求参数的注解。
 *
 * @author zExNocs
 * @date 2026/02/17
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface APIRequestParam {
    /**
     * 参数名称，作为 URL 请求参数的键。
     * 如果为空，则默认使用变量名作为参数名称。
     * @return 参数名称
     */
    String value() default "";

    /**
     * 参数的默认值。
     * @return 参数的默认值
     */
    String defaultValue() default "";
}
