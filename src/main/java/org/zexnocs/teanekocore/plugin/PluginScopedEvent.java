package org.zexnocs.teanekocore.plugin;

/**
 * 限定内部事件只通知本轮实际安装的插件，避免全局 Bean 自动介入所有实例。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.6.0-l
 */
public interface PluginScopedEvent {
    /**
     * 判断插件监听器能否参与。
     * @param pluginId 监听器所属插件
     * @return 允许参与时为真
     */
    boolean acceptsPlugin(String pluginId);
    /**
     * 声明监听阶段是否允许异步观测，可修改决策必须为假。
     * @return 允许独立异步监听时为真
     */
    boolean allowsAsyncListeners();
}
