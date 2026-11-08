package org.zexnocs.teanekoapp.web.plugins;

import java.util.Map;

/**
 * 插件声明管理资源的稳定迁移映射，宿主只迁移原本已经授权的精确键。
 * <br>2026/11/07：迁移不自动授予第三方新增资源权限。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public interface IManagementResourceMigration {
    /** @return 接收迁移资源的已注册插件 ID */
    String pluginId();

    /** @return 旧资源键到本插件新资源键的精确映射 */
    Map<String, String> resourceMappings();
}
