package org.zexnocs.teanekoagent.agent.conversation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeFieldChecker;
import org.zexnocs.teanekoagent.adapter.config.ConfigDataAgentConversationPolicy;
import org.zexnocs.teanekoagent.agent.*;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证窗口配置优先级、旧记录兼容和非法配置恢复，不访问实际数据库。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
class AgentConversationPolicyTest {
    private final AgentRegistry registry = new AgentRegistry();
    private final AgentHandle handle = new AgentFactory(registry).create(new AgentDefinition("测试", new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.SERVICE)));
    private final MockEnvironment environment = new MockEnvironment();
    private final IConfigDataService configs = mock(IConfigDataService.class);
    private final AgentScopeConfigManager manager = new AgentScopeConfigManager();
    private final ConfigDataAgentConversationPolicy policy = new ConfigDataAgentConversationPolicy(environment, configs, manager);
    private final AgentRunRequest request = new AgentRunRequest(UUID.randomUUID(), new AgentActorContext(new AgentScope("客户端", "群"),
            AgentActorContext.Origin.USER, Optional.of(UUID.randomUUID()), "用户", "来源", "目标"), AgentActivity.SERVICE, List.of());

    /** 结束本用例拥有的实例注册表。 */
    @AfterEach void cleanup() { registry.close(); }

    /** 验证构造不读配置，缺失 scope 时只返回本地默认值而不注册 scope。 */
    @Test void defaultsAreLazyAndMissingScopeIsNotRegistered() {
        verifyNoInteractions(configs);
        when(configs.getConfigData(manager, AgentScopeConfigData.class, "群")).thenReturn(Optional.empty());
        assertEquals(AgentConversationOptions.defaults(), policy.resolve(handle, request));
        verify(configs).getConfigData(manager, AgentScopeConfigData.class, "群"); verifyNoMoreInteractions(configs);
    }

    /** 验证作用域只覆盖窗口与输入限制，完成记录缓存仍由本地配置管理。 */
    @Test void registeredScopeOverridesOnlyWindowAndInput() {
        environment.setProperty("tea-neko.agent.conversation.max-history-messages", "12");
        environment.setProperty("tea-neko.agent.conversation.max-input-bytes", "4096");
        environment.setProperty("tea-neko.agent.conversation.max-completed-requests", "3");
        environment.setProperty("tea-neko.agent.conversation.max-replay-bytes", "10000");
        var data = new AgentScopeConfigData(); data.setHistoryMaxMessages(0); data.setInputMaxBytes(8192);
        when(configs.getConfigData(manager, AgentScopeConfigData.class, "群")).thenReturn(Optional.of(data));
        assertEquals(new AgentConversationOptions(0, 8192, 3, 10000), policy.resolve(handle, request));
        data.setHistoryMaxMessages(-1); data.setInputMaxBytes(-1);
        assertEquals(new AgentConversationOptions(12, 4096, 3, 10000), policy.resolve(handle, request));
    }

    /** 验证每次解析新快照，后续配置变动不改写已固定策略。 */
    @Test void reloadProducesIndependentSnapshot() {
        var data = new AgentScopeConfigData();
        when(configs.getConfigData(manager, AgentScopeConfigData.class, "群")).thenReturn(Optional.of(data));
        var before = policy.resolve(handle, request); data.setHistoryMaxMessages(6);
        var after = policy.resolve(handle, request); assertEquals(40, before.maxHistoryMessages()); assertEquals(6, after.maxHistoryMessages());
        data.setInputMaxBytes(0); assertThrows(IllegalStateException.class, () -> policy.resolve(handle, request));
        data.setInputMaxBytes(-1); assertEquals(after, policy.resolve(handle, request));
    }

    /** 验证非法本地属性先拒绝，不回显原值，也不让 scope 覆盖掩盖无效默认值。 */
    @Test void invalidLocalSettingsFailBeforeScopeReadAndRecover() {
        environment.setProperty("tea-neko.agent.conversation.max-input-bytes", "敏感原值");
        var failure = assertThrows(IllegalStateException.class, () -> policy.resolve(handle, request));
        assertFalse(failure.getMessage().contains("敏感原值")); assertNull(failure.getCause()); verifyNoInteractions(configs);
        environment.setProperty("tea-neko.agent.conversation.max-input-bytes", "0");
        assertThrows(IllegalStateException.class, () -> policy.resolve(handle, request)); verifyNoInteractions(configs);
        environment.setProperty("tea-neko.agent.conversation.max-input-bytes", "262144");
        when(configs.getConfigData(manager, AgentScopeConfigData.class, "群")).thenReturn(Optional.empty());
        assertEquals(AgentConversationOptions.defaults(), policy.resolve(handle, request));
    }

    /** 验证旧 JSON 缺失新增字段时使用继承标记，不把零字节限制作为默认值。 */
    @Test void oldScopeJsonInheritsLocalWindowSettings() {
        var old = new ObjectMapper().readValue("{\"enabled\":true,\"mode\":\"PASSIVE_BASIC\"}", AgentScopeConfigData.class);
        assertEquals(-1, old.getHistoryMaxMessages()); assertEquals(-1, old.getInputMaxBytes());
        when(configs.getConfigData(manager, AgentScopeConfigData.class, "群")).thenReturn(Optional.of(old));
        assertEquals(AgentConversationOptions.defaults(), policy.resolve(handle, request));
    }

    /** 验证 /cfg 边界接受明确继承和有效限额，拒绝无效数字与未开放字段。 */
    @Test void fieldCheckerRestrictsOverridesToValidValues() {
        var checker = new AgentScopeFieldChecker();
        for (var value : List.of("-1", "0", "40")) { assertNull(checker.isValid("historyMaxMessages", value)); }
        for (var value : List.of("-2", "无效", "2147483648")) { assertNotNull(checker.isValid("historyMaxMessages", value)); }
        for (var value : List.of("-1", "1", Long.toString(Long.MAX_VALUE))) { assertNull(checker.isValid("inputMaxBytes", value)); }
        for (var value : List.of("0", "-2", "9223372036854775808")) { assertNotNull(checker.isValid("inputMaxBytes", value)); }
        assertNotNull(checker.isValid("apiKey", "敏感值")); assertNotNull(checker.isValid("maxReplayBytes", "0"));
    }
}
