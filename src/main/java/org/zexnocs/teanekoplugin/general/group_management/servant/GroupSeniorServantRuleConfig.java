package org.zexnocs.teanekoplugin.general.group_management.servant;

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import org.zexnocs.teanekocore.plugin.PluginOwned;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.zexnocs.teanekocore.database.configdata.api.IConfigData;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.List;

/**
 * 群高级公务员规则配置。
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 *
 * @author zExNocs
 * @date 2026/03/07
 * @since 4.1.0
 * @version 4.9.0-l
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@PluginOwned("general.group-management")
public class GroupSeniorServantRuleConfig implements IConfigData {
    /**
     * 用户平台 ID 列表。
     */
    @Description("群高级公务员列表")
    private List<String> list;
}
