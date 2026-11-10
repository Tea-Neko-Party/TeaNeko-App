package org.zexnocs.teanekoagent.llm.integration.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.interaction.InteractionRuntime;
import org.zexnocs.teanekoagent.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.ImageLLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.ModelCapabilities;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModel;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekoagent.llm.framework.response.LLMChoice;
import org.zexnocs.teanekoagent.llm.framework.response.LLMResult;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.logger.ILogger;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * 内置图检测的真实结果判断及资源排空回归，不访问远端模型。
 * <br>2026/11/10：覆盖图片发送、拒绝误判、过期资格及并发槽真实释放。
 * @author zExNocs
 * @date 2026/11/10
 * @since 4.9.1-l
 * @version 4.9.1-l
 */
class ModelVisionProbeTest {
    /**
     * @param text 模型输出
     * @param finish 终止原因
     * @param status 期望状态
     */
    @ParameterizedTest
    @CsvSource({"'RED_SQUARE,BLUE_CIRCLE',stop,SUCCESS", "OK,stop,FAILED",
            "'BLUE_SQUARE,RED_CIRCLE',stop,FAILED", "'RED_SQUARE,BLUE_CIRCLE',length,FAILED",
            "拒绝,content_filter,FAILED"})
    void requiresActualPictureRecognition(String text, String finish, String status) {
        assertEquals(status, ModelVisionProbe.inspect(reply(text, finish)).status());
    }

    /** @throws Exception 当异步检测未按时完成时 */
    @Test
    void holdsSlotsUntilActualCompletionAndRejectsChangedAuthority() throws Exception {
        var models = mock(ILLMModelService.class);
        var model = mock(ILLMModel.class);
        var options = LLMModelOptions.builder().provider("test").model("custom").tools(List.of()).build();
        when(models.resolveOptions(any())).thenAnswer(ignored -> LLMModelOptions.copyOf(options));
        when(models.getModel("test")).thenReturn(model);
        when(model.inputCapabilities(any())).thenAnswer(call -> {
            LLMModelOptions value = call.getArgument(0);
            return org.zexnocs.teanekoagent.llm.instance.openai.OpenAIVisionCapabilities.resolve(value,
                    ModelCapabilities.textOnly());
        });
        var logical = new CompletableFuture<ILLMResult>();
        var physical = new CompletableFuture<Void>();
        var task = spy(new TaskFuture<ILLMResult>(mock(ILogger.class), "识图离线", logical));
        when(task.physicalCompletion()).thenReturn(physical);
        when(models.call(any(ILLMPrompt.class), any(org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy.class)))
                .thenAnswer(call -> {
            ILLMPrompt prompt = call.getArgument(0);
            assertTrue(prompt.getOptions().getTools().isEmpty());
            assertEquals(Boolean.TRUE, prompt.getOptions().findImageInput().orElseThrow());
            var image = prompt.getMessages().getFirst().getContents().stream()
                    .map(value -> value.getContentPart()).filter(ImageLLMContentPart.class::isInstance)
                    .findFirst().orElseThrow();
            assertTrue(image instanceof ImageLLMContentPart);
            var data = java.util.Base64.getDecoder().decode(((ImageLLMContentPart) image).getUrl().split(",", 2)[1]);
            try (var bytes = new java.io.ByteArrayInputStream(data)) {
                var decoded = javax.imageio.ImageIO.read(bytes);
                assertEquals(96, decoded.getWidth());
                assertEquals(64, decoded.getHeight());
            }
            return task;
        });
        var runtime = new InteractionRuntime();
        var service = new ModelVisionProbe(models, runtime.scheduler, Runnable::run);
        var valid = new AtomicBoolean(true);
        Runnable guard = () -> {
            if (!valid.get()) {
                throw new IllegalStateException("检测资格失效。");
            }
        };
        var first = service.probe("test", guard).toCompletableFuture();
        var second = service.probe("test", guard).toCompletableFuture();
        assertThrows(CompletionException.class, () -> service.probe("test", guard).toCompletableFuture().join());
        logical.complete(reply("RED_SQUARE,BLUE_CIRCLE", "stop"));
        assertFalse(first.isDone());
        assertFalse(second.isDone());
        valid.set(false);
        physical.complete(null);
        assertThrows(CompletionException.class, first::join);
        assertThrows(CompletionException.class, second::join);
        valid.set(true);
        var result = service.probe("test", guard).toCompletableFuture().get(1, TimeUnit.SECONDS);
        assertEquals("SUCCESS", result.status());
        assertTrue(result.reason().contains("尚未声明"));
        assertTrue(options.findImageInput().isEmpty());
        assertEquals(0, runtime.pendingTimers());
    }

    /**
     * @param text 固定输出
     * @param finish 实际原因
     * @return 测试结果
     */
    private static ILLMResult reply(String text, String finish) {
        return LLMResult.builder().choices(List.of(LLMChoice.builder().finishReason(finish)
                .message(LLMAssistantMessage.builder().contents(new LLMContentListBuilder().addText(text).build())
                        .build()).build())).build();
    }
}
