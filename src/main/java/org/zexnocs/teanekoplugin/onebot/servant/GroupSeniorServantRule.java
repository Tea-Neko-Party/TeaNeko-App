package org.zexnocs.teanekoplugin.onebot.servant;

import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekoclient.onebot.utils.OnebotScopeIdUtils;
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
        namespaces = {OnebotTeaNekoClient.GROUP_NAMESPACE},
        configType = GroupSeniorServantRuleConfig.class)
public class GroupSeniorServantRule {
    private final IConfigDataService iConfigService;
    private final OnebotScopeIdUtils onebotScopeIdUtils;

    public GroupSeniorServantRule(IConfigDataService iConfigService, OnebotScopeIdUtils onebotScopeIdUtils) {
        this.iConfigService = iConfigService;
        this.onebotScopeIdUtils = onebotScopeIdUtils;
    }

    /**
     * 判断是否是管理员。
     *
     * @param groupId 群号
     * @param userId 用户平台 ID
     * @return 是否是管理员
     */
    public boolean isAdmin(long groupId, long userId) {
        var scopeId = onebotScopeIdUtils.getGroupConfigKey(groupId);
        var config = iConfigService.getConfigData(this, GroupSeniorServantRuleConfig.class, scopeId)
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
