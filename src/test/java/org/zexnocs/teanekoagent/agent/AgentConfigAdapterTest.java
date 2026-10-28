package org.zexnocs.teanekoagent.agent;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent._app_config.AgentFoundationConfig;
import org.zexnocs.teanekoagent._app_config.FileAgentRuntimeConfig;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeFieldChecker;
import org.zexnocs.teanekoagent.adapter.config.ConfigDataAgentScopePolicy;
import org.zexnocs.teanekoagent.agent.access.AgentScopePolicy;
import org.zexnocs.teanekoagent.agent.access.IAgentRuntimeConfigPort;
import org.zexnocs.teanekoagent.agent.access.IAgentScopePolicyPort;
import org.zexnocs.teanekoagent.file_config.AgentMainFileConfig;
import org.zexnocs.teanekoagent.llm.file_config.LLMModelFileConfig;
import org.zexnocs.teanekoagent.llm.framework.interaction.ILLMInteractionDefaults;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionSettings;
import org.zexnocs.teanekoagent.llm.framework.model.LLMConfigurationException;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ICredentialResolver;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.logger.ILogger;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证 /cfg 只读适配、字段校验、全局配置懒加载及组合装配，不启动 Spring 或数据库。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class AgentConfigAdapterTest {
    /** 验证配置管理器使用独立名称和原 /cfg 声明，管理端不依赖 Agent 门控。 */
    @Test void configManagerUsesExistingRegistrationBoundary() {
        var declaration = AgentScopeConfigManager.class.getAnnotation(ConfigManager.class);
        assertEquals("agent", declaration.value());
        assertEquals(AgentScopeConfigData.class, declaration.configType());
        assertEquals(AgentScopeFieldChecker.class, declaration.fieldChecker());
        assertTrue(declaration.enabled());
        assertTrue(declaration.namespaces().length > 0);
    }

    /** 验证缺失记录始终未启用，不创建配置或向默认人格配置回退。 */
    @Test void absentScopeDoesNotRegisterOrEnableAnything() {
        var configs = mock(IConfigDataService.class);
        var manager = new AgentScopeConfigManager();
        var scope = new AgentScope("client", "scope");
        when(configs.getConfigData(manager, AgentScopeConfigData.class, scope.scopeId())).thenReturn(Optional.empty());
        var adapter = new ConfigDataAgentScopePolicy(configs, manager);
        assertEquals(AgentScopePolicy.unregistered(), adapter.read(scope));
        verify(configs, only()).getConfigData(manager, AgentScopeConfigData.class, scope.scopeId());
    }

    /** 验证返回策略与可变 DTO 隔离，后续读取可以观察关闭和模式变化。 */
    @Test void scopeSnapshotsDoNotAliasMutableConfigData() {
        var configs = mock(IConfigDataService.class);
        var manager = new AgentScopeConfigManager();
        var data = new AgentScopeConfigData();
        when(configs.getConfigData(manager, AgentScopeConfigData.class, "scope")).thenReturn(Optional.of(data));
        var adapter = new ConfigDataAgentScopePolicy(configs, manager);
        var first = adapter.read(new AgentScope("client", "scope"));
        data.setEnabled(false); data.setMode("ACTIVE");
        var changed = adapter.read(new AgentScope("client", "scope"));
        assertTrue(first.enabled()); assertEquals(AgentScopePolicy.Mode.PASSIVE_BASIC, first.mode());
        assertFalse(changed.enabled()); assertEquals(AgentScopePolicy.Mode.ACTIVE, changed.mode());
        data.setMode("unknown");
        assertThrows(IllegalArgumentException.class, () -> adapter.read(new AgentScope("client", "scope")));
    }

    /** 验证普通 /cfg 仅能写当前声明字段，不接受近似布尔值、模式或认证字段。 */
    @Test void configFieldCheckerRejectsUnknownFieldsAndApproximateValues() {
        var checker = new AgentScopeFieldChecker();
        assertNull(checker.isValid("enabled", "true")); assertNull(checker.isValid("enabled", "false"));
        assertNotNull(checker.isValid("enabled", "TRUE")); assertNotNull(checker.isValid("enabled", null));
        for (var mode : AgentScopePolicy.Mode.values()) { assertNull(checker.isValid("mode", mode.name())); }
        assertNotNull(checker.isValid("mode", "active"));
        assertNotNull(checker.isValid("api-key", "测试敏感值"));
        assertNotNull(checker.isValid("correctionKey", "other"));
    }

    /** 验证文件适配构造不读取配置，关闭总开关时不继续解析交互设置。 */
    @Test void runtimeAdapterLoadsLazilyAndHonorsGlobalDisable() {
        var files = mock(IFileConfigService.class);
        var defaults = mock(ILLMInteractionDefaults.class);
        var adapter = new FileAgentRuntimeConfig(files, defaults);
        verifyNoInteractions(files, defaults);
        var main = new AgentMainFileConfig(); main.setEnabled(false);
        when(files.requireCurrent(AgentMainFileConfig.class)).thenReturn(main);
        assertFalse(adapter.read().enabled());
        verifyNoInteractions(defaults);
        main.setEnabled(true);
        when(files.requireCurrent(LLMModelFileConfig.class)).thenReturn(new LLMModelFileConfig());
        when(defaults.resolve()).thenReturn(LLMInteractionSettings.defaults());
        assertEquals(LLMInteractionSettings.defaults(), adapter.read().interaction());
    }

    /** 验证文件和环境变量解析错误不会导出属性值或原始异常链，修复后可恢复。 */
    @Test void runtimeAdapterRedactsFailureAndRecoversAfterReload() {
        var files = mock(IFileConfigService.class);
        var defaults = mock(ILLMInteractionDefaults.class);
        when(files.requireCurrent(AgentMainFileConfig.class)).thenThrow(new IllegalStateException("测试敏感值"))
                .thenReturn(new AgentMainFileConfig());
        when(files.requireCurrent(LLMModelFileConfig.class)).thenReturn(new LLMModelFileConfig());
        when(defaults.resolve()).thenReturn(LLMInteractionSettings.defaults());
        var adapter = new FileAgentRuntimeConfig(files, defaults);
        var failure = assertThrows(LLMConfigurationException.class, adapter::read);
        assertFalse(failure.getMessage().contains("测试敏感值")); assertNull(failure.getCause());
        assertTrue(adapter.read().enabled());
        when(defaults.resolve()).thenThrow(new IllegalStateException("测试敏感值"));
        assertThrows(LLMConfigurationException.class, adapter::read);
    }

    /** 验证组合根只建立底座，不加载配置、创建默认实例或执行就绪检查。 */
    @Test void springAssemblyDoesNotPerformRuntimeWork() {
        var root = new AgentFoundationConfig();
        var config = mock(IAgentRuntimeConfigPort.class);
        var scopes = mock(IAgentScopePolicyPort.class);
        var models = mock(ILLMModelService.class);
        var credentials = mock(ICredentialResolver.class);
        var logger = mock(ILogger.class);
        try (var registry = root.agentRegistry()) {
            var factory = root.agentFactory(registry);
            var readiness = root.agentReadiness(config, models, credentials, logger);
            assertNotNull(root.agentAccessGate(registry, scopes, readiness, logger));
            assertNotNull(factory); assertTrue(registry.snapshots().isEmpty());
            verifyNoInteractions(config, scopes, models, credentials, logger);
        }
    }

    /** 验证模型文件最近加载失败时，Agent 不借兼容缓存绕过严格检查。 */
    @Test void failedModelFileStopsRuntimeConfigurationBeforeDefaults() {
        var files = mock(IFileConfigService.class);
        var defaults = mock(ILLMInteractionDefaults.class);
        when(files.requireCurrent(AgentMainFileConfig.class)).thenReturn(new AgentMainFileConfig());
        when(files.requireCurrent(LLMModelFileConfig.class)).thenThrow(new IllegalStateException("测试旧模型缓存不可用。"));
        var adapter = new FileAgentRuntimeConfig(files, defaults);
        assertThrows(LLMConfigurationException.class, adapter::read);
        verifyNoInteractions(defaults);
        verify(files, never()).get(AgentMainFileConfig.class);
        verify(files, never()).get(LLMModelFileConfig.class);
    }
}
