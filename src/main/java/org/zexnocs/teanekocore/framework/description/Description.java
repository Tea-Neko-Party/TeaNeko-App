package org.zexnocs.teanekocore.framework.description;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 统一描述注解，用于为类、方法或字段提供描述信息。
 *
 * @author zExNocs
 * @date 2026/02/10
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
public @interface Description {
    /**
     * 描述内容。
     * @return 描述内容。
     */
    String value();
}
