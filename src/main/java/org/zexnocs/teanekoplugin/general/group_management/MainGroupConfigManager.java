package org.zexnocs.teanekoplugin.general.group_management;

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import org.zexnocs.teanekocore.plugin.PluginOwned;

import org.zexnocs.teanekoapp.config.TeaNekoConfigNamespaces;
import org.zexnocs.teanekocore.database.configdata.api.IConfigKey;
import org.zexnocs.teanekocore.database.configdata.api.default_config.StringDefaultConfigData;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.Optional;

/**
 * 用于设置管理群默认监视/审核的群 ID
 *
 * <br>2026/11/04：提供简短中文名称并修正方法说明的 HTML 格式，配置行为不变。
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 *
 * @author zExNocs
 * @date 2026/03/18
 * @since 4.3.4
 * @version 4.9.0-l
 */
// 2026/11/04：显示名称与原稳定配置键分别维护。
@ConfigManager(value = "main-group-id", displayName = "主群设置", description = """
        配置附属管理群所管理的默认主群 ID""",
        configType = StringDefaultConfigData.class,
        namespaces = {TeaNekoConfigNamespaces.GROUP})
@Description("管理群入群请求的指令。")
@PluginOwned("general.group-management")
public class MainGroupConfigManager {
    private final IConfigDataService iConfigDataService;

    public MainGroupConfigManager(IConfigDataService iConfigDataService) {
        this.iConfigDataService = iConfigDataService;
    }

    /**
     * 根据 scope ID 获取 config
     * 2026/11/04：返回说明使用中文语义，避免未转义的泛型尖括号。
     *
     * @param scopeId scope id
     * @return 已登记的主群配置，未登记时为空
     */
    public Optional<StringDefaultConfigData> getConfig(String scopeId) {
        return iConfigDataService.getConfigData(this, StringDefaultConfigData.class, scopeId);
    }

    /**
     * 根据 key 获取 config
     * 2026/11/04：返回说明使用中文语义，避免未转义的泛型尖括号。
     *
     * @param key 返回 scope ID 的 key
     * @return 已登记的主群配置，未登记时为空
     */
    public Optional<StringDefaultConfigData> getConfig(IConfigKey key) {
        return iConfigDataService.getConfigData(this, StringDefaultConfigData.class, key);
    }
}
