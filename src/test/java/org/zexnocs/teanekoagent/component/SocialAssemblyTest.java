package org.zexnocs.teanekoagent.component;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.zexnocs.teanekoagent.component.affinity.AffinityComponent;
import org.zexnocs.teanekoagent.component.care.CareComponent;
import org.zexnocs.teanekoagent.component.contact.PrivateContactComponent;
import org.zexnocs.teanekoagent.component.memory.MemoryComponent;
import org.zexnocs.teanekoagent.component.memory.MemoryInputSystem;
import org.zexnocs.teanekoagent.component.memory.MemoryOutputSystem;
import org.zexnocs.teanekoagent.component.memory.history.IAgentHistoryRecorder;
import org.zexnocs.teanekoagent.component.memory.runtime.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.component.memory.shared.SharedMemoryComponent;
import org.zexnocs.teanekoagent.component.memory.shared.SharedMemoryPromptSource;
import org.zexnocs.teanekoagent.component.mood.MoodComponent;
import org.zexnocs.teanekoagent.component.proactive.ProactiveComponent;
import org.zexnocs.teanekoagent.component.social.SocialAccess;
import org.zexnocs.teanekoagent.component.social.SocialStateService;
import org.zexnocs.teanekoagent.component.tool.ToolComponent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * 新社交组件通过真实目录校验依赖、字段与序列化，新 Agent 仍保持仅模型。
 * <br>2026/11/06：不是只增加前端卡片，后端装配缺失依赖时必须拒绝。
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.8.6-l
 */
class SocialAssemblyTest {
    /** 可独立选择好感度与心情，关怀要求原主动能力和好感度；参数不能越界。 */
    @Test void validatesRealDependenciesAndParameters() {
        var histories = new DefaultListableBeanFactory().getBeanProvider(IAgentHistoryRecorder.class);
        var states = mock(SocialStateService.class);
        var access = mock(SocialAccess.class);
        var refs = mock(IAgentMemoryReferences.class);
        var registry = AgentAssemblyTest.registry(new ToolComponent(), new MemoryComponent(new MemoryInputSystem(histories), new MemoryOutputSystem(histories)),
                new AffinityComponent(states, access, refs), new MoodComponent(states, access, refs), new CareComponent(), new ProactiveComponent(),
                new SharedMemoryComponent(mock(SharedMemoryPromptSource.class)), new PrivateContactComponent());
        assertEquals(1, registry.validate(AgentAssembly.MINIMAL).parameters().size());
        String base = "\"llm\":{},\"tool\":{},\"memory\":{}";
        assertTrue(registry.validate("{\"version\":1,\"components\":{" + base + ",\"mood\":{}}}").has("mood"));
        assertThrows(IllegalArgumentException.class, () -> registry.validate("{\"version\":1,\"components\":{" + base + ",\"care\":{}}}"));
        assertThrows(IllegalArgumentException.class, () -> registry.validate("{\"version\":1,\"components\":{" + base + ",\"affinity\":{\"positiveStep\":100}}}"));
        var full = registry.validate("{\"version\":1,\"components\":{" + base
                + ",\"affinity\":{},\"mood\":{},\"proactive\":{},\"care\":{},\"shared-memory\":{},\"private-contact\":{}}}");
        assertEquals(9, full.parameters().size());
        assertFalse(registry.editor("{\"enabled\":true}").contains("\"care\""));
        assertTrue(registry.describe().stream().filter(item -> item.id().equals("care")).findFirst().orElseThrow().requires().contains("affinity"));
    }
}
