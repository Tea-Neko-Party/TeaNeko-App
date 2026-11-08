package org.zexnocs.teanekoplugin.agent.expression;

import org.zexnocs.teanekocore.plugin.PluginSwitchMode;
import org.zexnocs.teanekocore.plugin.TeaNekoPlugin;

/**
 * 表达与素材插件入口，业务实现与元数据同组维护。
 * <br>2026/11/07：统一业务归属，组件装配继续保留独立参数。
 * <br>2026/11/07：使用注解集中声明插件身份、客户端需求和生命周期策略。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@TeaNekoPlugin(id = "agent.expression", name = "表达与素材", version = "4.9.0-l",
        description = "拟人分段、原生表情和共享图库。", category = "Agent",
        dependencies = {"teaneko.agent"}, switchMode = PluginSwitchMode.HOT)
public final class ExpressionPlugin {
}
