package org.zexnocs.teanekoagent.component.tool.api;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import java.lang.annotation.*;

/**
 * 标记已有 Spring Bean 的 Agent 工具方法，不要求实现工具接口或额外手写能力包。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：声明工具所需的业务组件，不绕过客户端及权限条件。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.1-l
 * @version 4.8.0-l
 */
@Inherited @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
public @interface TeaNekoAgentToolClass {
    /** @return 可选能力 ID，空时按 Bean 身份生成稳定 ID */
    String value() default "";
    /** @return 所属插件，空时优先继承 PluginOwned，否则使用 Agent 宿主 */
    String pluginId() default "";
    /** @return 工具分类包，不参与授权 */
    String toolPackage() default "";
    /** @return 类级 client 范围，空集合允许全部 */
    String[] client() default {};
    /** @return 是否默认安装到使用宿主默认能力的实例 */
    boolean defaultEnabled() default true;
    /** @return 是否由既有 AgentFeature 显式调用解析器拥有，仅兼容组合能力使用 */
    boolean managedByFeature() default false;
    /** @return 2026/11/04：工具需要安装的组件；组件安装与实际业务权限分别检查。 */
    String[] requires() default {};
}
