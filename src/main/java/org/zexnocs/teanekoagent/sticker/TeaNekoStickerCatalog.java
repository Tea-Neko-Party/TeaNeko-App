package org.zexnocs.teanekoagent.sticker;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明可扫描的目录后端，实现仍须是 Spring Bean。
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.1-l
 */
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
public @interface TeaNekoStickerCatalog {
    /** @return 稳定实现标识 */ String value();
    /** @return 契约版本 */ int version() default 1;
}
