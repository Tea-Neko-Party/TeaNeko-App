package org.zexnocs.teanekoagent.component;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.zexnocs.teanekoplugin.agent.social.affinity.AffinityComponent;
import org.zexnocs.teanekoplugin.agent.social.care.CareComponent;
import org.zexnocs.teanekoplugin.agent.social.contact.PrivateContactComponent;
import org.zexnocs.teanekoplugin.agent.cognition.memory.MemoryComponent;
import org.zexnocs.teanekoplugin.agent.cognition.memory.MemoryInputSystem;
import org.zexnocs.teanekoplugin.agent.cognition.memory.MemoryOutputSystem;
import org.zexnocs.teanekoplugin.agent.cognition.memory.history.IAgentHistoryRecorder;
import org.zexnocs.teanekoplugin.agent.cognition.memory.runtime.IAgentMemoryReferences;
import org.zexnocs.teanekoplugin.agent.social.mood.MoodComponent;
import org.zexnocs.teanekoplugin.agent.social.proactive.ProactiveComponent;
import org.zexnocs.teanekoplugin.agent.social.social.SocialAccess;
import org.zexnocs.teanekoplugin.agent.social.social.SocialStateService;
import org.zexnocs.teanekoagent.component.tool.ToolComponent;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 新社交组件通过真实目录校验依赖、字段与序列化，新 Agent 仍保持仅模型。
 * <br>2026/11/06：不是只增加前端卡片，后端装配缺失依赖时必须拒绝。
 * <br>2026/11/06：不安装运行服务也能校验声明，防止目录发现提前解析业务依赖。
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.8.6-l
 */
class SocialAssemblyTest {
    /**
     * 可独立选择好感度与心情，关怀要求原主动能力和好感度；参数不能越界。
     * <br>2026/11/06：空服务容器刻意不提供运行依赖，声明校验不得尝试取用。
     */
    @Test void validatesRealDependenciesAndParameters() {
        var services = new DefaultListableBeanFactory();
        var histories = services.getBeanProvider(IAgentHistoryRecorder.class);
        var states = services.getBeanProvider(SocialStateService.class);
        var access = services.getBeanProvider(SocialAccess.class);
        var refs = services.getBeanProvider(IAgentMemoryReferences.class);
        var registry = AgentAssemblyTest.registry(new ToolComponent(), new MemoryComponent(new MemoryInputSystem(histories), new MemoryOutputSystem(histories)),
                new AffinityComponent(states, access, refs), new MoodComponent(states, access, refs), new CareComponent(), new ProactiveComponent(),
                new PrivateContactComponent());
        registry.installMigrations(java.util.List.of(new org.zexnocs.teanekoplugin.agent.cognition.CognitionAssemblyMigration()));
        assertEquals(1, registry.validate(AgentAssembly.MINIMAL).parameters().size());
        String base = "\"llm\":{},\"tool\":{},\"memory\":{}";
        assertTrue(registry.validate("{\"version\":1,\"components\":{" + base + ",\"mood\":{}}}").has("mood"));
        assertThrows(IllegalArgumentException.class, () -> registry.validate("{\"version\":1,\"components\":{" + base + ",\"care\":{}}}"));
        assertThrows(IllegalArgumentException.class, () -> registry.validate("{\"version\":1,\"components\":{" + base + ",\"affinity\":{\"positiveStep\":100}}}"));
        var full = registry.validate("{\"version\":1,\"components\":{" + base
                + ",\"affinity\":{},\"mood\":{},\"proactive\":{},\"care\":{},\"shared-memory\":{},\"private-contact\":{}}}");
        assertEquals(8, full.parameters().size());
        assertTrue(full.settings("memory").path("crossChatRecallEnabled").asBoolean());
        assertFalse(registry.editor("{\"enabled\":true}").contains("\"care\""));
        assertTrue(registry.describe().stream().filter(item -> item.id().equals("care")).findFirst().orElseThrow().requires().contains("affinity"));
    }
}
