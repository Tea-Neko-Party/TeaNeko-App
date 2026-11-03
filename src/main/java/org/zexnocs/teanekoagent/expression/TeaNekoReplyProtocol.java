package org.zexnocs.teanekoagent.expression;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明完整协议解析器，须同时作为 Spring Bean 实现对应接口。
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.1-l
 */
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
public @interface TeaNekoReplyProtocol {
    /** @return 稳定协议 ID */ String value();
    /** @return 语法版本 */ int version() default 1;
}
