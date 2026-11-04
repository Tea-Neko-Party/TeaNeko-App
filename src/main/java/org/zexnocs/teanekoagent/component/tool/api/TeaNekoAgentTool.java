package org.zexnocs.teanekoagent.component.tool.api;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 将公开实例方法暴露为 Agent 工具，方法和参数描述使用现有 Description。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.1-l
 * @version 4.8.0-l
 */
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
public @interface TeaNekoAgentTool {
    /** @return 工具名，空时使用 Java 方法名 */
    String value() default "";
    /** @return 允许的 client，空时沿用类声明；类也未声明时允许全部 */
    String[] client() default {};
    /** @return 可选条件方法名，签名为 boolean 方法(TeaNekoAgentToolContext)，选择及执行前后复核 */
    String condition() default "";
    /** @return 是否开启供应商严格 schema 模式 */
    boolean strict() default false;
}
