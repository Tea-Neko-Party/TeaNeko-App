package org.zexnocs.teanekoplugin.onebot.request.auto_approve;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.zexnocs.teanekocore.database.configdata.api.IConfigData;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 根据主群中等级自动批准子群的入群申请。此外可以设定白名单和黑名单。
 *
 * @author zExNocs
 * @date 2026/03/08
 * @since 4.1.1
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class SubsidiaryGroupAutoApproveByLevelRuleConfig implements IConfigData {
    @Description("主群群号，用于判断等级是否达到自动批准的条件")
    private long mainGroupId = 0L;

    @Description("等级阈值")
    private int levelThreshold = 0;

    @Description("白名单，未达到等级阈值的用户仍然可以自动批准的名单")
    private List<Long> whiteList = new CopyOnWriteArrayList<>();

    @Description("黑名单，不进行自动比准的名单")
    private List<Long> blackList = new CopyOnWriteArrayList<>();

    @Description("是否自动拒绝不符合等级的入群申请")
    private boolean rejectIfNotMeetLevel = true;

    @Description("是否自动拒绝黑名单中的入群申请")
    private boolean rejectIfInBlackList = true;

    @Description("拒绝理由")
    private String rejectReason = "未达到入群要求，被自动拒绝。";
}