package org.zexnocs.teanekoagent.component.llm.framework;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.zexnocs.teanekoagent.component.llm.file_config.form.*;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelId;
import org.zexnocs.teanekoagent.component.llm.framework.model.interfaces.ILLMModel;
import org.zexnocs.teanekoagent.component.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekoagent.component.llm.instance.deepseek.DeepSeekConfigurationAdapter;
import org.zexnocs.teanekoagent.component.llm.instance.kimi.KimiConfigurationAdapter;
import org.zexnocs.teanekoagent.component.llm.instance.openai.completions.OpenAICompletionsConfigurationAdapter;
import org.zexnocs.teanekoagent.component.llm.instance.openai.responses.OpenAIResponsesConfigurationAdapter;
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
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：确认扩展只发现一次，插件状态与实际模型注册仍实时更新。
 * <br>2026/11/05：凭据目录重扫失败时保留已发布的完整模型配置目录。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.5-l
 * @version 4.8.2-l
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
            scanner.scan("org.zexnocs.teanekoagent.component.llm", "org.zexnocs.teanekoagent.integration.web",
                    "org.zexnocs.teanekoclient.web");
            context.refresh();
            assertSame(context.getEnvironment(), context.getBean(org.springframework.core.env.Environment.class));
            assertSame(context.getEnvironment(), context.getBean("environment"));
            assertSame(context.getBean(EnvironmentCredentialInput.class), context.getBean("environmentCredentialInput"));
            assertNotNull(context.getBean(DeepSeekConfigurationAdapter.class));
            assertNotNull(context.getBean(org.zexnocs.teanekoclient.web.agent.WebPrivateAgentPreparation.class));
        }
    }

    /**
     * 2026/11/04：只读公开样板，不访问运行凭据、文件或供应商网络。
     * 2026/11/05：真实重载生命周期验证失败初始化不由查询重试，显式重扫恢复后复用缓存及实时启停。
     */
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
            var logger = mock(org.zexnocs.teanekocore.logger.ILogger.class);
            // 2026/11/05：由真实重载服务在容器就绪前初始化，目录查询不触发扫描。
            context.registerBean(org.zexnocs.teanekocore.logger.ILogger.class, () -> logger);
            var scanner = spy(new BeanScanner(context, logger));
            // 2026/11/05：首次初始化失败由原重载服务保留恢复入口，业务查询不能反复扫描。
            doThrow(new IllegalStateException("测试首次凭据目录初始化失败。")).doCallRealMethod().when(scanner)
                    .getBeansWithAnnotationAndInterface(LLMCredentialInput.class, ILLMCredentialInput.class);
            context.registerBean(BeanScanner.class, () -> scanner);
            context.register(org.zexnocs.teanekocore.reload.ReloadService.class,
                    LLMConfigurationDirectory.class, DeepSeekConfigurationAdapter.class,
                    KimiConfigurationAdapter.class, OpenAIResponsesConfigurationAdapter.class,
                    OpenAICompletionsConfigurationAdapter.class, ManualCredentialInput.class, EnvironmentCredentialInput.class);
            context.refresh();
            var directory = context.getBean(LLMConfigurationDirectory.class);
            assertThrows(IllegalStateException.class, directory::describe);
            verify(scanner, times(1)).getBeansWithAnnotationAndInterface(LLMCredentialInput.class, ILLMCredentialInput.class);
            context.getBean(org.zexnocs.teanekocore.reload.ReloadService.class).reloadAll();
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
            assertTrue(directory.describe().inputs().isEmpty());
            when(plugins.enabled(any(), any())).thenReturn(true);
            assertEquals(1, directory.describe().routes().size());
            when(models.getModels()).thenReturn(Map.of());
            assertTrue(directory.describe().routes().isEmpty());
            verify(scanner, times(2)).getBeansWithAnnotationAndInterface(
                    LLMConfigurationAdapter.class, ILLMConfigurationAdapter.class);
            verify(scanner, times(2)).getBeansWithAnnotationAndInterface(
                    LLMCredentialInput.class, ILLMCredentialInput.class);
            // 2026/11/05：显式重扫失败不使已经发布的凭据输入目录消失。
            var originalInputs = directory.describe().inputs();
            doThrow(new IllegalStateException("测试凭据目录重扫失败。")).when(scanner)
                    .getBeansWithAnnotationAndInterface(LLMCredentialInput.class, ILLMCredentialInput.class);
            assertThrows(IllegalStateException.class, directory::reload);
            assertEquals(originalInputs, directory.describe().inputs());
        }
    }
}
