package org.zexnocs.teanekoplugin.onebot.servant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.zexnocs.teanekocore.database.configdata.api.IConfigData;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.List;

/**
 * 群高级公务员规则配置。
 *
 * @author zExNocs
 * @date 2026/03/07
 * @since 4.1.0
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GroupSeniorServantRuleConfig implements IConfigData {
    /**
     * onebot ID 列表。
     */
    @Description("群高级公务员列表")
    private List<Long> list;
}
