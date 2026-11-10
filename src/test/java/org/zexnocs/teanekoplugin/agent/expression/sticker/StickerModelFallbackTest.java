package org.zexnocs.teanekoplugin.agent.expression.sticker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.zexnocs.teanekoagent.integration.config.AgentComponentAccess;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionSettings;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMImageDetail;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.llm.framework.model.ModelCapabilities;
import org.zexnocs.teanekoagent.llm.framework.structured.LLMJsonService;
import org.zexnocs.teanekoagent.runtime.AgentFactory;
import org.zexnocs.teanekoagent.runtime.AgentRegistry;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoagent.runtime.IAgentFactory;
import org.zexnocs.teanekoagent.runtime.access.AgentAccessDecision;
import org.zexnocs.teanekoagent.runtime.access.AgentAccessGate;
import org.zexnocs.teanekoagent.runtime.access.AgentReadiness;
import org.zexnocs.teanekoagent.runtime.access.AgentRuntimeSnapshot;
import org.zexnocs.teanekoagent.runtime.access.AgentScopePolicy;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekoplugin.agent.expression.sticker.file_config.AgentStickerFileConfig;
import org.zexnocs.teanekoplugin.agent.expression.sticker.model.StickerModelSelection;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 主备视觉请求按同一权限、固定配置和持久额度串行执行。
 * <br>2026/11/10：覆盖仅一次备用、启动前不可用、额度不足、取消和配置撤销。
 * @author zExNocs
 * @date 2026/11/10
 * @since 4.9.1-l
 * @version 4.9.1-l
 */
class StickerModelFallbackTest {
    /** 主请求失败后只调用一次备用；备用失败没有第三次。 */
    @Test
    void usesAtMostOneFallbackAndChargesAdditionalCall() {
        try (var h = new Harness(); var control = ExecutionControl.create()) {
            var charges = new AtomicInteger();
            var result = h.analyzer.analyze(h.source, h.prepared, h.file.snapshot(), control, () -> {
                charges.incrementAndGet();
                return CompletableFuture.completedStage(true);
            }).toCompletableFuture();
            assertEquals(List.of("primary"), h.routes);
            h.pending.getFirst().completeExceptionally(new IllegalStateException("主模型合成失败"));
            assertEquals(List.of("primary", "backup"), h.routes);
            assertEquals(1, charges.get());
            h.pending.getLast().completeExceptionally(new IllegalStateException("备用合成失败"));
            assertThrows(CompletionException.class, result::join);
            assertEquals(2, h.routes.size());
        }
    }

    /** 启动前没有视觉能力时，只发一次备用请求，不重复占用第二次额度。 */
    @Test
    void unavailablePrimaryUsesFallbackAsFirstCall() {
        try (var h = new Harness(); var control = ExecutionControl.create()) {
            h.primaryImages = false;
            var result = h.analyzer.analyze(h.source, h.prepared, h.file.snapshot(), control,
                    () -> {
                        throw new AssertionError("尚未发起首选请求，不得追加额度。");
                    }).toCompletableFuture();
            assertEquals(List.of("backup"), h.routes);
            h.pending.getFirst().complete(answer());
            assertEquals(Stickers.Kind.OTHER, result.join().kind());
        }
    }

    /** 取消、配置变更、缺少备用及额度不足均不能再发起请求。 */
    @Test
    void refusesFallbackAfterCancellationChangeOrQuotaExhaustion() {
        for (String reason : List.of("cancel", "changed", "none", "quota")) {
            try (var h = new Harness(); var control = ExecutionControl.create()) {
                if (reason.equals("none")) {
                    h.plan = new StickerModelSelection.Plan("primary", Optional.empty(), "v1");
                }
                var result = h.analyzer.analyze(h.source, h.prepared, h.file.snapshot(), control,
                        () -> CompletableFuture.completedStage(false)).toCompletableFuture();
                if (reason.equals("cancel")) {
                    control.cancel();
                }
                if (reason.equals("changed")) {
                    h.plan = new StickerModelSelection.Plan("primary", Optional.of("backup"), "v2");
                }
                h.pending.getFirst().completeExceptionally(new IllegalStateException("合成失败"));
                assertThrows(CompletionException.class, result::join);
                assertEquals(List.of("primary"), h.routes);
            }
        }
    }

    /** @return 有效的非表情包结果 */
    private static Stickers.Analysis answer() {
        return new Stickers.Analysis(Stickers.Kind.OTHER, 1, false, "测试形状", "", "", List.of(), 1, "合成夹具");
    }

    /**
     * 拥有模型工作及实例的隔离夹具。
     * @author zExNocs
     * @date 2026/11/10
     * @since 4.9.1-l
     * @version 4.9.1-l
     */
    private static final class Harness implements AutoCloseable {
        final AgentStickerFileConfig file = new AgentStickerFileConfig();
        final AgentRegistry agents = new AgentRegistry();
        final List<String> routes = new ArrayList<>();
        final List<CompletableFuture<Stickers.Analysis>> pending = new ArrayList<>();
        StickerModelSelection.Plan plan = new StickerModelSelection.Plan("primary", Optional.of("backup"), "v1");
        boolean primaryImages = true;
        final Stickers.Source source = new Stickers.Source(new AgentScope("test", "group"), UUID.randomUUID(),
                "测试", "机器人", "消息", 0, Instant.now(), false, UUID.randomUUID(), "https://example.test/a.png");
        final IStickerAssetStore.Prepared prepared = new IStickerAssetStore.Prepared(
                new Stickers.Blob("0".repeat(64), "png", 1, 1, 1, 1),
                List.of("data:image/png;base64,aGVsbG8="), "测试图片");
        final LLMStickerAnalyzer analyzer;

        /** 只装配本地模型端口，不创建网络请求。 */
        Harness() {
            var files = mock(IFileConfigService.class);
            when(files.requireCurrent(AgentStickerFileConfig.class)).thenReturn(file);
            var components = mock(AgentComponentAccess.class);
            when(components.settings(any(AgentScope.class), eq("sticker")))
                    .thenReturn(Optional.of(new ObjectMapper().createObjectNode()));
            var selection = mock(StickerModelSelection.class);
            when(selection.plan(any(), anyString())).thenAnswer(ignored -> plan);
            var gate = mock(AgentAccessGate.class);
            when(gate.evaluate(any(), any(), any(), anyString())).thenAnswer(call -> {
                String route = call.getArgument(3);
                var capabilities = route.equals("primary") && !primaryImages ? ModelCapabilities.textOnly()
                        : ModelCapabilities.userImages(Set.of(LLMImageDetail.LOW));
                var runtime = new AgentRuntimeSnapshot(new LLMModelOptionsSnapshot(
                        LLMModelOptions.builder().provider(route).model("test").build()), capabilities,
                        LLMInteractionSettings.defaults());
                return new AgentAccessDecision(AgentAccessDecision.Reason.ALLOWED,
                        Optional.of(new AgentScopePolicy(true, true, AgentScopePolicy.Mode.ACTIVE)),
                        Optional.of(new AgentReadiness(AgentReadiness.Status.READY, Optional.of(runtime))));
            });
            var json = mock(LLMJsonService.class);
            when(json.generateSettled(eq(Stickers.Analysis.class), any(), any(), any(), any())).thenAnswer(call -> {
                ILLMPrompt prompt = call.getArgument(1);
                routes.add(prompt.getOptions().getProvider());
                var result = new CompletableFuture<Stickers.Analysis>();
                pending.add(result);
                return result;
            });
            var beans = new DefaultListableBeanFactory();
            beans.registerSingleton("factory", new AgentFactory(agents));
            analyzer = new LLMStickerAnalyzer(beans.getBeanProvider(IAgentFactory.class), gate, json, files,
                    components, new ObjectMapper(), selection, mock(ILogger.class), Runnable::run);
            analyzer.start().toCompletableFuture().join();
        }

        /** 先完成未结束的合成调用，再释放工作域及模型句柄。 */
        @Override
        public void close() {
            var stopped = analyzer.stop();
            List.copyOf(pending).forEach(value -> value.completeExceptionally(new IllegalStateException("夹具结束")));
            stopped.toCompletableFuture().join();
            agents.close();
        }
    }
}
