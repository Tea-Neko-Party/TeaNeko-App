package org.zexnocs.teanekoagent.expression;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明一个客户端的明确图片与原生表情编码能力，须同时为 Spring Bean。
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
public @interface TeaNekoExpressionClient{
    /** @return 精确客户端 ID */ String value();
    /** @return 编码契约版本 */ int version() default 1;
}
