package org.zexnocs.teanekoplugin.agent.social;

import org.zexnocs.teanekocore.plugin.PluginSwitchMode;
import org.zexnocs.teanekocore.plugin.TeaNekoPlugin;

/**
 * 社交互动插件入口，业务实现与元数据同组维护。
 * <br>2026/11/07：统一业务归属，组件装配继续保留独立参数。
 * <br>2026/11/07：使用注解集中声明插件身份、客户端需求和生命周期策略。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@TeaNekoPlugin(id = "agent.social", name = "社交互动", version = "4.9.0-l",
        description = "好感、心情、主动活动、主动关怀与受邀私聊。", category = "Agent",
        dependencies = {"teaneko.agent", "agent.cognition"}, switchMode = PluginSwitchMode.HOT)
public final class SocialPlugin {
}
