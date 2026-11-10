package org.zexnocs.teanekoagent.llm.framework.model;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

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
import org.zexnocs.teanekocore.logger.FailureDiagnostic;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * 验证具体模型的图片能力声明与实际调用前的输入检查。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：视觉模型夹具补充上下文预算与已解析的额度凭据。
 * <br>2026/11/04：校正当前实现的版本归属为 v4.7.2-l。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：根据原提交职责恢复编码损坏的历史注释。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 * <br>2026/11/10：验证视觉模型选择、配置隔离与资源结束边界。
 * <br>2026/11/10：校验错误跨异步包装保留位置及能力对照，图片、正文和未知类型不进入诊断。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.9.1-l
 */
class LLMImageCapabilityTest {
    /** 2026/11/10：角色和细节不匹配必须报告实际位置及允许值，同时隐藏图片地址和消息正文。 */
    @Test
    void inputFailureReportsPositionAndCapabilityComparisonWithoutSensitiveContent() {
        var first = LLMUserMessage.builder().contents(new LLMContentListBuilder().addText("private-chat").build()).build();
        var second = LLMSystemMessage.builder().contents(new LLMContentListBuilder().addText("private-system")
                .addImage("https://example.test/private-image?key=private-key", LLMImageDetail.ORIGINAL).build()).build();
        var failure = assertThrows(LLMInputValidationException.class, () -> LLMInputValidator.validate(
                List.of(first, second), ModelCapabilities.userImages(Set.of(LLMImageDetail.AUTO))));
        var diagnostic = FailureDiagnostic.from(new java.util.concurrent.CompletionException(failure));
        String reason = diagnostic.description();
        assertTrue(reason.contains("messages[1].contents[1].image"));
        assertTrue(reason.contains("要求=允许图片角色=[USER]，允许图片细节=[AUTO]"));
        assertTrue(reason.contains("实际=图片角色=SYSTEM，图片细节=ORIGINAL"));
        assertTrue(reason.contains("发送前"));
        assertFalse(diagnostic.toString().contains("private-"));
        assertFalse(reason.contains("\n"));
        assertNull(diagnostic.httpStatus());
    }

    /** 2026/11/10：未知兼容型号明确报缺少图片声明；启用后通过同一预检且输出额度仍由模型决定。 */
    @Test
    void explicitImageDeclarationChangesAdmissionWithoutReducingOutputLimit() {
        var model = new OpenAIResponsesModel(mock(IAPIResponseService.class), mock(ICredentialResolver.class));
        var options = LLMModelOptions.builder().provider("openai").model("compatible-vision")
                .maxTokens(163840).build();
        var prompt = imagePrompt(options.getModel(), LLMImageDetail.AUTO);
        var failure = assertThrows(LLMInputValidationException.class, () -> LLMInputValidator.validate(
                prompt.getMessages(), model.inputCapabilities(options)));
        assertTrue(FailureDiagnostic.from(failure).description().contains("当前模型未声明图片输入能力"));
        var enabled = LLMModelOptions.merge(options, LLMModelOptions.builder().imageInput(true).build());
        assertDoesNotThrow(() -> LLMInputValidator.validate(prompt.getMessages(), model.inputCapabilities(enabled)));
        var request = org.zexnocs.teanekoagent.llm.instance.openai.responses.OpenAIResponsesMapper
                .toRequest(prompt, enabled, "private-key");
        var json = new tools.jackson.databind.ObjectMapper().valueToTree(request);
        assertEquals(163840, json.path("max_output_tokens").asInt());
    }

    /** 2026/11/10：不匹配的自定义内容只报告结构类别，未知 type 和正文不能进入公开原因。 */
    @Test
    void malformedContentReportDoesNotEchoUnknownTypeOrText() {
        var content = mock(org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMContent.class);
        when(content.getType()).thenReturn("private-type\nprivate-key");
        when(content.getContentPart()).thenReturn(
                new org.zexnocs.teanekoagent.llm.framework.message.content.TextLLMContentPart("private-text"));
        var failure = assertThrows(LLMInputValidationException.class, () -> LLMInputValidator.requirePart(content));
        String reason = FailureDiagnostic.from(failure).description();
        assertTrue(reason.contains("类型声明=未知类型（原值已隐藏），内容实体=文本"));
        assertFalse(reason.contains("private-"));
        assertFalse(reason.contains("\n"));
    }

    /** 2026/11/10：显式图片声明贯穿配置、快照和能力准入，本地声明不发送到服务端。 */
    @Test
    void explicitVisionDeclarationSupportsCompatibleUnknownModels() {
        var json = tools.jackson.databind.json.JsonMapper.builder()
                .propertyNamingStrategy(tools.jackson.databind.PropertyNamingStrategies.KEBAB_CASE).build();
        var entry = json.readValue("{\"id\":\"compatible\",\"adapter\":\"openai\",\"model\":\"custom-vision\",\"image-input\":true,\"max-tokens\":256,\"context-window-tokens\":8192}",
                LLMModelFileConfigParameter.class);
        var model = new OpenAIResponsesModel(mock(IAPIResponseService.class), mock(ICredentialResolver.class));
        var options = entry.toOptions(model.getBaseOptions());
        assertTrue(model.inputCapabilities(options).supportsImages());
        assertEquals(Boolean.TRUE, new LLMModelOptionsSnapshot(options).toOptions().findImageInput().orElseThrow());
        var disabled = LLMModelOptions.merge(options, LLMModelOptions.builder().imageInput(false).build());
        assertFalse(model.inputCapabilities(disabled).supportsImages());
        var known = LLMModelOptions.builder().model("gpt-5.5").imageInput(true).build();
        assertTrue(model.inputCapabilities(known).imageDetails().contains(LLMImageDetail.ORIGINAL));
        var request = org.zexnocs.teanekoagent.llm.instance.openai.responses.OpenAIResponsesMapper.toRequest(
                imagePrompt("custom-vision", LLMImageDetail.LOW), options, "test-secret");
        String encoded = json.writeValueAsString(request);
        assertTrue(encoded.contains("input_image"));
        assertFalse(encoded.contains("image-input"));
        assertFalse(encoded.contains("imageInput"));
        assertFalse(encoded.contains("test-secret"));
    }

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
        // 2026/11/04：实际请求携带已解析参数读取同一代次凭据。
        when(credentials.require(eq("deepseek"), any())).thenReturn(new LLMProviderAccess("fake-key", null, null));
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
        // 2026/11/04：视觉测试模型显式声明上下文与输出容量。
        entry.setMaxTokens(2048);
        entry.setContextWindowTokens(131072L);
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
