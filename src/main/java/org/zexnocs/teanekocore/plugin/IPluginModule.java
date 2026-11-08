package org.zexnocs.teanekocore.plugin;

/**
 * 隔离装配使用的程序化插件声明；生产业务入口通过 TeaNekoPlugin 注解注册。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/07：统一接口名称，生产声明与业务实例创建分离。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.0-l
 * @version 4.9.0-l
 */
@FunctionalInterface
public interface IPluginModule {
    /**
     * 返回插件不可变声明，不在此处启动业务或访问远程服务。
     * @return 插件声明
     */
    PluginDescriptor descriptor();
}
