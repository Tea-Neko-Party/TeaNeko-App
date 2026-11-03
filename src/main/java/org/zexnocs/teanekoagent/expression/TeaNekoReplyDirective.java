package org.zexnocs.teanekoagent.expression;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明协议内指令解析器，不授予发送权限。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.2-l
 */
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
public @interface TeaNekoReplyDirective {
    /** @return 指令名 */ String value();
    /** @return 所属协议 */ String protocol() default "teaneko:expression";
    /** @return 语法版本 */ int version() default 1;
}
