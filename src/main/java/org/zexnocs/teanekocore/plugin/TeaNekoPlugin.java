package org.zexnocs.teanekocore.plugin;

import org.springframework.stereotype.Component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明业务插件的静态身份与依赖，实例仍由 Spring 创建并遵守条件装配。
 * <br>2026/11/07：插件标识使用独立 id 属性，不把稳定业务标识解释为 Bean 名称。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Component
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TeaNekoPlugin {
    /** @return 全局唯一插件 ID，与 Spring Bean 名称无关 */
    String id();

    /** @return 中文显示名称 */
    String name();

    /** @return 插件功能和使用边界 */
    String description();

    /** @return 当前实现版本 */
    String version();

    /** @return 所需宿主插件 SPI 版本 */
    int apiVersion() default 1;

    /** @return 必需业务插件或宿主能力的稳定标识 */
    String[] dependencies() default {};

    /** @return 必需已安装客户端的稳定标识，暂时断线不等同依赖缺失 */
    String[] clients() default {};

    /** @return 管理目录中的功能分类 */
    String category() default "其他";

    /** @return 该插件是否能在当前进程完成可重复启停 */
    PluginSwitchMode switchMode() default PluginSwitchMode.RESTART_REQUIRED;
}
