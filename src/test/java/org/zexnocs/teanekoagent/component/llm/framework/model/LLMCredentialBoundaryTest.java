package org.zexnocs.teanekoagent.component.llm.framework.model;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
// 2026/11/05：跟随模型配置装配迁移更新凭据解析器引用。
import org.zexnocs.teanekoagent.component.llm.configuration.FileLLMCredentialResolver;
import org.zexnocs.teanekoagent.component.llm.file_config.LLMModelFileConfig;
import org.zexnocs.teanekoagent.component.llm.file_config.LLMModelFileConfigParameter;
import org.zexnocs.teanekocore.file_config.exception.FileConfigDataNotFoundException;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.file_config.parser.YAMLFileTypeParser;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证 Core YAML 环境解析、统一文件凭据、连接校验与认证信息不外泄。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04����֤�о���ʽ����ģ�������ĺ����������
 *
 * <br>2026/11/04��У������ʵ�ֵİ汾����Ϊ v4.7.2-l��
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/05：适配模型专属 configuration 包，保留原凭据隔离验证。
 *
 * <br>2026/11/05：使用两节点引用环验证元数据边界，保留循环输入覆盖。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
class LLMCredentialBoundaryTest {
    private static final String SECRET = "test-only-secret";
    private final IFileConfigService files = mock(IFileConfigService.class);

    /**
     * 验证凭据仅在使用指定供应商时懒读当前文件，重载失败不能取旧值或 application 回退。
     */
    @Test
    void resolvesOnlyRequestedProviderLazilyAndReadsCurrentFile() {
        var resolver = new FileLLMCredentialResolver(files);
        verifyNoInteractions(files);
        assertThrows(LLMConfigurationException.class, () -> resolver.require("deepseek"));
        var entry = configure(SECRET); entry.setBaseUrl("https://gateway.example.test/v1///"); entry.setApiPath("/chat/custom");
        var access = resolver.require("deepseek");
        assertEquals(SECRET, access.apiKey());
        assertEquals("https://gateway.example.test/v1", access.baseUrlOr("fallback"));
        assertEquals("/chat/custom", access.apiPathOr("fallback"));
        assertThrows(LLMConfigurationException.class, () -> resolver.require("kimi"));
        entry.setApiKey("rotated-test-key");
        assertEquals("rotated-test-key", resolver.require("deepseek").apiKey());
        assertEquals(SECRET, access.apiKey());
        when(files.requireCurrent(LLMModelFileConfig.class)).thenThrow(new FileConfigDataNotFoundException(SECRET));
        var failure = assertThrows(LLMConfigurationException.class, () -> resolver.require("deepseek"));
        assertNull(failure.getCause()); assertFalse(failure.toString().contains(SECRET));
        verify(files, never()).get(LLMModelFileConfig.class);
    }

    /**
     * 经实际 Core YAML 解析环境值、默认值、类型和嵌套 metadata，普通导出隐藏凭据而显式本地写回保留当前值。
     * @param directory 独立临时文件目录
     * @throws Exception 当临时文件或解析失败时
     */
    @Test
    void supportsYamlEnvironmentPlaceholdersWithoutExportingCredentials(@TempDir Path directory) throws Exception {
        var environment = new MockEnvironment().withProperty("TEST_LLM_KEY", SECRET).withProperty("TEST_TEXT", "原文\nmodel: 不应注入结构");
        var parser = new YAMLFileTypeParser(environment);
        var yaml = """
                default-model-id: "${TEST_ROUTE:deepseek}"
                models:
                  - id: deepseek
                    model: "${TEST_MODEL:offline-model}"
                    api-key: "${TEST_LLM_KEY}"
                    base-url: "${TEST_BASE:https://default.test}"
                    api: "${TEST_PATH:/default}"
                    max-tokens: "${TEST_TOKENS:2048}"
                    context-window-tokens: 131072
                    thinking: "${TEST_THINKING:true}"
                    metadata:
                      vendor:
                        texts: ["${TEST_TEXT}", "${TEST_FALLBACK:默认文本}"]
                """;
        var config = parser.fromFileToData(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), LLMModelFileConfig.class);
        when(files.requireCurrent(LLMModelFileConfig.class)).thenReturn(config);
        // 2026/11/05：默认路由与凭据解析器共同归入模型 configuration 包。
        assertEquals(LLMModelId.of("deepseek"), new org.zexnocs.teanekoagent.component.llm.configuration.FileDefaultModelResolver(files).findDefaultModelId().orElseThrow());
        var access = new FileLLMCredentialResolver(files).require("deepseek");
        assertEquals(SECRET, access.apiKey());
        assertEquals("LLMProviderAccess[已隐藏凭据]", access.toString());
        assertEquals("\"LLMProviderAccess[已隐藏凭据]\"", new ObjectMapper().writeValueAsString(access));
        assertEquals("https://default.test", access.baseUrlOr("https://default.test"));
        assertEquals("/default", access.apiPathOr("/default"));
        var entry = config.getModels().getFirst(); var options = entry.toOptions(null);
        assertEquals("offline-model", options.getModel()); assertEquals(2048, options.getMaxTokens()); assertEquals(Boolean.TRUE, options.getThinking());
        assertEquals(Map.of("texts", List.of("原文\nmodel: 不应注入结构", "默认文本")), options.getMetadata().get("vendor"));
        assertFalse(new ObjectMapper().writeValueAsString(config).contains(SECRET));
        assertFalse(new ObjectMapper().writeValueAsString(options.getMetadata()).contains(SECRET));
        var output = directory.resolve("model.yml"); parser.fromDataToWrite(output, config);
        try (var stream = Files.newInputStream(output)) {
            var restored = parser.fromFileToData(stream, LLMModelFileConfig.class);
            assertEquals(SECRET, restored.getModels().getFirst().getApiKey());
            assertEquals("/default", restored.getModels().getFirst().getApiPath());
        }
        assertThrows(IllegalArgumentException.class, () -> new YAMLFileTypeParser(new MockEnvironment()).fromFileToData(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)), LLMModelFileConfig.class));
    }

    /**
     * 验证缺失或非法凭据的错误与原因链不包含敏感值。
     */
    @Test
    void missingOrMalformedCredentialsHaveNoSensitiveCause() {
        for (var key : List.of("", " ", "${UNRESOLVED_TEST_KEY}", "unsafe\n" + SECRET)) {
            configure(key); var resolver = new FileLLMCredentialResolver(files);
            var error = assertThrows(LLMConfigurationException.class, () -> resolver.require("deepseek"));
            assertFalse(error.toString().contains(SECRET));
            assertFalse(error.toString().contains("UNRESOLVED_TEST_KEY"));
            assertNull(error.getCause());
        }
    }

    /**
     * 验证非法连接配置被拒绝且诊断不回显原值。
     */
    @Test
    void rejectsInvalidConnectionValuesWithoutIncludingThemInDiagnostics() {
        for (var url : List.of("https://user:" + SECRET + "@example.test", "https://example.test?key=" + SECRET,
                "file:///" + SECRET, "https://example.test/#" + SECRET, "not a url " + SECRET)) {
            var error = assertThrows(LLMConfigurationException.class, () -> new LLMProviderAccess(SECRET, url, null));
            assertFalse(error.toString().contains(SECRET));
            assertNull(error.getCause());
        }
        for (var path : List.of("https://elsewhere.test/", "//elsewhere.test/", "/../secret", "/a/../secret",
                "/path?key=" + SECRET, "/path#" + SECRET, "/%2e%2e/secret", "relative")) {
            assertThrows(LLMConfigurationException.class, () -> new LLMProviderAccess(SECRET, null, path));
        }
        var local = new LLMProviderAccess(SECRET, "http://localhost:8080/v1", "/responses");
        assertEquals("http://localhost:8080/v1", local.baseUrlOr("fallback"));
    }

    /**
     * 验证供应商标识不能注入任意 Spring 属性路径。
     */
    @Test
    void rejectsPropertyPathInjection() {
        var resolver = new FileLLMCredentialResolver(files);
        for (var provider : List.of("", "deepseek.api-key", "${TEST_LLM_KEY}", "../deepseek")) {
            var error = assertThrows(LLMConfigurationException.class, () -> resolver.require(provider));
            assertEquals("LLM 凭据供应商标识不合法。", error.getMessage());
        }
    }

    /**
     * 验证嵌套或 header 形式的认证字段也不能进入普通元数据。
     * 2026/11/05：使用两节点引用环验证元数据边界，保留循环输入覆盖。
     */
    @Test
    void rejectsSecretMetadataIncludingNestedAndHeaderForms() {
        for (var key : List.of("apiKey", "api-key", "api_key", "header.authorization", "body.api_key",
                "openai.accessToken", "client_secret", "password", "credentials")) {
            var options = LLMModelOptions.builder().metadata(Map.of("body", List.of(Map.of(key, SECRET)))).build();
            var error = assertThrows(LLMConfigurationException.class, () -> LLMOptionBoundary.validate(options));
            assertFalse(error.toString().contains(SECRET));
        }
        var cyclic = new LinkedHashMap<String, Object>();
        var nested = new LinkedHashMap<String, Object>();
        cyclic.put("nested", nested);
        nested.put("parent", cyclic);
        cyclic.put("reasoning", Map.of("effort", "medium"));
        assertDoesNotThrow(() -> LLMOptionBoundary.validateMetadata(cyclic));
    }

    /**
     * 安装只有测试路由与凭据的当前文件，不读取真实部署文件。
     * @param key 本用例的原始认证值
     * @return 可供本用例更新的路由条目
     */
    private LLMModelFileConfigParameter configure(String key) {
        var entry = new LLMModelFileConfigParameter(); entry.setId("deepseek"); entry.setModel("offline-model"); entry.setApiKey(key);
        // 2026/11/04：认证夹具只增加离线容量声明，凭据仍走原安全边界。
        entry.setMaxTokens(2048);
        entry.setContextWindowTokens(131072L);
        var config = new LLMModelFileConfig(); config.setModels(List.of(entry));
        when(files.requireCurrent(LLMModelFileConfig.class)).thenReturn(config); return entry;
    }
}
