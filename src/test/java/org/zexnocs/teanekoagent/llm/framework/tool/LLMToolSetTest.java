package org.zexnocs.teanekoagent.llm.framework.tool;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.zexnocs.teanekoagent.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.IContextualToolExecutor;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMTool;
import org.zexnocs.teanekoagent.llm.framework.tool.parameter.*;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ResourceBudget;
import org.zexnocs.teanekocore.framework.composition.ComponentKey;
import org.zexnocs.teanekocore.framework.composition.ComponentPatch;
import org.zexnocs.teanekocore.framework.composition.EntityId;
import org.zexnocs.teanekocore.framework.composition.EntitySnapshot;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationResult.Status.*;

/**
 * 验证固定工具集的可见范围、参数校验、异步完成及取消回填契约。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class LLMToolSetTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private static final ComponentKey<Actor> ACTOR = ComponentKey.of("tool-test.actor", Actor.class);
    /**
     * 只由宿主创建的不可变测试身份，供工具上下文注入断言使用。
     *
     * @param user 原操作者标识
     * @param namespace 原存储协议使用的命名空间
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record Actor(String user, String namespace) {}

    /**
     * 用不可变测试身份组件建立宿主上下文，不从模型参数生成身份。
     *
     * @param control 借用的取消、截止时间和预算控制
     * @return 满足本方法约束的 {@code ToolInvocationContext} 结果
     */
    static ToolInvocationContext context(ExecutionControl control) {
        var snapshot = EntitySnapshot.of(EntityId.random(), ComponentPatch.builder()
                .put(ACTOR, new Actor("trusted-user", "trusted-space")).build());
        return new ToolInvocationContext(UUID.randomUUID(), snapshot, control);
    }

    /**
     * 构造带固定调用 ID 的测试工具请求，便于核对迟到结果的配对稳定性。
     *
     * @param name 稳定名称
     * @param arguments 模型提供的业务参数 JSON
     * @return 满足本方法约束的 {@code LLMToolCall} 结果
     */
    static LLMToolCall call(String name, String arguments) {
        return new LLMToolCall("call-1", "function", name, arguments);
    }

    /**
     * 构造要求文本参数的测试定义，供绑定、schema 和白名单用例共用。
     *
     * @param name 稳定名称
     * @return 满足本方法约束的 {@code LLMTool} 结果
     */
    private LLMTool tool(String name) {
        return LLMTool.builder().name(name).description("Test tool")
                .parameters(new LLMObjectFunctionParameter("Arguments", Map.of(
                        "text", new LLMStringFunctionParameter("Text")), List.of("text")))
                .build();
    }

    /**
     * 将受控执行器绑定到唯一可见测试工具，避免依赖全局注册表。
     *
     * @param executor 本地实际执行器
     * @return 满足本方法约束的 {@code LLMToolSet} 结果
     */
    private LLMToolSet selected(IContextualToolExecutor executor) {
        return new LLMToolSet(mapper, List.of(new LLMToolBinding(tool("echo"), executor)));
    }

    /**
     * 验证工具等待实际业务完成，回填消息保留原调用 ID。
     */
    @Test
    void waitsForActualBusinessCompletionAndReturnsPairedMessage() {
        var pending = new CompletableFuture<String>();
        var received = new AtomicReference<ToolInvocationContext>();
        var set = selected((call, context) -> {
            received.set(context);
            return pending.minimalCompletionStage();
        });
        try (var control = ExecutionControl.create()) {
            var context = context(control);
            var request = call("echo", "{\"text\":\"hello\"}");
            var result = set.execute(request, context).toCompletableFuture();
            assertFalse(result.isDone());
            assertSame(context, received.get());
            assertEquals("trusted-space", received.get().bindings().require(ACTOR).namespace());
            // 调用开始后原始 DTO 的修改不能破坏迟到结果的 ID/参数配对。
            request.setId("changed");
            request.setArguments("{}");
            pending.complete("committed");
            var output = result.join();
            assertEquals(SUCCESS, output.status());
            assertFalse(output.truncated());
            assertEquals("call-1", output.call().getId());
            assertEquals("{\"text\":\"hello\"}", output.call().getArguments());
            var message = output.toMessage();
            assertEquals("call-1", message.getToolCallId());
            assertEquals("echo", message.getName());
            assertEquals("committed", ((TextLLMContentPart) message.getContents().getFirst().getContentPart()).getText());
            control.checkActive();
        }
    }

    /**
     * 验证Bean 后续改名、改 schema 或更换执行器不影响已冻结绑定。
     *
     */
    @Test
    void freezesDefinitionsAndSynchronousExecutorAcrossBeanMutation() {
        var schema = new LLMObjectFunctionParameter("schema", new LinkedHashMap<>(Map.of(
                "text", new LLMStringFunctionParameter("original"))), new ArrayList<>(List.of("text")));
        var original = tool("echo");
        original.setParameters(schema);
        original.setExecutor(arguments -> "original");
        var selected = LLMToolBinding.from(original);
        var set = new LLMToolSet(mapper, List.of(selected));
        original.setName("renamed");
        original.setExecutor(arguments -> "replacement");
        ((AbstractLLMFunctionParameter) schema.getProperties().get("text")).setType("integer");
        schema.getProperties().clear();
        schema.getRequired().clear();
        try (var control = ExecutionControl.create()) {
            var result = set.execute(call("echo", "{\"text\":\"valid\"}"), context(control)).toCompletableFuture().join();
            assertEquals("original", result.output());
            assertEquals(SUCCESS, result.status());
        }
        assertEquals("string", selected.definition().getParameters().getProperties().get("text").getType());
        assertThrows(UnsupportedOperationException.class, () -> set.definitions().clear());
        assertThrows(UnsupportedOperationException.class, () -> selected.definition().getParameters().getProperties().clear());
        assertThrows(UnsupportedOperationException.class, selected.definition()::call);
        var json = mapper.writeValueAsString(set.definitions());
        assertFalse(json.contains("executor"));
        assertFalse(json.contains("toolPackage"));
    }

    /**
     * 验证本轮可见集合就是执行白名单，并保留工具选择顺序。
     */
    @Test
    void visibleSetIsTheOnlyExecutionAllowlistAndRetainsSelectionOrder() {
        var executions = new AtomicInteger();
        var echo = new LLMToolBinding(tool("echo"), (_, _) -> {
            executions.incrementAndGet();
            return CompletableFuture.completedFuture("done");
        });
        var other = new LLMToolBinding(tool("other"), (_, _) -> CompletableFuture.completedFuture("other"));
        var set = new LLMToolSet(mapper, List.of(other, echo));
        assertEquals(List.of("other", "echo"), set.definitions().stream().map(ILLMTool::getName).toList());
        try (var control = ExecutionControl.create()) {
            var result = set.execute(call("global-hidden-tool", "{}"), context(control)).toCompletableFuture().join();
            assertEquals(NOT_ALLOWED, result.status());
            assertEquals(0, executions.get());
            assertEquals("NOT_ALLOWED", mapper.readTree(result.output()).path("error").path("code").asString());
        }
        assertThrows(IllegalArgumentException.class, () -> new LLMToolSet(mapper, List.of(echo, echo)));
    }

    /**
     * 验证非法 JSON、缺失、错型、未知及重复参数在执行前被拒绝。
     *
     * @param arguments 模型提供的业务参数 JSON
     */
    @ParameterizedTest
    @ValueSource(strings = {"{", "null", "[]", "1", "{}", "{\"text\":null}", "{\"text\":5}",
            "{\"text\":\"hi\",\"context\":{\"user\":\"forged\"}}", "{\"text\":\"hi\",\"namespace\":\"other\"}",
            "{\"text\":\"first\",\"text\":\"duplicate\"}", "{\"text\":\"hi\"} {}"})
    void rejectsMalformedMissingWrongTypeUnknownAndDuplicateArgumentsBeforeInvocation(String arguments) {
        var executions = new AtomicInteger();
        var set = selected((call, context) -> {
            executions.incrementAndGet();
            return CompletableFuture.completedFuture("done");
        });
        try (var control = ExecutionControl.create()) {
            var result = set.execute(call("echo", arguments), context(control)).toCompletableFuture().join();
            assertEquals(INVALID_ARGUMENTS, result.status());
            assertEquals(0, executions.get());
            assertFalse(result.output().contains("forged"));
        }
    }

    /**
     * 验证嵌套数组、整数、枚举及额外属性策略按 schema 验证。
     */
    @Test
    void validatesNestedArraysNumbersEnumsAndExplicitAdditionalProperties() {
        var item = new LLMObjectFunctionParameter("item", Map.of(
                "count", new LLMIntegerFunctionParameter("count"),
                "kind", new LLMStringFunctionParameter("kind", List.of("known")),
                "flag", new LLMBooleanFunctionParameter("flag")), List.of("count", "kind", "flag"));
        var definition = tool("batch");
        definition.setParameters(new LLMObjectFunctionParameter("root", Map.of(
                "items", new LLMArrayFunctionParameter("items", item)), List.of("items"), true));
        var executions = new AtomicInteger();
        var set = new LLMToolSet(mapper, List.of(new LLMToolBinding(definition, (call, context) -> {
            executions.incrementAndGet();
            return CompletableFuture.completedFuture("done");
        })));
        try (var control = ExecutionControl.create()) {
            var context = context(control);
            var valid = "{\"items\":[{\"count\":1,\"kind\":\"known\",\"flag\":false}],\"extra\":1}";
            assertEquals(SUCCESS, set.execute(call("batch", valid), context).toCompletableFuture().join().status());
            for (var invalid : List.of(valid.replace(":1,", ":1.5,"), valid.replace("known", "unknown"),
                    valid.replace(":false", ":\"false\""), valid.replace("\"items\":[", "\"items\":null,\"bad\":["))) {
                assertEquals(INVALID_ARGUMENTS, set.execute(call("batch", invalid), context).toCompletableFuture().join().status());
            }
            assertEquals(1, executions.get());
        }
    }

    /**
     * 验证结果按上限裁剪且不拆分 Unicode 代理对。
     */
    @Test
    void truncatesSuccessfulOutputWithoutSplittingUnicodePair() {
        var set = new LLMToolSet(mapper, List.of(new LLMToolBinding(tool("echo"),
                (call, context) -> CompletableFuture.completedFuture("a".repeat(127) + "😀tail"))), 128);
        try (var control = ExecutionControl.create()) {
            var result = set.execute(call("echo", "{\"text\":\"hi\"}"), context(control)).toCompletableFuture().join();
            assertEquals(SUCCESS, result.status());
            assertTrue(result.truncated());
            assertEquals("a".repeat(127), result.output());
        }
        assertThrows(IllegalArgumentException.class, () -> new LLMToolSet(mapper, List.of(), 127));
    }

    /**
     * 验证同步与异步异常、空结果仅回填脱敏状态。
     */
    @Test
    void sanitizesSynchronousAndAsynchronousFailuresAndNullResults() {
        var secret = "private-api-key-do-not-echo";
        List<IContextualToolExecutor> failures = List.of(
                (call, context) -> { throw new IllegalStateException(secret); },
                (call, context) -> CompletableFuture.failedFuture(new IllegalArgumentException(secret)),
                (call, context) -> null,
                (call, context) -> CompletableFuture.completedFuture(null));
        try (var control = ExecutionControl.create()) {
            for (var executor : failures) {
                var result = selected(executor).execute(call("echo", "{\"text\":\"hi\"}"), context(control))
                        .toCompletableFuture().join();
                assertEquals(FAILED, result.status());
                assertFalse(result.output().contains(secret));
                assertEquals("FAILED", mapper.readTree(result.output()).path("error").path("code").asString());
            }
        }
    }

    /**
     * 验证取消结束等待并通知协作资源，迟到完成不能覆盖回执。
     */
    @Test
    void cancellationEndsWaitingNotifiesCooperativeResourceAndIgnoresLateCompletion() {
        var pending = new CompletableFuture<String>();
        var releases = new AtomicInteger();
        var set = selected((call, context) -> {
            var registration = context.control().onTermination(ignored -> releases.incrementAndGet());
            pending.whenComplete((result, failure) -> registration.close());
            return pending;
        });
        try (var control = ExecutionControl.create()) {
            var result = set.execute(call("echo", "{\"text\":\"hi\"}"), context(control)).toCompletableFuture();
            assertFalse(result.isDone());
            control.cancel();
            assertEquals(CANCELLED, result.join().status());
            assertEquals(1, releases.get());
            pending.complete("late");
            assertEquals(CANCELLED, result.join().status());
            assertFalse(result.join().output().contains("late"));
        }
    }

    /**
     * 验证已停止上下文不会启动工具，工具超时仍能独立识别。
     */
    @Test
    void stoppedContextDoesNotInvokeAndToolTimeoutRemainsDistinct() {
        var executions = new AtomicInteger();
        var set = selected((call, context) -> {
            executions.incrementAndGet();
            return CompletableFuture.failedFuture(new TimeoutException("sensitive provider detail"));
        });
        try (var control = ExecutionControl.create()) {
            var context = context(control);
            assertEquals(TIMED_OUT, set.execute(call("echo", "{\"text\":\"hi\"}"), context).toCompletableFuture().join().status());
            control.cancel();
            assertEquals(CANCELLED, set.execute(call("echo", "{\"text\":\"hi\"}"), context).toCompletableFuture().join().status());
            assertEquals(1, executions.get());
        }
    }

    /**
     * 验证截止时间能结束永不完成的工具等待并解除计时器。
     *
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     */
    @Test
    void deadlineEndsNeverCompletingToolAndReleasesTimerWithoutWaitingForBusiness() throws Exception {
        var scheduler = mock(ScheduledExecutorService.class);
        var scheduled = mock(ScheduledFuture.class);
        var timer = new AtomicReference<Callable<?>>();
        doAnswer(invocation -> {
            timer.set(invocation.getArgument(0));
            return scheduled;
        }).when(scheduler).schedule(org.mockito.ArgumentMatchers.<Callable<Object>>any(), anyLong(), eq(TimeUnit.NANOSECONDS));
        var start = Instant.parse("2026-10-28T00:00:00Z");
        var pending = new CompletableFuture<String>();
        var set = selected((call, context) -> pending);
        try (var control = ExecutionControl.withDeadline(new ResourceBudget(Map.of()),
                start.plusSeconds(1), Clock.fixed(start, ZoneOffset.UTC), scheduler)) {
            var result = set.execute(call("echo", "{\"text\":\"hi\"}"), context(control)).toCompletableFuture();
            assertFalse(result.isDone());
            timer.get().call();
            assertEquals(TIMED_OUT, result.join().status());
            verify(scheduled).cancel(false);
            assertFalse(pending.isDone()); // 包装等待结束不冒充业务回滚。
            pending.complete("late");
            assertEquals(TIMED_OUT, result.join().status());
        }
    }

    /**
     * 验证取消阶段子调用不会反向取消父执行或其他工具。
     */
    @Test
    void cancellingChildInvocationDoesNotCancelParentOrOtherTool() {
        var pending = new CompletableFuture<String>();
        var set = selected((call, context) -> pending);
        try (var parent = ExecutionControl.create(); var child = parent.child()) {
            var parentContext = context(parent);
            var childContext = parentContext.withControl(child);
            assertEquals(parentContext.requestId(), childContext.requestId());
            assertSame(parentContext.bindings(), childContext.bindings());
            assertSame(parent.budget(), childContext.control().budget());
            var first = set.execute(call("echo", "{\"text\":\"hi\"}"), childContext).toCompletableFuture();
            var second = set.execute(call("echo", "{\"text\":\"hi\"}"), parentContext).toCompletableFuture();
            child.cancel();
            assertEquals(CANCELLED, first.join().status());
            assertFalse(second.isDone());
            parent.checkActive();
            pending.complete("second");
            assertEquals(SUCCESS, second.join().status());
        }
    }

    /**
     * 验证同步工具在取消后抛错时保留取消原因。
     */
    @Test
    void synchronousFailureAfterCancellationRetainsStopReason() {
        var set = selected((call, context) -> {
            context.control().cancel();
            throw new IllegalStateException("operation was interrupted; sensitive detail");
        });
        try (var control = ExecutionControl.create()) {
            var result = set.execute(call("echo", "{\"text\":\"hi\"}"), context(control)).toCompletableFuture().join();
            assertEquals(CANCELLED, result.status());
            assertFalse(result.output().contains("sensitive"));
        }
    }

    /**
     * 验证无参数工具接受空输入，可信上下文的文本和 JSON 均脱敏。
     */
    @Test
    void definitionWithoutArgumentsAcceptsEmptyInputAndContextIsRedacted() {
        var definition = LLMTool.function("ping", "ping", null, arguments -> "pong");
        var set = new LLMToolSet(mapper, List.of(LLMToolBinding.from(definition)));
        try (var control = ExecutionControl.create()) {
            var context = context(control);
            assertEquals("pong", set.execute(call("ping", null), context).toCompletableFuture().join().output());
            var json = mapper.writeValueAsString(context);
            assertFalse(json.contains("trusted-user"));
            assertFalse(json.contains("trusted-space"));
            assertFalse(json.contains("control"));
            assertFalse(context.toString().contains("trusted-user"));
        }
    }

    /**
     * 验证非法调用和循环 schema 不会启动业务执行。
     */
    @Test
    void rejectsInvalidCallAndCyclicSchemaWithoutExecuting() {
        var executions = new AtomicInteger();
        var set = selected((call, context) -> {
            executions.incrementAndGet();
            return CompletableFuture.completedFuture("done");
        });
        try (var control = ExecutionControl.create()) {
            var malformed = call("echo", "{\"text\":\"hi\"}");
            malformed.setType("unknown");
            assertEquals(INVALID_CALL, set.execute(malformed, context(control)).toCompletableFuture().join().status());
            malformed.setType("function");
            malformed.setId(" ");
            var invalid = set.execute(malformed, context(control)).toCompletableFuture().join();
            assertEquals(INVALID_CALL, invalid.status());
            assertThrows(IllegalStateException.class, invalid::toMessage);
            assertEquals(0, executions.get());
        }
        var cyclic = new LLMObjectFunctionParameter("cycle", new HashMap<>(), List.of());
        cyclic.getProperties().put("self", cyclic);
        var definition = tool("cycle");
        definition.setParameters(cyclic);
        assertThrows(IllegalArgumentException.class, () -> new LLMToolBinding(definition,
                (call, context) -> CompletableFuture.completedFuture("done")));
    }
}
