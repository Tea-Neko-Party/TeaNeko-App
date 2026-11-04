package org.zexnocs.teanekoagent.llm.framework;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.zexnocs.teanekoagent.llm.file_config.form.*;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelId;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModel;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekoagent.llm.instance.deepseek.DeepSeekConfigurationAdapter;
import org.zexnocs.teanekoagent.llm.instance.kimi.KimiConfigurationAdapter;
import org.zexnocs.teanekoagent.llm.instance.openai.completions.OpenAICompletionsConfigurationAdapter;
import org.zexnocs.teanekoagent.llm.instance.openai.responses.OpenAIResponsesConfigurationAdapter;
import org.zexnocs.teanekocore.plugin.PluginAccess;
import org.zexnocs.teanekocore.utils.scanner.BeanScanner;

import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证供应商样板通过实际 Spring 注解发现，未注册模型不暴露模板，凭据方案保持原文件语义。
 * <br>2026/11/04：通过真实组件扫描验证业务标识不会占用框架 Environment Bean 名。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.5-l
 * @version 4.7.5-l
 */
class LLMConfigurationDirectoryTest {
    /** 2026/11/04：复现实际包扫描的命名路径，避免显式注册夹具绕过启动冲突。 */
    @Test
    void componentScanKeepsFrameworkEnvironmentSeparateFromExtensionIds() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(org.zexnocs.teanekocore.database.configdata.ConfigEditService.class,
                    () -> mock(org.zexnocs.teanekocore.database.configdata.ConfigEditService.class));
            var scanner = new org.springframework.context.annotation.ClassPathBeanDefinitionScanner(context, false);
            scanner.addIncludeFilter(new org.springframework.core.type.filter.AnnotationTypeFilter(LLMCredentialInput.class));
            scanner.addIncludeFilter(new org.springframework.core.type.filter.AnnotationTypeFilter(LLMConfigurationAdapter.class));
            scanner.addIncludeFilter(new org.springframework.core.type.filter.AnnotationTypeFilter(
                    org.zexnocs.teanekoclient.web.WebRoomPreparation.class));
            scanner.scan("org.zexnocs.teanekoagent.llm", "org.zexnocs.teanekoagent.adapter.web",
                    "org.zexnocs.teanekoclient.web");
            context.refresh();
            assertSame(context.getEnvironment(), context.getBean(org.springframework.core.env.Environment.class));
            assertSame(context.getEnvironment(), context.getBean("environment"));
            assertSame(context.getBean(EnvironmentCredentialInput.class), context.getBean("environmentCredentialInput"));
            assertNotNull(context.getBean(DeepSeekConfigurationAdapter.class));
            assertNotNull(context.getBean(org.zexnocs.teanekoagent.adapter.web.WebPrivateAgentPreparation.class));
        }
    }

    /** 2026/11/04：只读公开样板，不访问运行凭据、文件或供应商网络。 */
    @Test
    void discoversProviderAdaptersAndFiltersByActualModelAndPlugin() {
        try (var context = new AnnotationConfigApplicationContext()) {
            var models = mock(ILLMModelService.class);
            var plugins = mock(PluginAccess.class);
            when(plugins.enabled(any(), any())).thenReturn(true);
            var route = new DeepSeekConfigurationAdapter().describe().defaults().get("id").toString();
            when(models.getModels()).thenReturn(Map.of(LLMModelId.of(route), mock(ILLMModel.class)));
            context.registerBean(ILLMModelService.class, () -> models);
            context.registerBean(PluginAccess.class, () -> plugins);
            context.registerBean(org.zexnocs.teanekocore.logger.ILogger.class,
                    () -> mock(org.zexnocs.teanekocore.logger.ILogger.class));
            context.register(BeanScanner.class, LLMConfigurationDirectory.class, DeepSeekConfigurationAdapter.class,
                    KimiConfigurationAdapter.class, OpenAIResponsesConfigurationAdapter.class,
                    OpenAICompletionsConfigurationAdapter.class, ManualCredentialInput.class, EnvironmentCredentialInput.class);
            context.refresh();
            var directory = context.getBean(LLMConfigurationDirectory.class);
            var catalog = directory.describe();
            assertEquals(1, catalog.routes().size());
            assertEquals(route, catalog.routes().getFirst().id());
            assertEquals("", catalog.routes().getFirst().defaults().get("api-key"));
            var environment = catalog.inputs().stream().filter(input -> input.id().equals("environment")).findFirst().orElseThrow();
            assertTrue(Pattern.matches(environment.pattern(), "MODEL_API_KEY"));
            assertFalse(Pattern.matches(environment.pattern(), "含空格 KEY"));
            assertEquals("${MODEL_API_KEY}", environment.prefix() + "MODEL_API_KEY" + environment.suffix());
            when(plugins.enabled(any(), any())).thenReturn(false);
            assertTrue(directory.describe().routes().isEmpty());
        }
    }
}
