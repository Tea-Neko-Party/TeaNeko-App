package org.zexnocs.teanekoplugin.onebot.common.delete;

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.zexnocs.teanekocore.database.configdata.api.IConfigData;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.plugin.PluginOwned;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 自动撤回规则配置。
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 *
 * @author zExNocs
 * @date 2026/03/07
 * @since 4.1.0
 * @version 4.9.0-l
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@PluginOwned("onebot.common")
public class AutoDeleteRuleConfig implements IConfigData {
    @Description("自动撤回的成员ID列表")
    private List<Long> list = new CopyOnWriteArrayList<>();
}
