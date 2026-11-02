package org.zexnocs.teanekocore.plugin;

import java.lang.annotation.*;

/**
 * 声明命令、事件监听器及任务阶段 Bean 的插件归属；不代替业务权限。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.6.0-l
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface PluginOwned {
    /**
     * 所属插件的稳定标识。
     * @return 已注册插件 ID
     */
    String value();
}
