package org.zexnocs.teanekoplugin.onebot.member_events;

import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginSwitchMode;
import org.zexnocs.teanekocore.plugin.TeaNekoPlugin;

/**
 * 提供入群欢迎和退群通知。
 * <br>2026/11/07：将同类业务作为一个可热启停的插件注册。
 * <br>2026/11/07：使用注解集中声明插件身份、客户端需求和生命周期策略。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@PluginOwned("onebot.member-events")
@TeaNekoPlugin(id = "onebot.member-events", name = "OneBot 成员事件", version = "4.9.0-l",
        description = "提供入群欢迎和退群通知。", category = "OneBot",
        dependencies = {"teaneko.core"}, clients = {"onebot"}, switchMode = PluginSwitchMode.HOT)
public class OnebotMemberEventsPlugin {
}
