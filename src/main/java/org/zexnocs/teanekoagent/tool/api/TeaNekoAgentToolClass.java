package org.zexnocs.teanekoagent.tool.api;

import java.lang.annotation.*;

/**
 * 标记已有 Spring Bean 的 Agent 工具方法，不要求实现工具接口或额外手写能力包。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.1-l
 * @version 4.6.0-l
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
}
