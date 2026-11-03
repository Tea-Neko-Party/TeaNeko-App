package org.zexnocs.teanekoapp.web.settings;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明已有 Spring 配置区的稳定键，发现与本地允许启用分离。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TeaNekoChatSettings {
    /** @return 稳定配置区标识 */
    String value();

    /** @return 支持的字段视图契约版本 */
    int version() default 1;
}
