package org.zexnocs.teanekoagent.component.llm.file_config;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.component.llm.configuration.FileDefaultModelResolver;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMConfigurationException;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelId;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.configuration.file_config.AgentMainFileConfig;
import org.zexnocs.teanekocore.file_config.api.FileConfig;
import org.zexnocs.teanekocore.file_config.exception.FileConfigDataNotFoundException;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 2026/11/05：跟随模型配置装配迁移更新默认路由解析器引用。

/**
 * 离线验证模型文件的严格路由、非敏感选项合并及配置迁移边界。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：文件配置夹具补充模型上下文和输入比例。
 * <br>2026/11/04：校正当前实现的版本归属为 v4.7.2-l。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：适配模型专属 configuration 包，保留原文件配置验证。
 * <br>2026/11/07：根据原提交职责恢复编码损坏的历史注释。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.8.0-l
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
     * 顶层凭据与连接可以同文件配置，但不能流入普通选项；metadata 中的认证或连接字段仍拒绝。
     */
    @Test
    void fileCredentialsStayOutsideOptionsAndMetadata() {
        List<Consumer<LLMModelFileConfigParameter>> setters = List.of(
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
        entry.setApiPath("/responses"); entry.setBaseUrl("https://gateway.test");
        configure(entry);
        assertEquals("configured-model", service.getDefaultOptions(route, null).getModel());
        assertFalse(new ObjectMapper().writeValueAsString(service.getDefaultOptions(route, null).getMetadata()).contains("legacy-test-secret"));
        assertFalse(new ObjectMapper().writeValueAsString(entry).contains("legacy-test-secret"));
    }

    /**
     * 验证默认路由来自统一模型文件，读取当前重载值且不再访问 Agent 主配置。
     */
    @Test
    void defaultRouteUsesCurrentModelFileAndReloadedValues() {
        var resolver = new FileDefaultModelResolver(files);
        var config = new LLMModelFileConfig();
        when(files.requireCurrent(LLMModelFileConfig.class)).thenReturn(config);
        assertTrue(resolver.findDefaultModelId().isEmpty());
        config.setDefaultModelId("deepseek");
        assertEquals(route, resolver.findDefaultModelId().orElseThrow());
        config.setDefaultModelId("kimi");
        assertEquals(LLMModelId.of("kimi"), resolver.findDefaultModelId().orElseThrow());
        when(files.requireCurrent(LLMModelFileConfig.class)).thenThrow(new FileConfigDataNotFoundException("private-source"));
        var error = assertThrows(LLMConfigurationException.class, resolver::findDefaultModelId);
        assertNull(error.getCause());
        assertFalse(error.toString().contains("private-source"));
        verify(files, never()).get(AgentMainFileConfig.class);
    }

    /**
     * 验证主配置只有一个文件绑定，旧配置服务不再进入编译产物。
     *
     */
    @Test
    void mainConfigHasOneFileBindingAndOldServiceIsRemoved() {
        assertNotNull(AgentMainFileConfig.class.getAnnotation(FileConfig.class));
        assertThrows(ClassNotFoundException.class, () -> Class.forName("org.zexnocs.teanekoagent_old.file_config.interfaces.IAgentFileConfigService"));
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
        // 2026/11/04：离线模型显式提供输出额度与上下文能力。
        entry.setMaxTokens(2048);
        entry.setContextWindowTokens(131072L);
        return entry;
    }
}
