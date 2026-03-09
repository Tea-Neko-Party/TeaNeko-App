package org.zexnocs.teanekoplugin.onebot.request.auto_reject;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.zexnocs.teanekocore.database.configdata.api.IConfigData;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 低QQ等级自动拒绝入群请求的配置类。
 *
 * @author zExNocs
 * @date 2026/03/08
 * @since 4.1.1
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class LowQQLevelAutoRejectRuleConfig implements IConfigData {
    @Description("等级阈值，低于此等级的用户将被自动拒绝入群请求。")
    private short levelThreshold = 0;

    @Description("拒绝消息，低于等级阈值的用户将收到此消息。")
    private String rejectMessage = "您的QQ等级过低被自动拒绝，请联系群管理员。";

    @Description("拒绝后播报消息到的群列表。")
    private List<Long> reportGroupIdList = new CopyOnWriteArrayList<>();
}
