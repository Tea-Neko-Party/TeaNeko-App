package org.zexnocs.teanekocore.plugin;

/**
 * 随应用装配的插件入口；插件 Bean 和资源仍由 Spring 生命周期拥有。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.0-l
 * @version 4.6.0-l
 */
@FunctionalInterface
public interface PluginModule {
    /**
     * 返回插件不可变声明，不在此处启动业务或访问远程服务。
     * @return 插件声明
     */
    PluginDescriptor descriptor();
}
