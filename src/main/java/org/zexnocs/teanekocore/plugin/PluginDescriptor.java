package org.zexnocs.teanekocore.plugin;

import java.util.Objects;
import java.util.Set;

/**
 * 插件静态元数据；实现版本与宿主 SPI 版本分别声明。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @param id 全局稳定标识
 * @param version 插件实现版本
 * @param apiVersion 宿主 SPI 版本，当前为一
 * @param dependencies 必需插件标识
 * @param mandatory 是否为不可通过逻辑开关关闭的宿主基础模块
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.0-l
 * @version 4.6.0-l
 */
public record PluginDescriptor(String id, String version, int apiVersion, Set<String> dependencies, boolean mandatory) {
    /**
     * 创建可逻辑启停的普通插件声明。
     * @param id 稳定标识
     * @param version 实现版本
     * @param apiVersion SPI 版本
     * @param dependencies 必需依赖
     */
    public PluginDescriptor(String id, String version, int apiVersion, Set<String> dependencies) { this(id, version, apiVersion, dependencies, false); }
    /**
     * 固定依赖，拒绝无效身份和不支持的 SPI。
     * @param id 插件标识
     * @param version 实现版本
     * @param apiVersion SPI 版本
     * @param dependencies 必需依赖
     * @param mandatory 是否为必需宿主模块
     * @throws IllegalArgumentException 当声明无效时
     */
    public PluginDescriptor {
        requireId(id); Objects.requireNonNull(version, "插件版本不能为空。");
        dependencies = Set.copyOf(dependencies); dependencies.forEach(PluginDescriptor::requireId);
        if (version.isBlank() || apiVersion != 1 || dependencies.contains(id)) {
            throw new IllegalArgumentException("插件版本、SPI 或依赖声明无效：" + id);
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
