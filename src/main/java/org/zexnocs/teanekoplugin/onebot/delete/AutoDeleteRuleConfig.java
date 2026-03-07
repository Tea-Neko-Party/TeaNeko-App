package org.zexnocs.teanekoplugin.onebot.delete;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.zexnocs.teanekocore.database.configdata.api.IConfigData;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 自动撤回规则配置。
 *
 * @author zExNocs
 * @date 2026/03/07
 * @since 4.1.0
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class AutoDeleteRuleConfig implements IConfigData {
    @Description("自动撤回的成员ID列表")
    private List<Long> list = new CopyOnWriteArrayList<>();
}
