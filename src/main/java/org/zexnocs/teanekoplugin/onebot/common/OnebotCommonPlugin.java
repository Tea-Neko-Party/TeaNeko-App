package org.zexnocs.teanekoplugin.onebot.common;

import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginSwitchMode;
import org.zexnocs.teanekocore.plugin.TeaNekoPlugin;

/**
 * 提供成员管理、点赞、头衔、撤回、互动及平台 Agent 工具。
 * <br>2026/11/07：将同类业务作为一个可热启停的插件注册。
 * <br>2026/11/07：使用注解集中声明插件身份、客户端需求和生命周期策略。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@PluginOwned("onebot.common")
@TeaNekoPlugin(id = "onebot.common", name = "OneBot 常用功能", version = "4.9.0-l",
        description = "提供成员管理、点赞、头衔、撤回、互动及平台 Agent 工具。", category = "OneBot",
        dependencies = {"teaneko.agent"}, clients = {"onebot"}, switchMode = PluginSwitchMode.HOT)
public class OnebotCommonPlugin {
}
