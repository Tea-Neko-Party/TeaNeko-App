package org.zexnocs.teanekoagent.llm.framework.structured;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.zexnocs.teanekoagent.llm.file_config.interfaces.ILLMFileConfigService;
import org.zexnocs.teanekoagent.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.interaction.ILLMInteractionService;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelService;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.IDefaultModelResolver;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModel;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.LLMModel;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.LLMResponseFormat;
import org.zexnocs.teanekoagent.llm.framework.response.LLMResult;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekoagent.llm.instance.deepseek.DeepSeekChatCompletionMapper;
import org.zexnocs.teanekoagent.llm.instance.deepseek.DeepSeekModelOptions;
import org.zexnocs.teanekoagent.llm.instance.kimi.KimiModelOptions;
import org.zexnocs.teanekoagent.llm.instance.openai.completions.OpenAIChatCompletionMapper;
import org.zexnocs.teanekoagent.llm.instance.openai.completions.OpenAIChatCompletionModelOptions;
import org.zexnocs.teanekoagent.llm.instance.openai.responses.OpenAIModelOptions;
import org.zexnocs.teanekoagent.llm.instance.openai.responses.OpenAIResponsesMapper;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.MemoryLayers;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证统一 JSON 合同实际写入供应商字段，能力不足时有告警且不发送不支持参数。
 * <br>2026/11/06：覆盖参数复制、路由覆盖及对象模式与 Schema 模式的区别。
 * <br>2026/11/06：按原生能力去除重复结构提示，保持输入预检、观察与实际发送一致。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 * <br>2026/11/10：原生提示不混用协议字段，核对 Gemini 路由中嵌套必需字段的完整 Responses 请求。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.3-l
 * @version 4.9.1-l
 */
class LLMJsonProtocolTest {
    private final ObjectMapper mapper = new ObjectMapper();

    /** 2026/11/10：当前 Gemini Responses 路由的联合记忆合同包含 context 等全部嵌套必需字段。 */
    @Test
    void responsesCarriesCompleteNestedMemoryContractForGemini() {
        var type = org.zexnocs.teanekoplugin.agent.cognition.personality.StyleObservations.Analysis.class;
        var schemas = LLMJsonTestSupport.schemas(type);
        var definition = schemas.require(type);
        var adapter = new org.zexnocs.teanekoagent.llm.instance.openai.responses.OpenAIResponsesModel(
                mock(org.zexnocs.teanekocore.api_response.interfaces.IAPIResponseService.class),
                mock(org.zexnocs.teanekoagent.llm.framework.model.interfaces.ICredentialResolver.class));
        assertEquals(LLMJsonSupport.SCHEMA, adapter.getJsonOutputSupport("gemini-3.8-flash-medium"));
        var options = LLMModelOptions.builder().provider("gemini").model("gemini-3.8-flash-medium")
                .maxTokens(163840).responseFormat(LLMResponseFormat.JSON).responseSchema(definition.schema()).build();
        var prompt = new LLMPrompt(java.util.List.of(), options);
        var request = mapper.valueToTree(OpenAIResponsesMapper.toRequest(prompt,
                adapter.getJsonOutputSupport(options.getModel()).adapt(options, true), "仅测试凭据"));
        var format = request.path("text").path("format");
        assertEquals("json_schema", format.path("type").stringValue());
        assertTrue(format.path("strict").booleanValue());
        assertFalse(format.has("json_schema"));
        assertEquals(163840, request.path("max_output_tokens").intValue());
        assertEquals(mapper.valueToTree(definition.schema().schema()), format.path("schema"));
        var item = format.path("schema").path("properties").path("styles").path("items");
        assertEquals(mapper.valueToTree(java.util.List.of("field", "pattern", "context", "quotes", "segments")),
                item.path("required"));
        assertFalse(item.path("additionalProperties").booleanValue());
        assertEquals(mapper.valueToTree(java.util.List.of("GENERAL", "TECHNICAL", "CASUAL")),
                item.path("properties").path("context").path("enum"));
        var failure = assertThrows(LLMJsonException.class, () -> new LLMJsonCodec(mapper).read(type, definition,
                "{\"memories\":[],\"styles\":[{\"field\":\"speakingStyle\",\"pattern\":\"简洁\"}]}",
                java.util.UUID.randomUUID()));
        assertTrue(failure.failureReason().contains("$.styles[0].context：缺少必需字段"));
    }

    /** 三类协议正确复制通用 Schema，但只在声明支持的适配器上发送严格 Schema。 */
    @Test void serializesNativeJsonAndSchemaWithoutLosingOptions() {
        var schema = LLMJsonSchema.fromType(MemoryLayers.Digest.class);
        var options = LLMModelOptions.builder().provider("route").model("离线").maxTokens(2048)
                .responseFormat(LLMResponseFormat.JSON).responseSchema(schema).build();
        assertEquals(schema, DeepSeekModelOptions.copyOf(options).findResponseSchema().orElseThrow());
        assertEquals(schema, KimiModelOptions.copyOf(options).findResponseSchema().orElseThrow());
        assertEquals(schema, OpenAIModelOptions.copyOf(options).findResponseSchema().orElseThrow());
        var prompt = new LLMPrompt(LLMUserMessage.builder().contents(new LLMContentListBuilder().addText("返回 JSON").build()).build(), options);
        var objectOptions = LLMJsonSupport.OBJECT.adapt(options, false);
        var deepseek = mapper.valueToTree(DeepSeekChatCompletionMapper.toRequest(prompt, objectOptions,
                "https://example.invalid", "/chat/completions", "测试凭据"));
        assertEquals("json_object", deepseek.path("response_format").path("type").stringValue());
        assertEquals(1, deepseek.path("response_format").size());
        var kimi = mapper.valueToTree(OpenAIChatCompletionMapper.toRequest(prompt, KimiModelOptions.copyOf(objectOptions), "测试凭据"));
        assertEquals("json_object", kimi.path("response_format").path("type").stringValue());
        var chat = mapper.valueToTree(OpenAIChatCompletionMapper.toRequest(prompt, OpenAIChatCompletionModelOptions.copyOf(options), "测试凭据"));
        assertEquals("json_schema", chat.path("response_format").path("type").stringValue());
        assertTrue(chat.path("response_format").path("json_schema").path("strict").booleanValue());
        assertEquals(mapper.valueToTree(schema.schema()), chat.path("response_format").path("json_schema").path("schema"));
        var responses = mapper.valueToTree(OpenAIResponsesMapper.toRequest(prompt, OpenAIModelOptions.copyOf(options), "测试凭据"));
        assertEquals("json_schema", responses.path("text").path("format").path("type").stringValue());
        assertTrue(responses.path("text").path("format").path("strict").booleanValue());
        assertEquals(mapper.valueToTree(schema.schema()), responses.path("text").path("format").path("schema"));
        assertEquals(schema, options.getResponseSchema());
    }

    /** 通用服务和显式模型重载不让默认 TEXT 覆盖结构合同，未知适配器实际降级只报告一次。 */
    @Test void routeOverridePreservesContractAndUnsupportedAdapterWarns() {
        var scanner = mock(IBeanScanner.class);
        var files = mock(ILLMFileConfigService.class);
        var annotation = mock(LLMModel.class);
        when(annotation.id()).thenReturn("route");
        var model = mock(ILLMModel.class, CALLS_REAL_METHODS);
        when(model.getProvider()).thenReturn("route");
        when(model.getModel()).thenReturn("默认模型");
        when(model.getBaseOptions()).thenReturn(LLMModelOptions.builder().provider("route").model("默认模型").maxTokens(2048).build());
        when(model.getJsonOutputSupport(any())).thenReturn(LLMJsonSupport.SCHEMA);
        when(files.getDefaultOptions(any(), any())).thenAnswer(call -> call.getArgument(1));
        when(scanner.getBeansWithAnnotationAndInterface(LLMModel.class, ILLMModel.class))
                .thenReturn(Map.of("模型", IndependentPair.of(annotation, model)));
        var transport = new TaskFuture<ILLMResult>(mock(ILogger.class), "离线 JSON 传输", CompletableFuture.completedFuture(LLMResult.builder().build()));
        when(model.call(any(ILLMPrompt.class))).thenReturn(transport);
        var service = new LLMModelService(scanner, files, mock(IDefaultModelResolver.class));
        service.init();
        var json = LLMJsonTestSupport.create(mock(ILLMInteractionService.class), mapper);
        var source = new LLMPrompt(LLMUserMessage.builder().contents(new LLMContentListBuilder().addText("测试资料").build()).build());
        var prepared = json.prepare(MemoryLayers.Digest.class, source);
        service.call("route", "本次模型", prepared.prompt());
        var captured = ArgumentCaptor.forClass(ILLMPrompt.class);
        verify(model).call(captured.capture());
        assertEquals("本次模型", Objects.requireNonNull(captured.getValue().getOptions()).getModel());
        assertEquals(LLMResponseFormat.JSON, captured.getValue().getOptions().getResponseFormat());
        assertEquals(prepared.definition().schema(), captured.getValue().getOptions().findResponseSchema().orElseThrow());
        // 2026/11/06：原生 Schema 只在协议中携带一次，系统消息不再重复完整结构。
        var nativeMessages = mapper.writeValueAsString(captured.getValue().getMessages());
        assertFalse(nativeMessages.contains("最多600字"));
        // 2026/11/10：具体承载字段由适配器决定，通用提示不误指向 Chat Completions。
        assertTrue(nativeMessages.contains("本次请求携带的 JSON Schema"));
        var nativeJson = new LLMJsonService(LLMJsonTestSupport.schemas(MemoryLayers.Digest.class),
                mock(ILLMInteractionService.class), mapper, new LLMJsonDiagnostics(false, "未启用"), service,
                mock(java.util.concurrent.ScheduledExecutorService.class));
        var nativeRequest = nativeJson.prepare(MemoryLayers.Digest.class,
                new LLMPrompt(source.getMessages(), LLMModelOptions.builder()
                        .provider("route").model("Schema 模式").maxTokens(2048).build()));
        assertFalse(mapper.writeValueAsString(nativeRequest.prompt().getMessages()).contains("最多600字"));
        assertTrue(Objects.requireNonNull(nativeRequest.prompt().getOptions()).findResponseSchema().isPresent());
        when(model.getJsonOutputSupport(any())).thenReturn(LLMJsonSupport.OBJECT);
        var objectPrompt = service.prepareRequest(new LLMPrompt(prepared.prompt().getMessages(),
                LLMModelOptions.builder().provider("route").model("对象模式").maxTokens(2048)
                        .responseFormat(LLMResponseFormat.JSON).responseSchema(prepared.definition().schema()).build()));
        assertTrue(mapper.writeValueAsString(objectPrompt.getMessages()).contains("最多600字"));
        assertTrue(Objects.requireNonNull(objectPrompt.getOptions()).findResponseSchema().isEmpty());

        when(model.getJsonOutputSupport(any())).thenReturn(LLMJsonSupport.NONE);
        var logger = assertInstanceOf(Logger.class, LoggerFactory.getLogger(LLMJsonSupport.class));
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.call("route", "不支持原生 JSON", prepared.prompt());
            verify(model, times(2)).call(captured.capture());
            assertEquals(LLMResponseFormat.TEXT, captured.getValue().getOptions().getResponseFormat());
            assertTrue(captured.getValue().getOptions().findResponseSchema().isEmpty());
            assertTrue(mapper.writeValueAsString(captured.getValue().getMessages()).contains("JSON Schema"));
            assertEquals(1, appender.list.size());
            assertTrue(appender.list.getFirst().getFormattedMessage().contains("未声明原生 JSON"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
