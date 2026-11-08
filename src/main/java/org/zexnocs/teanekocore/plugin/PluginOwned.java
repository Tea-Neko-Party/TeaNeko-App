package org.zexnocs.teanekocore.plugin;

import java.lang.annotation.*;

/**
 * 声明命令、事件监听器及任务阶段 Bean 的插件归属；不代替业务权限。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/07：仅业务插件包允许声明，宿主能力使用独立身份合同。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.0-l
 * @version 4.9.0-l
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface PluginOwned {
    /**
     * 2026/11/07：所属业务插件的稳定标识，不接受宿主能力身份。
     * @return 已注册插件 ID
     */
    String value();
}
