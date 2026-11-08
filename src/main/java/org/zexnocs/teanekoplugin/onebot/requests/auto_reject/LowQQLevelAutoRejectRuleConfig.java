package org.zexnocs.teanekoplugin.onebot.requests.auto_reject;

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
 * 低QQ等级自动拒绝入群请求的配置类。
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 *
 * @author zExNocs
 * @date 2026/03/08
 * @since 4.1.1
 * @version 4.9.0-l
 */
@NoArgsConstructor
@AllArgsConstructor
@Getter
@PluginOwned("onebot.requests")
public class LowQQLevelAutoRejectRuleConfig implements IConfigData {
    @Description("等级阈值，低于此等级的用户将被自动拒绝入群请求。")
    private short levelThreshold = 0;

    @Description("拒绝消息，低于等级阈值的用户将收到此消息。")
    private String rejectMessage = "您的QQ等级过低被自动拒绝，请联系群管理员。";

    @Description("拒绝后播报消息到的群列表。")
    private List<Long> reportGroupIdList = new CopyOnWriteArrayList<>();
}
