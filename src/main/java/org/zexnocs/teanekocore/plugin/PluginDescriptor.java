package org.zexnocs.teanekocore.plugin;

import java.util.Objects;
import java.util.Set;

/**
 * 插件静态元数据；实现版本与宿主 SPI 版本分别声明。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/07：补充公开介绍及运行切换方式，保留原构造入口。
 *
 * @param id 全局稳定标识
 * @param version 插件实现版本
 * @param apiVersion 宿主 SPI 版本，当前为一
 * @param dependencies 必需插件标识
 * @param mandatory 是否为不可通过逻辑开关关闭的宿主基础模块
 * @param name 中文显示名称
 * @param description 功能与使用边界说明
 * @param category 插件分类
 * @param switchMode 已装配插件的切换方式
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.0-l
 * @version 4.9.0-l
 */
public record PluginDescriptor(String id, String version, int apiVersion, Set<String> dependencies, boolean mandatory,
                               String name, String description, String category, PluginSwitchMode switchMode) {
    /**
     * 创建可逻辑启停的普通插件声明。
     * @param id 稳定标识
     * @param version 实现版本
     * @param apiVersion SPI 版本
     * @param dependencies 必需依赖
     */
    public PluginDescriptor(String id, String version, int apiVersion, Set<String> dependencies) {
        this(id, version, apiVersion, dependencies, false);
    }

    /**
     * 保留旧模块声明；未声明可重复生命周期的普通插件默认在重启后切换。
     * @param id 稳定插件标识
     * @param version 实现版本
     * @param apiVersion 宿主 SPI 版本
     * @param dependencies 必需插件标识
     * @param mandatory 是否为不可关闭的宿主模块
     */
    public PluginDescriptor(String id, String version, int apiVersion, Set<String> dependencies, boolean mandatory) {
        this(id, version, apiVersion, dependencies, mandatory, id, "", "其他",
                mandatory ? PluginSwitchMode.HOT : PluginSwitchMode.RESTART_REQUIRED);
    }
    /**
     * 固定依赖，拒绝无效身份和不支持的 SPI。
     * @param id 插件标识
     * @param version 实现版本
     * @param apiVersion SPI 版本
     * @param dependencies 必需依赖
     * @param mandatory 是否为必需宿主模块
     * @param name 显示名称
     * @param description 功能说明
     * @param category 分类
     * @param switchMode 运行切换方式
     * @throws IllegalArgumentException 当声明无效时
     */
    public PluginDescriptor {
        requireId(id);
        Objects.requireNonNull(version, "插件版本不能为空。");
        Objects.requireNonNull(name, "插件名称不能为空。");
        Objects.requireNonNull(description, "插件介绍不能为空。");
        Objects.requireNonNull(category, "插件分类不能为空。");
        Objects.requireNonNull(switchMode, "插件切换方式不能为空。");
        dependencies = Set.copyOf(dependencies);
        dependencies.forEach(PluginDescriptor::requireId);
        if (version.isBlank() || apiVersion != 1 || dependencies.contains(id)) {
            throw new IllegalArgumentException("插件版本、SPI 或依赖声明无效：" + id);
        }
        if (name.isBlank() || category.isBlank()) {
            throw new IllegalArgumentException("插件名称或分类不能为空：" + id);
        }
    }

    /**
     * 检查稳定标识，禁止依赖 Java 类名定位插件。
     * @param id 待验证标识
     * @throws IllegalArgumentException 当格式无效时
     */
    public static void requireId(String id) {
        if (id == null || !id.matches("[a-z][a-z0-9.-]{0,63}")) {
            throw new IllegalArgumentException("插件标识格式无效。");
        }
    }
}
