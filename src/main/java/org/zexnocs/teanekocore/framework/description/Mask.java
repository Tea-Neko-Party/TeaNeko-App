package org.zexnocs.teanekocore.framework.description;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 掩码注解，用于标记不希望展示具体值的字段。
 * 例如一些 api、密钥 等敏感信息，会使用掩码符进行掩码展示。
 *
 * @author zExNocs
 * @date 2026/02/11
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
public @interface Mask {
    /**
    * 掩码符，默认为 "*"
    * @return 掩码符
    */
    String value() default "*";
}
