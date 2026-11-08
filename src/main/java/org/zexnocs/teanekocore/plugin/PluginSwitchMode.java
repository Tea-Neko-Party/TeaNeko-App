package org.zexnocs.teanekocore.plugin;

/**
 * 插件代码已装配后的运行切换方式，不表示支持类加载或卸载。
 * <br>2026/11/07：区分受控热启停与下一次启动生效。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public enum PluginSwitchMode {
    /** 通过可重复生命周期立即切换新增业务资格。 */
    HOT,
    /** 保存意愿，下一次应用启动才改变实际状态。 */
    RESTART_REQUIRED
}
