package org.zexnocs.teanekoplugin.general.servant;

import org.zexnocs.teanekoapp.config.TeaNekoConfigNamespaces;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;

/**
 * 群高级公务员服务。
 *
 * @author zExNocs
 * @date 2026/03/07
 * @since 4.1.0
 */
@ConfigManager(
        value = "group-servants",
        description = """
        群高级公务员，具有一票决定权。""",
        namespaces = {TeaNekoConfigNamespaces.GROUP},
        configType = GroupSeniorServantRuleConfig.class)
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
