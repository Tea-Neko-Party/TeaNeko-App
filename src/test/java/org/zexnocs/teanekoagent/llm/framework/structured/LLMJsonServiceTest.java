package org.zexnocs.teanekoagent.llm.framework.structured;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.zexnocs.teanekoagent.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.interaction.ILLMInteractionGuard;
import org.zexnocs.teanekoagent.llm.framework.interaction.ILLMInteractionService;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionSettings;
import org.zexnocs.teanekoagent.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.LLMResponseFormat;
import org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationContext;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.framework.composition.ComponentPatch;
import org.zexnocs.teanekocore.framework.composition.EntityId;
import org.zexnocs.teanekocore.framework.composition.EntitySnapshot;
import org.zexnocs.teanekocore.logger.FailureDiagnostic;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IClassScanner;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.MemoryLayers;
import org.zexnocs.teanekoplugin.agent.cognition.personality.PersonalityLearningOutput;
import org.zexnocs.teanekoplugin.agent.expression.expression.LegacyReplyOutput;
import org.zexnocs.teanekoplugin.agent.expression.sticker.Stickers;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 结构化输出合同、异步返回和安全诊断的离线回归。
 * <br>2026/11/06：覆盖缓存发布、实际提示、严格绑定、撤销及受限原文归档。
 * <br>2026/11/06：按原生能力去除重复结构提示，保持输入预检、观察与实际发送一致。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * <br>2026/11/10：核对原生结构提示保留字段名，仍拒绝模型返回的错误别名。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.3-l
 * @version 4.9.1-l
 */
class LLMJsonServiceTest {
    /** 2026/11/10：原生模式仍提示真实顶层键；错误别名不能变成空成功或推进记忆水位。 */
    @Test
    void nativeSchemaKeepsRootNamesAndRejectsInventedAliases() {
        var type = org.zexnocs.teanekoplugin.agent.cognition.personality.StyleObservations.Analysis.class;
        var schemas = LLMJsonTestSupport.schemas(type);
        var service = new LLMJsonService(schemas, mock(ILLMInteractionService.class), mapper,
                new LLMJsonDiagnostics(false, "未启用"),
                mock(org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelService.class),
                // 2026/11/10：此用例仅检查提示，不启动截止任务。
                mock(java.util.concurrent.ScheduledExecutorService.class));
        var definition = schemas.require(type);
        var options = LLMModelOptions.builder().responseFormat(LLMResponseFormat.JSON)
                .responseSchema(definition.schema()).build();
        var instruction = LLMJsonSupport.SCHEMA.messages(List.of(service.instruction(definition)), options).getFirst();
        var text = assertInstanceOf(TextLLMContentPart.class,
                instruction.getContents().getFirst().getContentPart()).getText();
        assertTrue(text.contains("memories"));
        assertTrue(text.contains("styles"));
        assertFalse(text.contains("subjectId"));
        assertFalse(text.contains("facts"));
        assertTrue(text.length() < 500);
        var invalid = assertThrows(LLMJsonException.class,
                () -> service.read(type, "{\"facts\":[],\"styles\":[]}", UUID.randomUUID()));
        assertEquals(LLMJsonException.Code.JSON_SHAPE, invalid.code());
        var empty = service.read(type, "{\"memories\":[],\"styles\":[]}", UUID.randomUUID());
        assertTrue(empty.memories().isEmpty());
        assertTrue(empty.styles().isEmpty());
    }

    /** 2026/11/06：标准约束进入协议且在 Java 解码前执行，不能仅靠自然语言说明。 */
    @Test
    void enforcesDeclaredLengthsCountsAndStringEnums() {
        var schemas = LLMJsonTestSupport.schemas(LegacyReplyOutput.class,
                org.zexnocs.teanekoplugin.agent.cognition.personality.StyleObservations.Analysis.class);
        var legacy = schemas.require(LegacyReplyOutput.class);
        var tree = mapper.valueToTree(legacy.schema().schema()).path("properties").path("messages");
        assertEquals(32, tree.path("maxItems").intValue());
        assertEquals(2000, tree.path("items").path("properties").path("text").path("maxLength").intValue());
        assertEquals(3, tree.path("items").path("properties").path("mode").path("enum").size());
        var codec = new LLMJsonCodec(mapper);
        for (var text : List.of(
                mapper.writeValueAsString(Map.of("teaneko_reply", 1, "messages",
                        java.util.Collections.nCopies(33, Map.of("mode", "plain", "text", "测试")))),
                mapper.writeValueAsString(Map.of("teaneko_reply", 1, "messages",
                        List.of(Map.of("mode", "plain", "text", "字".repeat(2001))))),
                mapper.writeValueAsString(Map.of("teaneko_reply", 1, "messages",
                        List.of(Map.of("mode", "private-value", "text", "测试")))))) {
            var failure = assertThrows(LLMJsonException.class,
                    () -> codec.read(LegacyReplyOutput.class, legacy, text, UUID.randomUUID()));
            assertEquals(LLMJsonException.Code.JSON_SHAPE, failure.code());
            assertFalse(failure.getMessage().contains("private-value"));
        }
        var analysis = mapper.valueToTree(schemas.require(
                org.zexnocs.teanekoplugin.agent.cognition.personality.StyleObservations.Analysis.class).schema().schema());
        assertEquals(4, analysis.path("properties").path("memories").path("maxItems").intValue());
        assertEquals(2, analysis.path("properties").path("styles").path("maxItems").intValue());
    }
    @TempDir Path directory;
    private final ObjectMapper mapper = new ObjectMapper();

    /** 类级说明、嵌套、枚举及可空字段来自生产合同，显式 JSON 别名不丢失。 */
    @Test void derivesCompleteDescriptionAndNullableShape() {
        var schemas = LLMJsonTestSupport.schemas(Stickers.Analysis.class, PersonalityLearningOutput.class, LegacyReplyOutput.class);
        var sticker = mapper.valueToTree(schemas.require(Stickers.Analysis.class).schema().schema());
        assertTrue(sticker.path("description").stringValue().contains("插画"));
        assertTrue(sticker.path("properties").path("kind").path("enum").toString().contains("STICKER"));
        assertTrue(sticker.path("properties").path("visualDescription").path("description").stringValue().contains("160"));
        assertEquals(9, sticker.path("required").size());
        assertFalse(sticker.path("additionalProperties").booleanValue());
        var personality = schemas.require(PersonalityLearningOutput.class);
        var shape = mapper.valueToTree(personality.schema().schema()).path("properties").path("adjustment");
        assertEquals("null", shape.path("anyOf").path(1).path("type").stringValue());
        assertNull(new LLMJsonCodec(mapper).read(PersonalityLearningOutput.class, personality,
                "{\"adjustment\":null}", UUID.randomUUID()).adjustment());
        var legacy = schemas.require(LegacyReplyOutput.class);
        assertTrue(mapper.valueToTree(legacy.schema().schema()).path("properties").has("teaneko_reply"));
        assertEquals(1, new LLMJsonCodec(mapper).read(LegacyReplyOutput.class, legacy,
                "{\"teaneko_reply\":1,\"messages\":[]}", UUID.randomUUID()).version());
    }

    /** 请求只读目录，重扫失败保留原合同且未注册类型不能触发懒发现。 */
    @Test void scanningOccursOnlyAtInitializationAndExplicitReload() {
        var scanner = mock(IClassScanner.class);
        when(scanner.getClassesWithAnnotation(LLMJsonType.class)).thenReturn(Map.of(
                MemoryLayers.Digest.class, MemoryLayers.Digest.class.getAnnotation(LLMJsonType.class)));
        var schemas = new LLMJsonSchemas(scanner);
        assertThrows(IllegalStateException.class, () -> schemas.require(MemoryLayers.Digest.class));
        verifyNoInteractions(scanner);
        schemas.init();
        var original = schemas.require(MemoryLayers.Digest.class);
        assertSame(original, schemas.require(MemoryLayers.Digest.class));
        assertThrows(IllegalStateException.class, () -> schemas.require(String.class));
        verify(scanner, times(1)).getClassesWithAnnotation(LLMJsonType.class);
        when(scanner.getClassesWithAnnotation(LLMJsonType.class)).thenThrow(new IllegalStateException("离线发现失败"));
        assertThrows(IllegalStateException.class, schemas::reload);
        assertSame(original, schemas.require(MemoryLayers.Digest.class));
        assertThrows(IllegalArgumentException.class, () -> LLMJsonSchema.fromType(Map.class));
        assertThrows(IllegalArgumentException.class, () -> LLMJsonSchema.fromType(List.class));
    }

    /**
     * 语法、类型、字段与领域失败不截断修复，不将供应商输出值或原 Throwable 带入日志。
     * @param text 无效模型输出
     */
    @ParameterizedTest
    @ValueSource(strings = {"```json\n{\"brief\":\"private-secret\",\"details\":\"x\"}\n```",
            "{\"brief\":\"private-secret\",", "{\"brief\":\"private-secret\",\"details\":\"x\"} trailing-private-secret",
            "{\"brief\":\"private-secret\",\"brief\":\"x\",\"details\":\"x\"}",
            "{\"brief\":\"private-secret\"}", "{\"brief\":12,\"details\":\"private-secret\"}",
            "{\"brief\":\"private-secret\",\"details\":null}", "[]", "null", "",
            "{\"brief\":\"private-secret\",\"details\":\"x\",\"private-secret\":true}",
            "{\"brief\":\"\",\"details\":\"private-secret\"}"})
    void rejectsInvalidJsonWithSafeLocation(String text) {
        var definition = LLMJsonTestSupport.schemas(MemoryLayers.Digest.class).require(MemoryLayers.Digest.class);
        var failure = assertThrows(LLMJsonException.class,
                () -> new LLMJsonCodec(mapper).read(MemoryLayers.Digest.class, definition, text, UUID.randomUUID()));
        assertEquals(LLMInteractionResult.Status.SUCCESS, failure.status());
        assertNull(failure.getCause());
        var report = FailureDiagnostic.from(failure).toString();
        assertTrue(report.contains("原输出结构"));
        assertFalse(report.contains("private-secret"));
        if (failure.code() == LLMJsonException.Code.JSON_SYNTAX) { assertTrue(report.contains("行=")); }
    }

    /**
     * 生成使用完整合同和原控制，观察者在严格绑定前保留真实用量；取消不交付迟到结果。
     * @throws Exception 当异步生成未在有界时间内完成时
     */
    @Test void generatesTypeThroughOriginalInteractionAndCancellation() throws Exception {
        var interactions = mock(ILLMInteractionService.class);
        var service = LLMJsonTestSupport.create(interactions, mapper);
        var original = LLMModelOptions.builder().provider("test").model("离线").maxTokens(2048).build();
        var input = new LLMPrompt(LLMUserMessage.builder().contents(new LLMContentListBuilder().addText("摘要资料").build()).build(), original);
        var prepared = service.prepare(MemoryLayers.Digest.class, input);
        assertEquals(LLMResponseFormat.TEXT, original.getResponseFormat());
        assertEquals(LLMResponseFormat.JSON, Objects.requireNonNull(prepared.prompt().getOptions()).getResponseFormat());
        assertEquals(2048, prepared.prompt().getOptions().getMaxTokens());
        assertTrue(mapper.writeValueAsString(prepared.prompt().getMessages()).contains("最多600字"));
        var pending = new CompletableFuture<LLMInteractionResult>();
        when(interactions.interact(any(), any(), any(), any(), any())).thenReturn(pending);
        var observed = new AtomicReference<LLMInteractionResult>();
        try (var control = ExecutionControl.create()) {
            var context = new ToolInvocationContext(control.id(), EntitySnapshot.of(EntityId.random(), ComponentPatch.empty()), control);
            var guard = ILLMInteractionGuard.asynchronous(call -> CompletableFuture.completedStage(null));
            var output = service.generate(prepared, context, LLMInteractionSettings.defaults(), guard, observed::set);
            var result = result(LLMInteractionResult.Status.SUCCESS, "{\"brief\":\"摘要\",\"details\":\"完整摘要\"}");
            pending.complete(result);
            assertEquals("摘要", output.toCompletableFuture().get(2, TimeUnit.SECONDS).brief());
            assertSame(result, observed.get());
            verify(interactions).interact(eq(prepared.prompt()), any(), same(context), any(), same(guard));
        }
        try (var control = ExecutionControl.create()) {
            var late = new CompletableFuture<LLMInteractionResult>();
            when(interactions.interact(any(), any(), any(), any(), any())).thenReturn(late);
            var context = new ToolInvocationContext(control.id(), EntitySnapshot.of(EntityId.random(), ComponentPatch.empty()), control);
            var work = service.generate(MemoryLayers.Digest.class, input, context, LLMInteractionSettings.defaults(),
                    ILLMInteractionGuard.asynchronous(call -> CompletableFuture.completedStage(null)));
            control.cancel();
            late.complete(result(LLMInteractionResult.Status.SUCCESS, "{\"brief\":\"迟到\",\"details\":\"不能提交\"}"));
            assertThrows(CompletionException.class, () -> work.toCompletableFuture().join());
        }
    }

    /** 终止失败按真实状态返回，不把截断、拒答或缺失答案解析成成功对象。 */
    @Test void preservesNonSuccessAndMissingCandidate() {
        var interactions = mock(ILLMInteractionService.class);
        var service = LLMJsonTestSupport.create(interactions, mapper);
        var input = new LLMPrompt(List.of());
        try (var control = ExecutionControl.create()) {
            var context = new ToolInvocationContext(control.id(), EntitySnapshot.of(EntityId.random(), ComponentPatch.empty()), control);
            for (var status : List.of(LLMInteractionResult.Status.TRUNCATED, LLMInteractionResult.Status.REFUSED,
                    LLMInteractionResult.Status.EMPTY_OUTPUT, LLMInteractionResult.Status.SUCCESS)) {
                var result = new LLMInteractionResult(status, List.of(), null, List.of(), List.of(), Map.of());
                when(interactions.interact(any(), any(), any(), any(), any())).thenReturn(CompletableFuture.completedStage(result));
                var failure = assertThrows(CompletionException.class, () -> service.generate(MemoryLayers.Digest.class, input,
                        context, LLMInteractionSettings.defaults(), ILLMInteractionGuard.asynchronous(
                                call -> CompletableFuture.completedStage(null))).toCompletableFuture().join());
                var cause = assertInstanceOf(LLMJsonException.class, failure.getCause());
                assertEquals(status, cause.status());
            }
        }
    }

    /**
     * 原文归档默认关闭；启用后容量和数量有界，只清理自己的过期文件。
     * @throws Exception 当临时目录无法读写时
     */
    @Test void archivesOnlyWhenEnabledWithRetentionAndOwnerPermissions() throws Exception {
        var disabled = directory.resolve("disabled");
        new LLMJsonDiagnostics(false, disabled.toString()).record(UUID.randomUUID(), "private-secret");
        assertFalse(Files.exists(disabled));
        var root = directory.resolve("enabled");
        var diagnostics = new LLMJsonDiagnostics(true, root.toString());
        var id = UUID.randomUUID();
        diagnostics.record(id, "private-secret");
        Path first;
        try (var files = Files.list(root)) { first = files.findFirst().orElseThrow(); }
        assertEquals("private-secret", Files.readString(first));
        var posix = Files.getFileAttributeView(first, java.nio.file.attribute.PosixFileAttributeView.class);
        if (posix != null) {
            assertEquals(java.util.Set.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE), posix.readAttributes().permissions());
        } else {
            var acl = Files.getFileAttributeView(first, java.nio.file.attribute.AclFileAttributeView.class);
            assertNotNull(acl);
            assertTrue(acl.getAcl().stream().allMatch(entry -> entry.principal().equals(aclOwner(first))));
        }
        Files.setLastModifiedTime(first, java.nio.file.attribute.FileTime.from(Instant.now().minusSeconds(90000)));
        var unrelated = Files.writeString(root.resolve("operator-note.txt"), "保留的运维记录");
        for (int index = 0; index < 22; index++) { diagnostics.record(id, "x"); }
        assertFalse(Files.exists(first));
        assertTrue(Files.exists(unrelated));
        try (var files = Files.list(root)) { assertEquals(20, files.filter(path -> !path.equals(unrelated)).count()); }
        diagnostics.record(UUID.randomUUID(), "x".repeat(270000));
        try (var files = Files.list(root)) {
            for (var path : files.toList()) { assertTrue(Files.size(path) < 1_048_576); }
        }
    }

    /**
     * @param path 已存在的测试文件
     * @return 实际文件所有者
     */
    private static java.nio.file.attribute.UserPrincipal aclOwner(Path path) {
        try { return Files.getOwner(path); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    /**
     * @param status 模型状态
     * @param text 最终正文
     * @return 不访问网络的真实结果容器
     */
    private static LLMInteractionResult result(LLMInteractionResult.Status status, String text) {
        var message = LLMAssistantMessage.builder().contents(new LLMContentListBuilder().addText(text).build()).build();
        return new LLMInteractionResult(status, List.of(message), message, List.of(), List.of(), Map.of());
    }
}
