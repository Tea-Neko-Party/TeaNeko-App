package org.zexnocs.teanekoagent.expression;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明协议内指令解析器，不授予发送权限。
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
public @interface TeaNekoReplyDirective {
    /** @return 指令名 */ String value();
    /** @return 所属协议 */ String protocol() default "teaneko:expression";
    /** @return 语法版本 */ int version() default 1;
}
