package org.zexnocs.teanekoplugin.onebot.request.review;

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
 * @author zExNocs
 * @date 2026/03/10
 * @since 4.1.4
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class GroupRequestReviewRuleConfig implements IConfigData {
    @Description("需要至少多少人同意才能通过入群请求")
    private int requestAgreeNum = 1;

    @Description("需要至少多少人拒绝才能拒绝入群请求")
    private int requestRejectNum = 1;

    @Description("审核群列表")
    private List<Long> reviewGroupList = new CopyOnWriteArrayList<>();
}
