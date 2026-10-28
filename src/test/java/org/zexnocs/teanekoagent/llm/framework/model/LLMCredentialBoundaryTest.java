package org.zexnocs.teanekoagent.llm.framework.model;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.zexnocs.teanekoagent.llm._app_config.SpringLLMCredentialResolver;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 Spring 凭据解析、连接校验及认证信息不外泄的边界。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class LLMCredentialBoundaryTest {
    private static final String SECRET = "test-only-secret";
    private static final String PREFIX = "tea-neko.llm.providers.deepseek.";

    /**
     * 验证凭据仅在使用指定供应商时懒加载，并读取更新后的 Spring 值。
     */
    @Test
    void resolvesOnlyRequestedProviderLazilyAndReadsUpdatedSpringValues() {
        var environment = new MockEnvironment();
        var resolver = new SpringLLMCredentialResolver(environment);
        assertThrows(LLMConfigurationException.class, () -> resolver.require("deepseek"));
        environment.setProperty(PREFIX + "api-key", SECRET);
        environment.setProperty(PREFIX + "base-url", "https://gateway.example.test/v1///");
        environment.setProperty(PREFIX + "api-path", "/chat/custom");
        var access = resolver.require("deepseek");
        assertEquals(SECRET, access.apiKey());
        assertEquals("https://gateway.example.test/v1", access.baseUrlOr("fallback"));
        assertEquals("/chat/custom", access.apiPathOr("fallback"));
        assertThrows(LLMConfigurationException.class, () -> resolver.require("kimi"));
        environment.setProperty(PREFIX + "api-key", "rotated-test-key");
        assertEquals("rotated-test-key", resolver.require("deepseek").apiKey());
        assertEquals(SECRET, access.apiKey());
    }

    /**
     * 验证环境变量占位符可以解析且凭据不被导出。
     */
    @Test
    void supportsSpringEnvironmentPlaceholderWithoutExportingTheValue() {
        var environment = new MockEnvironment()
                .withProperty("TEST_LLM_KEY", SECRET)
                .withProperty(PREFIX + "api-key", "${TEST_LLM_KEY}");
        var access = new SpringLLMCredentialResolver(environment).require("deepseek");
        assertEquals(SECRET, access.apiKey());
        assertEquals("LLMProviderAccess[redacted]", access.toString());
        assertEquals("\"LLMProviderAccess[redacted]\"", new ObjectMapper().writeValueAsString(access));
        assertEquals("https://default.test", access.baseUrlOr("https://default.test"));
        assertEquals("/default", access.apiPathOr("/default"));
    }

    /**
     * 验证缺失或非法凭据的错误与原因链不包含敏感值。
     */
    @Test
    void missingOrMalformedCredentialsHaveNoSensitiveCause() {
        for (var key : List.of("", " ", "${UNRESOLVED_TEST_KEY}", "unsafe\n" + SECRET)) {
            var resolver = new SpringLLMCredentialResolver(new MockEnvironment().withProperty(PREFIX + "api-key", key));
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
        var resolver = new SpringLLMCredentialResolver(new MockEnvironment());
        for (var provider : List.of("", "deepseek.api-key", "${TEST_LLM_KEY}", "../deepseek")) {
            var error = assertThrows(LLMConfigurationException.class, () -> resolver.require(provider));
            assertEquals("Invalid LLM credential provider identifier.", error.getMessage());
        }
    }

    /**
     * 验证嵌套或 header 形式的认证字段也不能进入普通元数据。
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
        cyclic.put("self", cyclic);
        cyclic.put("reasoning", Map.of("effort", "medium"));
        assertDoesNotThrow(() -> LLMOptionBoundary.validateMetadata(cyclic));
    }
}
