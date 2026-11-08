package org.zexnocs.teanekoplugin.agent.example;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/07：迁入所属业务插件，稳定业务标识和存储键保持不变。

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekoagent.plugin.IAgentFeature;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginSwitchMode;
import org.zexnocs.teanekocore.plugin.TeaNekoPlugin;

import java.time.Clock;

/**
 * 默认不装配的可编译插件示例；配置启用后仍需实例显式选择能力。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 *
 * <br>2026/11/07：使用注解集中声明插件身份、客户端需求和生命周期策略。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.0-l
 * @version 4.9.0-l
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "tea-neko.plugins.example.enabled", havingValue = "true")
@PluginOwned("example.clock")
@TeaNekoPlugin(id = "example.clock", name = "时钟示例", version = "1.0.0",
        description = "演示组件、工具、事件和异步扩展；实例须显式选择能力。", category = "示例",
        dependencies = {"teaneko.agent"}, switchMode = PluginSwitchMode.HOT)
public class ExampleAgentPlugin {
    /** @return 示例能力，不默认安装到所有实例 */
    @Bean public IAgentFeature exampleClockFeature() {
        return new ExampleClockFeature(Clock.systemUTC());
    }
}
