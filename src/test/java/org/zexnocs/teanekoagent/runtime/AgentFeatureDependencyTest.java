package org.zexnocs.teanekoagent.runtime;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.plugin.AgentPluginRuntime;
import org.zexnocs.teanekoagent.plugin.IAgentFeature;
import org.zexnocs.teanekocore.event.interfaces.IEventService;
import org.zexnocs.teanekocore.plugin.PluginDescriptor;
import org.zexnocs.teanekocore.plugin.PluginRegistry;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证能力依赖停用后不会留下缺少依赖的消费者系统。
 * <br>2026/11/07：跨插件能力依赖同样受实时开关约束。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
class AgentFeatureDependencyTest {
    /** 停用任一必需能力后消费者整组退出，重新启用后重新固定代次。 */
    @Test
    void disabledDependencyRemovesItsConsumer() {
        var plugins = new PluginRegistry(List.of(
                () -> new PluginDescriptor("test.source", "1", 1, Set.of()),
                () -> new PluginDescriptor("test.consumer", "1", 1, Set.of())));
        var runtime = new AgentPluginRuntime(plugins,
                List.of(new Feature("test.source", "source", Set.of()),
                        new Feature("test.consumer", "consumer", Set.of("test.source:source"))),
                new DefaultListableBeanFactory().getBeanProvider(IEventService.class));
        var definition = new AgentDefinition("依赖测试", new LLMModelOptionsSnapshot(null), Set.of(),
                Set.of("test.consumer:consumer"));
        assertEquals(Set.of("test.source:source", "test.consumer:consumer"),
                runtime.select(definition).tokens().keySet());
        plugins.setEnabled("test.source", false);
        assertTrue(runtime.select(definition).tokens().isEmpty());
        plugins.setEnabled("test.source", true);
        assertEquals(Set.of("test.source:source", "test.consumer:consumer"),
                runtime.select(definition).tokens().keySet());
    }

    /**
     * 独立能力声明，只表达依赖，不执行任何业务。
     * @param pluginId 所属插件
     * @param id 本地标识
     * @param dependencies 必需能力键
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    private record Feature(String pluginId, String id, Set<String> dependencies) implements IAgentFeature { }
}
