package org.zexnocs.teanekoapp.web.plugins;

import java.util.List;

/**
 * 插件向控制台贡献随应用构建的独立页面，不开放任意脚本地址。
 * <br>2026/11/07：通过稳定插件归属与页面声明复用宿主导航及授权。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public interface IPluginConsoleContribution {
    /** @return 已注册的所属插件 ID */
    String pluginId();

    /** @return 本插件提供的不可变页面声明 */
    List<PluginConsolePage> pages();
}
