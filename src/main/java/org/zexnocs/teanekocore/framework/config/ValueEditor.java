package org.zexnocs.teanekocore.framework.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明配置值的编辑方式；只影响表单，不改变序列化类型、校验和敏感字段保护。
 * <br>2026/11/06：字段声明优先于值类型声明，未指定时按原字段种类选择。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.4-l
 * @version 4.8.4-l
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.METHOD})
public @interface ValueEditor {
    /** @return 编辑器的稳定标识；前端可通过注册表扩展。 */
    String value();
}
