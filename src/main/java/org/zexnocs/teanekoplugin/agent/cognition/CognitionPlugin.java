package org.zexnocs.teanekoplugin.agent.cognition;

import org.zexnocs.teanekocore.plugin.PluginSwitchMode;
import org.zexnocs.teanekocore.plugin.TeaNekoPlugin;

/**
 * 认知插件入口，业务实现与元数据同组维护。
 * <br>2026/11/07：统一业务归属，组件装配继续保留独立参数。
 * <br>2026/11/07：使用注解集中声明插件身份、客户端需求和生命周期策略。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@TeaNekoPlugin(id = "agent.cognition", name = "认知", version = "4.9.0-l",
        description = "记忆、跨聊天回忆、人格与观察学习，组件分别装配。", category = "Agent",
        dependencies = {"teaneko.agent"}, switchMode = PluginSwitchMode.HOT)
public final class CognitionPlugin {
}
