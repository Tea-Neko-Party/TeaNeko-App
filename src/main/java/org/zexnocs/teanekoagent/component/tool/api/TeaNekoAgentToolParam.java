package org.zexnocs.teanekoagent.component.tool.api;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 补充工具参数命名和根 DTO 展开，默认值继续复用 Core 的 DefaultValue。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.1-l
 * @version 4.8.0-l
 */
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.PARAMETER)
public @interface TeaNekoAgentToolParam {
    /** @return 模型参数名，空时使用编译保留的 Java 参数名 */
    String value() default "";
    /** @return 是否必填，可空声明或 DefaultValue 仍使其成为可选参数 */
    boolean required() default true;
    /** @return 是否将唯一业务 DTO 的字段作为根参数，可信上下文不计入业务参数 */
    boolean flatten() default false;
    /** @return 字符串参数允许的值，空集合不增加枚举限制 */
    String[] allowedValues() default {};
}
