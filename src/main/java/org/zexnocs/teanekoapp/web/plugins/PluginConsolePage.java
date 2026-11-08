package org.zexnocs.teanekoapp.web.plugins;

import java.util.Set;

/**
 * 受信任的构建期页面声明；资源集合仅声明页面需要的权限，不授予权限。
 * <br>2026/11/07：页面 ID 与本地模块键分离，中文标题由插件提供。
 *
 * @param id 插件内唯一页面标识
 * @param title 中文标题
 * @param description 页面用途
 * @param moduleKey 已构建前端模块键
 * @param order 菜单排序
 * @param managementResources 页面必需的管理资源白名单键
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public record PluginConsolePage(String id, String title, String description, String moduleKey,
                                int order, Set<String> managementResources) {
    /**
     * 固定资源集合并拒绝不能安全映射为本地页面的声明。
     * @param id 插件内标识
     * @param title 中文标题
     * @param description 用途
     * @param moduleKey 本地模块键
     * @param order 排序
     * @param managementResources 必需资源
     * @throws IllegalArgumentException 当页面元数据无效时
     */
    public PluginConsolePage {
        if (id == null || !id.matches("[a-z][a-z0-9.-]{0,63}") || title == null || title.isBlank()
                || title.length() > 80 || description == null || description.length() > 2000
                || moduleKey == null || !moduleKey.matches("[a-z][a-z0-9.-]{0,63}:[a-z][a-z0-9.-]{0,63}")) {
            throw new IllegalArgumentException("插件页面声明无效。");
        }
        managementResources = Set.copyOf(managementResources);
    }
}
