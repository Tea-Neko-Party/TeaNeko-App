package org.zexnocs.teanekoplugin.agent.expression.sticker.model;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.zexnocs.teanekoagent.component.AgentAssembly;
import org.zexnocs.teanekoagent.component.AgentComponentRegistry;
import org.zexnocs.teanekoagent.component.llm.LLMComponent;
import org.zexnocs.teanekoagent.component.llm.ScopedLLMRouteSource;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekocore.database.configdata.ConfigDataRegisterService;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.database.easydata.core.EasyDataRow;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataDto;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataTransactionDto;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.file_config.parser.YAMLFileTypeParser;
import org.zexnocs.teanekocore.plugin.PluginDeclarationScanner;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekoplugin.agent.expression.ExpressionPlugin;
import org.zexnocs.teanekoplugin.contract.agent.AgentComponentFixtures;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 视觉模型选择、私有路由、秘密投影和插件撤销的联合回归。
 * <br>2026/11/10：使用真实组件目录和路由读取，防止把运行版本误当持久版本。
 * @author zExNocs
 * @date 2026/11/10
 * @since 4.9.1-l
 * @version 4.9.1-l
 */
class StickerModelSelectionTest {
    private final ObjectMapper json = new ObjectMapper();
    private final PluginRegistry plugins = new PluginRegistry(List.of(
            PluginDeclarationScanner.declaration(ExpressionPlugin.class)::descriptor),
            List.of(new org.zexnocs.teanekocore.plugin.HostCapability("teaneko.agent", "1", java.util.Set.of())));
    private final AgentComponentRegistry components = AgentComponentFixtures.directory(plugins);
    private final AgentScopeConfigData config = new AgentScopeConfigData();
    private final StickerModelFileConfig global = new StickerModelFileConfig();
    private final AgentScope scope = new AgentScope("test", "group@test");
    private final IFileConfigService files = mock(IFileConfigService.class);
    private final IConfigDataService configs = mock(IConfigDataService.class);
    private final AgentScopeConfigManager manager = new AgentScopeConfigManager();
    private final StickerModelSelection selections = new StickerModelSelection(configs, manager, components, files, json);

    /** 初始化完整装配和已应用的备用配置。 */
    StickerModelSelectionTest() {
        config.setEnabled(true);
        config.setComponents("{\"version\":1,\"components\":{\"llm\":{\"source\":\"system\",\"route\":\"chat\"},\"tool\":{},\"expression\":{},\"sticker\":{}}}");
        when(configs.getConfigData(manager, AgentScopeConfigData.class, scope.scopeId())).thenReturn(Optional.of(config));
        when(files.requireCurrent(StickerModelFileConfig.class)).thenReturn(global);
    }

    /** 默认继承、明确选择、旧备用兼容及原始版本变化均被正确表达。 */
    @Test
    void inheritsOrOverridesWithoutChangingChatModel() {
        var inherited = selections.plan(scope, "");
        assertEquals("chat", inherited.primary());
        assertTrue(inherited.fallback().isEmpty());
        var document = AgentAssembly.document(config.getComponents());
        ((ObjectNode) document.path("components").path("sticker")).putObject("model")
                .put("source", "system").put("route", "vision");
        config.setComponents(document.toString());
        global.setFallback(json.readTree("{\"source\":\"system\",\"route\":\"backup\"}"));
        var plan = selections.plan(scope, "legacy");
        assertEquals("vision", plan.primary());
        assertEquals("backup", plan.fallback().orElseThrow());
        assertNotEquals(inherited.revision(), plan.revision());
        assertEquals("chat", config.assembly().settings("llm").path("route").asString());
        global.setFallback(json.readTree("{\"source\":\"none\"}"));
        assertEquals("legacy", selections.plan(scope, "legacy").fallback().orElseThrow());
        plugins.setEnabled("agent.expression", false);
        assertThrows(RuntimeException.class, () -> selections.plan(scope, ""));
    }

    /** 私有组件使用原行锁、秘密和原文版本，停用后不能通过旧路由继续读取。 */
    @Test
    void privateModelUsesStoredRevisionAndKeepsSecretsAtTheirOriginalPath() {
        var document = AgentAssembly.document(config.getComponents());
        ((ObjectNode) document.path("components").path("sticker")).set("model", json.readTree("""
                {"source":"custom","configuration":{"adapter":"openai","model":"custom-vision",
                "api-key":"private-test-key","base-url":"https://example.test","api-path":"/v1/responses",
                "max-tokens":256,"context-window-tokens":4096,"image-input":true}}
                """));
        config.setComponents(document.toString());
        var plan = selections.plan(scope, "");
        assertTrue(plan.primary().contains(config.assembly().revision()));
        assertFalse(components.effective(config.getComponents()).settings("sticker").path("model").has("configuration"));
        String editor = components.editor(json.writeValueAsString(config));
        assertFalse(editor.contains("private-test-key"));
        assertTrue(editor.contains("$keep"));
        assertTrue(components.mergeSecrets(editor, config.getComponents()).contains("private-test-key"));
        var registration = mock(ConfigDataRegisterService.class);
        var dto = mock(IEasyDataDto.class);
        var atomic = mock(IEasyDataTransactionDto.class);
        var row = mock(EasyDataRow.class);
        when(registration.getDto(any())).thenReturn(dto);
        when(dto.atomic()).thenReturn(atomic);
        when(atomic.find(eq(scope.scopeId()), any())).thenReturn(row);
        when(row.getValue()).thenAnswer(ignored -> json.writeValueAsString(config));
        var transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("components", components);
        var source = new ScopedLLMRouteSource(registration, json, transactions,
                new LLMComponent(new YAMLFileTypeParser()), beans.getBeanProvider(AgentComponentRegistry.class));
        assertEquals("private-test-key", source.credentials(plan.primary()).apiKey());
        plugins.setEnabled("agent.expression", false);
        assertThrows(RuntimeException.class, () -> source.credentials(plan.primary()));
        plugins.setEnabled("agent.expression", true);
        config.setComponents(config.getComponents().replace("custom-vision", "changed-vision"));
        assertThrows(RuntimeException.class, () -> source.credentials(plan.primary()));
    }
}
