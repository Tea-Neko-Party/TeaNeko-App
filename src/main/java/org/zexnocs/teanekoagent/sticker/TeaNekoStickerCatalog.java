package org.zexnocs.teanekoagent.sticker;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明可扫描的目录后端，实现仍须是 Spring Bean。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.6.2-l
 */
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
public @interface TeaNekoStickerCatalog {
    /** @return 稳定实现标识 */ String value();
    /** @return 契约版本 */ int version() default 1;
}
