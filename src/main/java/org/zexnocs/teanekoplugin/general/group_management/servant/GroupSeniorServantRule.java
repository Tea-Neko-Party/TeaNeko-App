package org.zexnocs.teanekoplugin.general.group_management.servant;

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import org.zexnocs.teanekocore.plugin.PluginOwned;

import org.zexnocs.teanekoapp.config.TeaNekoConfigNamespaces;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;

/**
 * 群高级公务员服务。
 *
 * <br>2026/11/04：为管理界面提供简短中文名称，配置标识和业务行为不变。
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 *
 * @author zExNocs
 * @date 2026/03/07
 * @since 4.1.0
 * @version 4.9.0-l
 */
// 2026/11/04：显示名称与原稳定配置键分别维护。
@ConfigManager(
        value = "group-servants", displayName = "群高级管理员",
        description = """
        群高级公务员，具有一票决定权。""",
        namespaces = {TeaNekoConfigNamespaces.GROUP},
        configType = GroupSeniorServantRuleConfig.class)
@PluginOwned("general.group-management")
public class GroupSeniorServantRule {
    private final IConfigDataService iConfigService;

    public GroupSeniorServantRule(IConfigDataService iConfigService) {
        this.iConfigService = iConfigService;
    }

    /**
     * 判断是否是管理员。
     *
     * @param scopeId 区域 ID
     * @param userId 用户平台 ID
     * @return 是否是管理员
     */
    public boolean isAdmin(String scopeId, String userId) {
        var config = iConfigService
                .getConfigData(this, GroupSeniorServantRuleConfig.class, scopeId)
                .orElse(null);
        if(config == null) {
            return false;
        }
        var list = config.getList();
        if(list == null || list.isEmpty()) {
            return false;
        }
        return list.contains(userId);
    }
}
