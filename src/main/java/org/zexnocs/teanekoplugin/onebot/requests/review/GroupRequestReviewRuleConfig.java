package org.zexnocs.teanekoplugin.onebot.requests.review;

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import org.zexnocs.teanekocore.plugin.PluginOwned;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.zexnocs.teanekocore.database.configdata.api.IConfigData;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 入群请求审核规则配置
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 *
 * @author zExNocs
 * @date 2026/03/10
 * @since 4.1.4
 * @version 4.9.0-l
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@PluginOwned("onebot.requests")
public class GroupRequestReviewRuleConfig implements IConfigData {
    @Description("需要至少多少人同意才能通过入群请求")
    private int requestAgreeNum = 1;

    @Description("需要至少多少人拒绝才能拒绝入群请求")
    private int requestRejectNum = 1;

    @Description("审核群列表")
    private List<Long> reviewGroupList = new CopyOnWriteArrayList<>();
}
