package org.zexnocs.teanekoplugin.general.engagement;

import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginSwitchMode;
import org.zexnocs.teanekocore.plugin.TeaNekoPlugin;

/**
 * 提供签到、补签、积分奖励及好感度互动。
 * <br>2026/11/07：将同类业务作为一个可热启停的插件注册。
 * <br>2026/11/07：使用注解集中声明插件身份、客户端需求和生命周期策略。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@PluginOwned("general.engagement")
@TeaNekoPlugin(id = "general.engagement", name = "签到与互动", version = "4.9.0-l",
        description = "提供签到、补签、积分奖励及好感度互动。", category = "通用",
        dependencies = {"teaneko.core"}, switchMode = PluginSwitchMode.HOT)
public class GeneralEngagementPlugin {
}
