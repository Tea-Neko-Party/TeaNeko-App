package org.zexnocs.teanekoagent.expression;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明一个客户端的明确图片与原生表情编码能力，须同时为 Spring Bean。
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.1-l
 */
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
public @interface TeaNekoExpressionClient{
    /** @return 精确客户端 ID */ String value();
    /** @return 编码契约版本 */ int version() default 1;
}
