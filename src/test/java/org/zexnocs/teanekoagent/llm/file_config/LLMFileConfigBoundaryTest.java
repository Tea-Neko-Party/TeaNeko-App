package org.zexnocs.teanekoagent.llm.file_config;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent._app_config.FileDefaultModelResolver;
import org.zexnocs.teanekoagent.file_config.AgentMainFileConfig;
import org.zexnocs.teanekoagent.llm.framework.model.LLMConfigurationException;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelId;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekocore.file_config.api.FileConfig;
import org.zexnocs.teanekocore.file_config.exception.FileConfigDataNotFoundException;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 离线验证模型文件的严格路由、非敏感选项合并及配置迁移边界。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class LLMFileConfigBoundaryTest {
    private final IFileConfigService files = mock(IFileConfigService.class);
    private final LLMFileConfigService service = new LLMFileConfigService(files);
    private final LLMModelId route = LLMModelId.of("deepseek");

    /**
     * 验证非敏感默认选项正确合并，旧 provider 字段仍可定位路由。
     */
    @Test
    void mergesNonSensitiveDefaultsAndKeepsLegacyProviderAsRouteFallback() {
        var entry = entry("deepseek", "configured-model");
        entry.setId(null);
        entry.setProvider("deepseek");
        entry.setMaxTokens(2048);
        entry.setMetadata(Map.of("vendor.reasoning", "medium"));
        configure(entry);
        var base = LLMModelOptions.builder().provider("deepseek").model("code-model").temperature(0.5).build();
        var options = service.getDefaultOptions(route, base);
        assertEquals("configured-model", options.getModel());
        assertEquals("deepseek", options.getProvider());
        assertEquals(2048, options.getMaxTokens());
        assertEquals(0.5, options.getTemperature());
        assertEquals("medium", options.getMetadata().get("vendor.reasoning"));
        assertFalse(new ObjectMapper().writeValueAsString(options.getMetadata()).contains("apiKey"));
        assertEquals("code-model", base.getModel());
    }

    /**
     * 验证配置文件或所选路由缺失时明确失败，不静默使用代码默认模型。
     */
    @Test
    void missingFileAndSelectedRouteDoNotFallBackToCodeDefaults() {
        when(files.get(LLMModelFileConfig.class)).thenThrow(new FileConfigDataNotFoundException("sensitive-source-details"));
        var error = assertThrows(LLMConfigurationException.class, () -> service.getDefaultOptions(route, null));
        assertNull(error.getCause());
        assertFalse(error.toString().contains("sensitive-source-details"));
        doReturn(new LLMModelFileConfig()).when(files).get(LLMModelFileConfig.class);
        assertThrows(LLMConfigurationException.class, () -> service.getDefaultOptions(route, null));
    }

    /**
     * 验证路由未指定具体模型时在调用前被拒绝。
     */
    @Test
    void blankConcreteModelIsRejected() {
        configure(entry("deepseek", " "));
        assertThrows(LLMConfigurationException.class, () -> service.getDefaultOptions(route,
                LLMModelOptions.builder().model("code-model").build()));
    }

    /**
     * 验证重复或空路由明确报错，不依赖配置顺序选择结果。
     */
    @Test
    void duplicateAndEmptyRoutesAreRejectedInsteadOfChoosingTheFirst() {
        configure(entry("deepseek", "first"), entry("deepseek", "second"));
        assertThrows(LLMConfigurationException.class, () -> service.getDefaultOptions(route, null));
        configure(entry("deepseek", "first"), null);
        assertThrows(LLMConfigurationException.class, () -> service.getDefaultOptions(route, null));
        configure(entry(null, "first"));
        assertThrows(LLMConfigurationException.class, () -> service.getDefaultOptions(route, null));
        var config = new LLMModelFileConfig();
        config.setModels(null);
        when(files.get(LLMModelFileConfig.class)).thenReturn(config);
        assertThrows(LLMConfigurationException.class, () -> service.getDefaultOptions(route, null));
    }

    /**
     * 验证旧密钥和连接字段必须显式迁移，不能流入普通模型选项。
     */
    @Test
    void legacySecretsAndConnectionFieldsRequireExplicitMigration() {
        List<Consumer<LLMModelFileConfigParameter>> setters = List.of(
                e -> e.setApiKey("legacy-test-secret"), e -> e.setApi("/responses"),
                e -> e.setBaseUrl("https://legacy.test"),
                e -> e.setMetadata(Map.of("apiKey", "legacy-test-secret")),
                e -> e.setMetadata(Map.of("baseUrl", "https://legacy.test")));
        for (var setter : setters) {
            var entry = entry("deepseek", "configured-model");
            setter.accept(entry);
            configure(entry);
            var error = assertThrows(LLMConfigurationException.class, () -> service.getDefaultOptions(route, null));
            assertFalse(error.toString().contains("legacy-test-secret"));
        }
        var entry = entry("deepseek", "configured-model");
        entry.setApiKey("legacy-test-secret");
        assertFalse(new ObjectMapper().writeValueAsString(entry).contains("legacy-test-secret"));
    }

    /**
     * 验证默认路由使用新主配置且读取重载后的值。
     */
    @Test
    void defaultRouteUsesNewMainConfigAndReloadedValues() {
        var resolver = new FileDefaultModelResolver(files);
        var config = new AgentMainFileConfig();
        when(files.get(AgentMainFileConfig.class)).thenReturn(config);
        assertTrue(resolver.findDefaultModelId().isEmpty());
        config.setDefaultModelId("deepseek");
        assertEquals(route, resolver.findDefaultModelId().orElseThrow());
        config.setDefaultModelId("kimi");
        assertEquals(LLMModelId.of("kimi"), resolver.findDefaultModelId().orElseThrow());
        when(files.get(AgentMainFileConfig.class)).thenThrow(new FileConfigDataNotFoundException("private-source"));
        var error = assertThrows(LLMConfigurationException.class, resolver::findDefaultModelId);
        assertNull(error.getCause());
        assertFalse(error.toString().contains("private-source"));
    }

    /**
     * 验证主配置只有一个文件绑定，过渡服务也复用新类型。
     *
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     */
    @Test
    void mainConfigHasOneFileBindingAndOldServiceUsesTheNewType() throws Exception {
        assertNotNull(AgentMainFileConfig.class.getAnnotation(FileConfig.class));
        assertEquals(AgentMainFileConfig.class,
                org.zexnocs.teanekoagent_old.file_config.interfaces.IAgentFileConfigService.class
                        .getMethod("getMainConfig").getReturnType());
        assertEquals("agent", AgentMainFileConfig.class.getAnnotation(FileConfig.class).path());
        assertEquals("main-config", AgentMainFileConfig.class.getAnnotation(FileConfig.class).value());
    }

    /**
     * 向受控文件配置服务安装本用例的路由条目，不访问实际部署配置。
     *
     * @param entries 本用例需要安装的配置条目
     */
    private void configure(LLMModelFileConfigParameter... entries) {
        var config = new LLMModelFileConfig();
        config.setModels(Arrays.asList(entries));
        when(files.get(LLMModelFileConfig.class)).thenReturn(config);
    }

    /**
     * 构造仅含路由标识和具体模型的测试配置，避免依赖真实认证信息。
     *
     * @param id 稳定标识
     * @param model 本次实际选中的具体模型名称
     * @return 满足本方法约束的 {@code LLMModelFileConfigParameter} 结果
     */
    private static LLMModelFileConfigParameter entry(String id, String model) {
        var entry = new LLMModelFileConfigParameter();
        entry.setId(id);
        entry.setModel(model);
        return entry;
    }
}
