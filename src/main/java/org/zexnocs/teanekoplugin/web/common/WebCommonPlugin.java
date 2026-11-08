package org.zexnocs.teanekoplugin.web.common;

import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginSwitchMode;
import org.zexnocs.teanekocore.plugin.TeaNekoPlugin;

/**
 * 提供网页消息查询与私聊 Agent 初始设置。
 * <br>2026/11/07：将同类业务作为一个可热启停的插件注册。
 * <br>2026/11/07：使用注解集中声明插件身份、客户端需求和生命周期策略。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@PluginOwned("web.common")
@TeaNekoPlugin(id = "web.common", name = "网页聊天工具", version = "4.9.0-l",
        description = "提供网页消息查询与私聊 Agent 初始设置。", category = "Web",
        dependencies = {"teaneko.agent"}, clients = {"web"}, switchMode = PluginSwitchMode.HOT)
public class WebCommonPlugin {
}
