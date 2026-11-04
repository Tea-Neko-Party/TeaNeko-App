package org.zexnocs.teanekoagent.component;

import org.springframework.stereotype.Component;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明可装配的 Agent 组件类型，业务标识不占用 Spring Bean 名。
 *
 * 修改记录：<br>
 * 2026/11/04：以注解声明组件参数协议及显式依赖。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.8.0-l
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Component
public @interface TeaNekoAgentComponent {
    /** @return 稳定组件标识。 */
    String id();
    /** @return 中文显示名称。 */
    String name();
    /** @return 职责及行为说明。 */
    String description();
    /** @return 参数协议版本。 */
    int version() default 1;
    /** @return 必须显式一起安装的组件，不偷偷扩展装配。 */
    String[] requires() default {};
}
