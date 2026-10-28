package org.zexnocs.teanekoagent.llm.framework.interaction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.zexnocs.teanekoagent.llm.file_config.interfaces.ILLMFileConfigService;
import org.zexnocs.teanekoagent.llm.framework.input.LLMOptionsPolicy;
import org.zexnocs.teanekoagent.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.model.*;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.IDefaultModelResolver;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModel;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.LLMModel;
import org.zexnocs.teanekoagent.llm.framework.response.LLMChoice;
import org.zexnocs.teanekoagent.llm.framework.response.LLMResult;
import org.zexnocs.teanekoagent.llm.framework.response.LLMUsage;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekoagent.llm.framework.tool.*;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.framework.composition.ComponentPatch;
import org.zexnocs.teanekocore.framework.composition.EntityId;
import org.zexnocs.teanekocore.framework.composition.EntitySnapshot;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 使用真实模型服务、抽象适配器和交互循环验证已解析参数跨阶段固定，不发起 HTTP。
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
class LLMResolvedOptionsTest {
    private final InteractionRuntime runtime = new InteractionRuntime();
    private final ILLMFileConfigService files = mock(ILLMFileConfigService.class);
    private final IDefaultModelResolver defaults = mock(IDefaultModelResolver.class);
    private final CapturingModel adapter = new CapturingModel();
    private final AtomicReference<LLMModelOptions> configured = new AtomicReference<>(LLMModelOptions.builder()
            .provider("test").model("本轮模型").maxTokens(20).temperature(0.3)
            .metadata(Map.of("vendor.pinned", List.of("原值"))).build());
    private final LLMModelService models;

    /** 将真实模型服务接到可控文件默认值和手动完成的供应商替身。 */
    LLMResolvedOptionsTest() {
        var scanner = mock(IBeanScanner.class);
        var annotation = mock(LLMModel.class);
        when(annotation.id()).thenReturn("test");
        when(scanner.getBeansWithAnnotationAndInterface(LLMModel.class, ILLMModel.class))
                .thenReturn(Map.of("离线适配器", IndependentPair.of(annotation, adapter)));
        when(defaults.findDefaultModelId()).thenReturn(Optional.of(LLMModelId.of("test")));
        when(files.getDefaultOptions(any(), any())).thenAnswer(invocation ->
                LLMModelOptions.merge(invocation.getArgument(1), configured.get()));
        models = new LLMModelService(scanner, files, defaults);
        models.init();
    }

    /**
     * 建立只有用户正文的原始输入。
     * @return 尚未解析默认值的消息输入
     */
    private LLMPrompt input() {
        return new LLMPrompt(LLMUserMessage.builder()
                .contents(new LLMContentListBuilder().addText("离线问题").build()).build());
    }

    /**
     * 模拟宿主在轮次入口固定模型数据；不保存文件服务或执行器。
     * @return 使用不可变参数快照的已解析输入
     */
    private LLMPrompt resolved() {
        var input = input();
        var snapshot = new LLMModelOptionsSnapshot(models.resolveOptions(input));
        return new LLMPrompt(input.getMessages(), snapshot.toOptions(), LLMOptionsPolicy.RESOLVED);
    }

    /**
     * 构造最终响应或请求一次工具的模型响应。
     * @param tool 是否请求本轮工具
     * @return 含已知 usage 的可控供应商结果
     */
    private ILLMResult response(boolean tool) {
        return LLMResult.builder().usage(LLMUsage.builder().totalTokens(2).build())
                .choices(List.of(LLMChoice.builder().index(0).finishReason(tool ? "tool_calls" : "stop")
                        .message(LLMAssistantMessage.builder()
                                .contents(new LLMContentListBuilder().addText("离线正文").build())
                                .toolCalls(tool ? List.of(LLMToolCall.builder().id("一次工具")
                                        .name("reload").arguments("{}").build()) : List.of()).build()).build())).build();
    }

    /**
     * 同时改变适配器基础值和文件默认值，暴露任何意外的再次合并。
     */
    private void reload() {
        configured.set(LLMModelOptions.builder().provider("other").model("新文件模型").temperature(0.9)
                .metadata(Map.of("vendor.newFile", true)).build());
        var base = (LLMModelOptions) adapter.getBaseOptions();
        base.setTopP(0.8);
        base.setMetadata(Map.of("vendor.newBase", true));
    }

    /** 验证普通输入仍逐次读取最新默认值，已解析输入跳过文件及基础值合并。 */
    @Test void resolvedCallsPreserveAbsentFieldsAfterReload() {
        var pinned = resolved();
        clearInvocations(files, defaults);
        reload();
        var checked = models.resolveOptions(pinned);
        assertEquals("本轮模型", checked.getModel());
        models.call(pinned);
        verifyNoInteractions(files, defaults);
        var actual = adapter.received.getFirst();
        assertEquals("本轮模型", actual.getModel());
        assertEquals(0.3, actual.getTemperature());
        assertTrue(actual.findTopP().isEmpty());
        assertEquals(Map.of("vendor.pinned", List.of("原值")), actual.getMetadata());
        models.call(input());
        assertEquals("新文件模型", adapter.received.getLast().getModel());
        assertEquals(0.8, adapter.received.getLast().getTopP());
        verify(files).getDefaultOptions(any(), any());
    }

    /** 验证绕过模型服务直接调用适配器时，同样不补入基础默认值。 */
    @Test void directAdapterHonorsResolvedPolicy() {
        var options = LLMModelOptions.builder().provider("test").model("指定模型").build();
        adapter.call(new LLMPrompt(List.of(), options, LLMOptionsPolicy.RESOLVED));
        assertTrue(adapter.received.getFirst().findTemperature().isEmpty());
        adapter.call(new LLMPrompt(List.of(), options));
        assertEquals(0.1, adapter.received.getLast().getTemperature());
    }

    /** 验证已解析输入缺少路由或模型时拒绝，不能悄悄回退默认配置。 */
    @Test void incompleteResolvedOptionsNeverFallback() {
        for (var options : List.of(LLMModelOptions.empty(), LLMModelOptions.builder().provider("test").build(),
                LLMModelOptions.builder().provider(" test").model("模型").build(),
                LLMModelOptions.builder().provider("test").model(" ").build())) {
            var prompt = new LLMPrompt(List.of(), options, LLMOptionsPolicy.RESOLVED);
            assertThrows(IllegalArgumentException.class, () -> models.call(prompt));
            assertThrows(IllegalArgumentException.class, () -> adapter.call(prompt));
        }
        verifyNoInteractions(files, defaults);
        assertTrue(adapter.received.isEmpty());
    }

    /** 验证显式注册路由与已解析 provider 冲突时拒绝，避免改变本轮路由。 */
    @Test void mismatchedExplicitRouteIsRejected() {
        var prompt = new LLMPrompt(List.of(), LLMModelOptions.builder().provider("other").model("模型").build(),
                LLMOptionsPolicy.RESOLVED);
        assertThrows(IllegalArgumentException.class, () -> models.call(LLMModelId.of("test"), prompt));
        verifyNoInteractions(files, defaults);
        assertTrue(adapter.received.isEmpty());
    }

    /** 验证已解析策略保留认证和能力校验，不能用来绕开安全边界。 */
    @Test void resolvedOptionsStillValidateSecretsAndCapabilities() {
        var options = LLMModelOptions.builder().provider("test").model("指定模型")
                .metadata(Map.of("apiKey", "离线密钥占位")).build();
        var prompt = new LLMPrompt(List.of(), options, LLMOptionsPolicy.RESOLVED);
        assertThrows(LLMConfigurationException.class, () -> models.call(prompt));
        assertThrows(LLMConfigurationException.class, () -> adapter.call(prompt));
        options.setMetadata(Map.of());
        adapter.supported = false;
        assertThrows(IllegalArgumentException.class, () -> models.call(prompt));
        assertThrows(IllegalArgumentException.class, () -> adapter.call(prompt));
        assertTrue(adapter.received.isEmpty());
        verifyNoInteractions(files, defaults);
    }

    /** 验证兼容的 provider/model 覆盖入口不会丢失已解析策略。 */
    @Test void explicitModelConvenienceCallPreservesPolicy() {
        var pinned = resolved();
        clearInvocations(files, defaults);
        reload();
        models.call("test", "显式模型", pinned);
        assertEquals("显式模型", adapter.received.getFirst().getModel());
        assertFalse(adapter.received.getFirst().getMetadata().containsKey("vendor.newBase"));
        verifyNoInteractions(files, defaults);
    }

    /** 验证解析策略留在本地控制面，不作为 Prompt 的 JSON 属性导出。 */
    @Test void policyIsExcludedFromPromptJson() {
        var json = new ObjectMapper().valueToTree(new LLMPrompt(List.of(), null, LLMOptionsPolicy.RESOLVED));
        assertFalse(json.has("optionsPolicy"));
        assertFalse(json.toString().contains("RESOLVED"));
    }

    /**
     * 验证真实工具往返中，配置热更新和原始 DTO 修改均不能改变本轮参数；token 预检也保留策略。
     * @param tokenBudget 是否开启总 token 准入和输出上限收紧
     */
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void interactionPinsOptionsAcrossToolRound(boolean tokenBudget) {
        var pinned = resolved();
        clearInvocations(files, defaults);
        var limits = new LLMCallLimits(3, 2, 6, tokenBudget ? 18L : null);
        var settings = new LLMInteractionSettings(limits, LLMInteractionSettings.defaults().modelTimeout(),
                LLMInteractionSettings.defaults().toolTimeout(), LLMInteractionSettings.defaults().turnTimeout());
        var service = new LLMInteractionService(models, () -> settings, runtime, runtime.scheduler,
                prompt -> new LLMTokenEstimate(5, "fixed-v1"));
        var tools = new LLMToolSet(new ObjectMapper(), List.of(new LLMToolBinding(
                LLMTool.builder().name("reload").build(), (call, context) -> {
                    reload();
                    return CompletableFuture.completedStage("配置已更新");
                })));
        try (var parent = runtime.parent(limits)) {
            var context = new ToolInvocationContext(UUID.randomUUID(),
                    EntitySnapshot.of(EntityId.random(), ComponentPatch.builder().build()), parent);
            var pending = service.interact(pinned, tools, context, settings);
            assertInstanceOf(LLMModelOptions.class, pinned.getOptions(), "本用例必须持有可修改的原始参数。")
                    .setModel("调用方后改模型");
            runtime.runAll();
            assertFalse(pending.toCompletableFuture().isDone());
            adapter.replies.getFirst().complete(response(true));
            runtime.runAll();
            assertEquals(2, adapter.received.size());
            assertFalse(pending.toCompletableFuture().isDone());
            adapter.replies.getLast().complete(response(false));
            runtime.runAll();
            var result = pending.toCompletableFuture().join();
            assertEquals(LLMInteractionResult.Status.SUCCESS, result.status());
            assertEquals(2, result.modelAttempts());
            assertEquals(1, result.tools().size());
            for (var actual : adapter.received) {
                assertEquals("本轮模型", actual.getModel());
                assertEquals("test", actual.getProvider());
                assertEquals(0.3, actual.getTemperature());
                assertTrue(actual.findTopP().isEmpty());
                assertEquals(Map.of("vendor.pinned", List.of("原值")), actual.getMetadata());
            }
            if (tokenBudget) {
                assertEquals(13, adapter.received.getFirst().getMaxTokens());
                assertEquals(11, adapter.received.getLast().getMaxTokens());
            }
            verifyNoInteractions(files, defaults);
        }
    }

    /**
     * 通过真正的抽象适配器准备参数，仅将网络边界替换为可手动完成的任务。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private static final class CapturingModel extends AbstractLLMModel {
        private final List<LLMModelOptions> received = new ArrayList<>();
        private final List<CompletableFuture<ILLMResult>> replies = new ArrayList<>();
        private boolean supported = true;

        /** 固定初始基础参数，以便区分基础默认值与本轮已解析值。 */
        private CapturingModel() {
            super("test", "基础模型", LLMModelOptions.builder().temperature(0.1).build());
        }

        /**
         * 由用例控制能力验证结果。
         * @param options 当前有效参数
         * @return 当前是否允许执行
         */
        @Override public boolean supports(ILLMModelOptions options) { return supported; }

        /**
         * 捕获适配器最终参数并建立未完成的供应商任务。
         * @param prompt 经参数策略和能力校验的输入
         * @param options 最终有效参数
         * @return 可由用例完成的模型任务
         */
        @Override protected TaskFuture<ILLMResult> doCall(ILLMPrompt prompt, LLMModelOptions options) {
            received.add(options);
            var reply = new CompletableFuture<ILLMResult>();
            replies.add(reply);
            return new TaskFuture<>(mock(ILogger.class), "已解析参数离线调用", reply);
        }

        /**
         * 保留显式执行策略的真正适配器入口，网络资源由本夹具的任务替代。
         * @param prompt 经校验的输入
         * @param options 最终有效参数
         * @param execution 借用的阶段执行策略
         * @return 可由用例完成的模型任务
         */
        @Override protected TaskFuture<ILLMResult> doCall(ILLMPrompt prompt, LLMModelOptions options,
                                                         ExecutionPolicy execution) {
            execution.checkActive();
            return doCall(prompt, options);
        }
    }
}
