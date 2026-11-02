package org.zexnocs.teanekoagent.tool.api;

import java.lang.annotation.*;

/**
 * 将公开实例方法暴露为 Agent 工具，方法和参数描述使用现有 Description。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.6.0-l
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
