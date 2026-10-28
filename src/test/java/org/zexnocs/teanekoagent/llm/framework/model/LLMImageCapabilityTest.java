package org.zexnocs.teanekoagent.llm.framework.model;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.llm.file_config.LLMFileConfigService;
import org.zexnocs.teanekoagent.llm.file_config.LLMModelFileConfig;
import org.zexnocs.teanekoagent.llm.file_config.LLMModelFileConfigParameter;
import org.zexnocs.teanekoagent.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.message.LLMSystemMessage;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMImageDetail;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.LLMMessageRole;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ICredentialResolver;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModel;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.LLMModel;
import org.zexnocs.teanekoagent.llm.instance.deepseek.DeepSeekChatModel;
import org.zexnocs.teanekoagent.llm.instance.kimi.KimiChatModel;
import org.zexnocs.teanekoagent.llm.instance.openai.completions.OpenAIChatCompletionsModel;
import org.zexnocs.teanekoagent.llm.instance.openai.responses.OpenAIResponsesModel;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.api_response.interfaces.IAPIResponseService;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 验证具体模型的图片能力声明与实际调用前的输入检查。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class LLMImageCapabilityTest {
    private static final List<BiFunction<IAPIResponseService, ICredentialResolver, AbstractLLMModel>> MODELS = List.of(
            DeepSeekChatModel::new, OpenAIResponsesModel::new, KimiChatModel::new, OpenAIChatCompletionsModel::new);

    /**
     * 验证内置图片能力按精确模型名声明，不按整个供应商推测。
     */
    @Test
    void builtInsDeclareExactModelsRatherThanInferringAProviderWideCapability() {
        var api = mock(IAPIResponseService.class);
        var credentials = mock(ICredentialResolver.class);
        var deepseek = new DeepSeekChatModel(api, credentials);
        for (var model : List.of("deepseek-flash", "deepseek-v4-flash", "deepseek-v4-flash-vision-exp")) {
            assertTrue(deepseek.getCapabilities(model).supportsImages());
        }
        for (var model : List.of("deepseek-v4-pro", "deepseek-reasoner", "deepseek-future-flash")) {
            assertFalse(deepseek.getCapabilities(model).supportsImages());
        }
        var kimi = new KimiChatModel(api, credentials);
        assertTrue(kimi.getCapabilities("kimi-k2.6").supportsImages());
        assertTrue(kimi.getCapabilities("kimi-k2.7-code-highspeed").supportsImages());
        assertFalse(kimi.getCapabilities("kimi-k2").supportsImages());
        var openai = new OpenAIResponsesModel(api, credentials);
        assertTrue(openai.getCapabilities("gpt-5.5-2026-04-23").supportsImages());
        assertFalse(openai.getCapabilities("gpt-5.5-unknown-snapshot").supportsImages());
        assertTrue(openai.getCapabilities("gpt-5.5").imageDetails().contains(LLMImageDetail.ORIGINAL));
        assertFalse(openai.getCapabilities("gpt-4.1").imageDetails().contains(LLMImageDetail.ORIGINAL));
        assertEquals(Set.of(LLMImageDetail.AUTO), kimi.getCapabilities("kimi-k2.6").imageDetails());
        verifyNoInteractions(api, credentials);
    }

    /**
     * 验证支持图片的调用只提交一次请求并等待其实际完成。
     *
     */
    @Test
    void supportedImageCallSubmitsOneRequestAndWaitsForIt() {
        for (var factory : MODELS) {
            var api = mock(IAPIResponseService.class);
            var model = factory.apply(api, ignored -> new LLMProviderAccess("fake-key", null, null));
            doAnswer(invocation -> new TaskFuture<>(mock(ILogger.class), "fake-http", new CompletableFuture<>()))
                    .when(api).addTask(any(), any(), eq(true), eq(true));
            var result = model.call(imagePrompt(model.getModel(), LLMImageDetail.AUTO));
            assertFalse(result.asStage().toCompletableFuture().isDone());
            verify(api).addTask(any(), any(), eq(true), eq(true));
            verifyNoMoreInteractions(api);
        }
    }

    /**
     * 验证未知模型不会继承适配器默认模型的图片能力。
     */
    @Test
    void unknownModelCannotInheritTheAdaptersDefaultImageCapability() {
        for (var factory : MODELS) {
            var api = mock(IAPIResponseService.class);
            var credentials = mock(ICredentialResolver.class);
            var model = factory.apply(api, credentials);
            assertThrows(IllegalArgumentException.class, () -> model.call(imagePrompt("unknown-model", LLMImageDetail.AUTO)));
            verifyNoInteractions(api, credentials);
        }
    }

    /**
     * 验证非法图片角色或细节级别在凭据读取前被拒绝。
     */
    @Test
    void invalidImageRoleOrDetailFailsBeforeCredentialLookup() {
        for (var factory : MODELS) {
            var api = mock(IAPIResponseService.class);
            var credentials = mock(ICredentialResolver.class);
            var model = factory.apply(api, credentials);
            var systemImage = new LLMPrompt(LLMSystemMessage.builder().contents(LLMContentListBuilder.builder()
                    .addImage("https://example.test/image.png").build()).build());
            assertThrows(IllegalArgumentException.class, () -> model.call(systemImage));
            verifyNoInteractions(api, credentials);
        }
        var api = mock(IAPIResponseService.class);
        var credentials = mock(ICredentialResolver.class);
        assertThrows(IllegalArgumentException.class, () -> new KimiChatModel(api, credentials).call(imagePrompt("kimi-k2.6", LLMImageDetail.LOW)));
        assertThrows(IllegalArgumentException.class, () -> new OpenAIResponsesModel(api, credentials).call(imagePrompt("gpt-4.1", LLMImageDetail.ORIGINAL)));
        verifyNoInteractions(api, credentials);
    }

    /**
     * 验证模型服务按文件与单次覆盖后的实际模型验证输入。
     *
     */
    @Test
    void modelServiceValidatesTheEffectiveFileAndPromptModel() {
        var api = mock(IAPIResponseService.class);
        var credentials = mock(ICredentialResolver.class);
        when(credentials.require("deepseek")).thenReturn(new LLMProviderAccess("fake-key", null, null));
        doAnswer(invocation -> new TaskFuture<>(mock(ILogger.class), "fake-http", new CompletableFuture<>()))
                .when(api).addTask(any(), any(), eq(true), eq(true));
        var model = new DeepSeekChatModel(api, credentials);
        var scanner = mock(IBeanScanner.class);
        var annotation = DeepSeekChatModel.class.getAnnotation(LLMModel.class);
        when(scanner.getBeansWithAnnotationAndInterface(LLMModel.class, ILLMModel.class))
                .thenReturn(Map.of("deepseek", IndependentPair.of(annotation, model)));
        var files = mock(IFileConfigService.class);
        var entry = new LLMModelFileConfigParameter();
        entry.setId("deepseek");
        entry.setModel("deepseek-v4-pro");
        var config = new LLMModelFileConfig();
        config.setModels(List.of(entry));
        when(files.get(LLMModelFileConfig.class)).thenReturn(config);
        var service = new LLMModelService(scanner, new LLMFileConfigService(files), () -> Optional.of(LLMModelId.of("deepseek")));
        service.init();
        // 文件指定 Pro 时不能沿用代码内建 Flash 的视觉能力。
        assertThrows(IllegalArgumentException.class, () -> service.call(imagePrompt(null, LLMImageDetail.AUTO)));
        verifyNoInteractions(api, credentials);
        // 显式覆盖为 Flash 后放行，但不会改变文件配置。
        service.call(imagePrompt("deepseek-flash", LLMImageDetail.AUTO));
        assertEquals("deepseek-v4-pro", entry.getModel());
        verify(api).addTask(any(), any(), eq(true), eq(true));
        // 下一次文件配置变化重新解析，仍按本次的具体模型判断。
        entry.setModel("deepseek-flash");
        assertThrows(IllegalArgumentException.class, () -> service.call(imagePrompt("deepseek-v4-pro", LLMImageDetail.AUTO)));
        verifyNoMoreInteractions(api);
    }

    /**
     * 验证未列入视觉能力声明的模型仍可执行文本调用。
     *
     */
    @Test
    void textOnlyCallsStillPermitUnlistedModelNames() {
        for (var factory : MODELS) {
            var api = mock(IAPIResponseService.class);
            var model = factory.apply(api, ignored -> new LLMProviderAccess("fake-key", null, null));
            doAnswer(invocation -> new TaskFuture<>(mock(ILogger.class), "fake-http", new CompletableFuture<>()))
                    .when(api).addTask(any(), any(), eq(true), eq(true));
            model.call(new LLMPrompt(LLMUserMessage.builder().contents(LLMContentListBuilder.builder().addText("hello").build()).build(),
                    LLMModelOptions.builder().model("unlisted-text-model").build()));
            verify(api).addTask(any(), any(), eq(true), eq(true));
        }
    }

    /**
     * 验证能力快照不受原集合或调用方后续修改影响。
     */
    @Test
    void capabilitySnapshotsCannotBeChangedByTheOriginalSetsOrCaller() {
        var roles = new HashSet<>(Set.of(LLMMessageRole.USER));
        var details = new HashSet<>(Set.of(LLMImageDetail.AUTO));
        var capabilities = new ModelCapabilities(roles, details);
        roles.clear();
        details.clear();
        assertTrue(capabilities.supportsImages());
        assertThrows(UnsupportedOperationException.class, capabilities.imageInputRoles()::clear);
        assertThrows(IllegalArgumentException.class, () -> new ModelCapabilities(Set.of(), Set.of(LLMImageDetail.AUTO)));
    }

    /**
     * 构造指定模型和细节级别的图像 Prompt，供调用前能力验证。
     *
     * @param model 本次实际选中的具体模型名称
     * @param detail 模型协议使用的图片细节级别
     * @return 满足本方法约束的 {@code LLMPrompt} 结果
     */
    private static LLMPrompt imagePrompt(String model, LLMImageDetail detail) {
        return new LLMPrompt(LLMUserMessage.builder().contents(LLMContentListBuilder.builder()
                .addText("describe").addImage("https://example.test/image.png", detail).build()).build(),
                LLMModelOptions.builder().model(model).build());
    }
}
