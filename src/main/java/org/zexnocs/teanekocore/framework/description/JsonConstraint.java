package org.zexnocs.teanekocore.framework.description;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段或参数的可执行 JSON 约束，与自然语言说明分开声明。
 * <br>2026/11/06：提供字符串长度、数组数量和字符串枚举，负一表示未限制。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.8.6-l
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
public @interface JsonConstraint {
    /** @return 最少 Unicode 字符数，负一表示未限制 */
    int minLength() default -1;
    /** @return 最多 Unicode 字符数，负一表示未限制 */
    int maxLength() default -1;
    /** @return 最少数组元素数，负一表示未限制 */
    int minItems() default -1;
    /** @return 最多数组元素数，负一表示未限制 */
    int maxItems() default -1;
    /** @return 允许的字符串值；空数组表示沿用类型自身的枚举 */
    String[] enumValues() default {};
}
