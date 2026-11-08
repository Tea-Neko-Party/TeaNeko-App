package org.zexnocs.teanekoplugin.onebot.requests;

import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginSwitchMode;
import org.zexnocs.teanekocore.plugin.TeaNekoPlugin;

/**
 * 提供邀请处理、入群申请审核及自动规则，依赖群管理配置。
 * <br>2026/11/07：将同类业务作为一个可热启停的插件注册。
 * <br>2026/11/07：使用注解集中声明插件身份、客户端需求和生命周期策略。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@PluginOwned("onebot.requests")
@TeaNekoPlugin(id = "onebot.requests", name = "OneBot 申请处理", version = "4.9.0-l",
        description = "提供邀请处理、入群申请审核及自动规则，依赖群管理配置。", category = "OneBot",
        dependencies = {"teaneko.core", "general.group-management"}, clients = {"onebot"}, switchMode = PluginSwitchMode.HOT)
public class OnebotRequestsPlugin {
}
