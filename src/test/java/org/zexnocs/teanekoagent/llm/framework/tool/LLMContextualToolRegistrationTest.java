package org.zexnocs.teanekoagent.llm.framework.tool;

import lombok.NonNull;
import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.llm.framework.tool.api.LLMToolMapping;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMTool;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMToolProvider;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;
import tools.jackson.databind.ObjectMapper;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationResult.Status.*;

/**
 * 从注解扫描到实际异步完成/回填的离线使用方，不启动容器、数据库或模型。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class LLMContextualToolRegistrationTest {
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 提供同步、异步及非法返回值等工具夹具，用受控阶段验证真实扫描与执行链。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    static class Provider implements ILLMToolProvider {
        final CompletableFuture<Map<String, String>> pending = new CompletableFuture<>();
        ToolInvocationContext received;
        String text;
        int calls;

        /**
         * 保存测试观察到的业务参数与可信上下文，返回待测试主动完成的业务阶段。
         *
         * @param text 待处理的业务文本
         * @param context 用于本次测试的 {@code context} 数据
         * @return 等待工具业务结束后形成文本结果的阶段，异步失败继续传播
         */
        @LLMToolMapping(value = "remember", toolPackage = "test-memory")
        public CompletionStage<Map<String, String>> remember(String text, ToolInvocationContext context) {
            calls++;
            this.text = text;
            this.received = context;
            return pending.minimalCompletionStage();
        }

        /**
         * 提供旧同步反射工具用例，并记录实际调用次数。
         *
         * @param text 待处理的业务文本
         * @return 按本方法契约生成的文本或标识
         */
        @LLMToolMapping("sync")
        public String sync(String text) {
            calls++;
            return text;
        }

        /**
         * 提供同步返回但需要可信上下文的方法，验证上下文注入不依赖异步返回类型。
         *
         * @param context 用于本次测试的 {@code context} 数据
         * @return 按本方法契约生成的文本或标识
         */
        @LLMToolMapping("context_sync")
        public String contextSync(ToolInvocationContext context) {
            this.received = context;
            return context.requestId().toString();
        }

        /**
         * 提供已正常结束的空业务阶段，验证 Void 的工具结果转换。
         *
         * @return 本用例受控的完成阶段，可由测试推进成功、拒绝或异常
         */
        @LLMToolMapping("async_void")
        public CompletionStage<Void> asyncVoid() {
            return CompletableFuture.completedFuture(null);
        }

        /**
         * 故意返回缺失的异步阶段，用于验证非法实现不会被当作成功。
         *
         * @return 等待工具业务结束后形成文本结果的阶段，异步失败继续传播
         */
        @LLMToolMapping("null_stage")
        public CompletionStage<String> nullStage() {
            return null;
        }

        /**
         * 抛出带测试敏感标记的业务异常，验证错误回填不会泄漏异常文本。
         *
         * @return 按本方法契约生成的文本或标识
         * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
         */
        @LLMToolMapping("throwing")
        public String throwing() {
            throw new IllegalArgumentException("secret-not-for-model");
        }
    }

    /**
     * 使用真实工具扫描器与受控 BeanScanner 装配 provider，不启动 Spring 容器。
     *
     * @param provider 供应商注册标识，不由模型名称推断
     * @return 满足本方法约束的 {@code LLMToolService} 结果
     */
    private LLMToolService registered(Provider provider) {
        var scanner = mock(IBeanScanner.class);
        when(scanner.getBeansOfType(ILLMTool.class)).thenReturn(Map.of());
        when(scanner.getBeansOfType(ILLMToolProvider.class)).thenReturn(Map.of("provider", provider));
        when(scanner.getBeansWithAnnotation(org.zexnocs.teanekoagent.llm.framework.tool.api.LLMToolProvider.class))
                .thenReturn(Map.of());
        doReturn(Provider.class).when(scanner).getBeanClass(provider);
        var service = new LLMToolService(scanner, mapper);
        service.init();
        return service;
    }

    /**
     * 验证可信上下文不进入 schema，反射工具等业务阶段完成后才序列化结果。
     */
    @Test
    void annotatedContextIsNeverInSchemaAndActualStageIsFlattenedBeforeJsonResult() {
        var provider = new Provider();
        var service = registered(provider);
        var definition = service.getTool("remember");
        var schema = LLMFunctionParameterJsonMapper.toJsonSchema(definition.getParameters());
        assertEquals(Set.of("text"), definition.getParameters().getProperties().keySet());
        assertEquals(List.of("text"), definition.getParameters().getRequired());
        assertFalse(mapper.writeValueAsString(schema).contains("context"));
        var set = new LLMToolSet(mapper, service.getTools("test-memory").values().stream().map(LLMToolBinding::from).toList());
        try (var control = ExecutionControl.create()) {
            var context = LLMToolSetTest.context(control);
            var output = set.execute(LLMToolSetTest.call("remember", "{\"text\":\"call me Neko\"}"), context).toCompletableFuture();
            assertFalse(output.isDone());
            assertSame(context, provider.received);
            assertEquals("call me Neko", provider.text);
            assertEquals(1, provider.calls);
            provider.pending.complete(Map.of("status", "committed"));
            var result = output.join();
            assertEquals(SUCCESS, result.status());
            assertEquals("committed", mapper.readTree(result.output()).path("status").asString());
            assertEquals("call-1", result.toMessage().getToolCallId());
        }
    }

    /**
     * 验证模型不能伪造上下文或执行未暴露工具。
     */
    @Test
    void modelCannotInjectContextAndHiddenToolsCannotBeInvoked() {
        var provider = new Provider();
        var service = registered(provider);
        var set = new LLMToolSet(mapper, List.of(LLMToolBinding.from(service.getTool("remember"))));
        try (var control = ExecutionControl.create()) {
            var context = LLMToolSetTest.context(control);
            assertEquals(INVALID_ARGUMENTS, set.execute(LLMToolSetTest.call("remember",
                    "{\"text\":\"fact\",\"context\":{\"requestId\":\"fake\"}}"), context).toCompletableFuture().join().status());
            assertEquals(NOT_ALLOWED, set.execute(LLMToolSetTest.call("sync", "{\"text\":\"x\"}"), context)
                    .toCompletableFuture().join().status());
            assertEquals(0, provider.calls);
        }
    }

    /**
     * 验证旧同步入口在业务启动前拒绝异步上下文工具，原同步工具仍可使用。
     *
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     */
    @Test
    void synchronousRegistryCallRejectsContextualToolsBeforeBusinessStartsButKeepsLegacySync() throws Exception {
        var provider = new Provider();
        var service = registered(provider);
        assertThrows(UnsupportedOperationException.class,
                () -> service.call(LLMToolSetTest.call("remember", "{\"text\":\"fact\"}")));
        assertThrows(UnsupportedOperationException.class,
                () -> service.call(LLMToolSetTest.call("async_void", "{}")));
        assertEquals(0, provider.calls);
        assertEquals("legacy", service.call(LLMToolSetTest.call("sync", "{\"text\":\"legacy\"}")));
        assertEquals(1, provider.calls);
    }

    /**
     * 验证同步及 Void 结果可正常回填，声明异步却返回 null 阶段时明确失败。
     */
    @Test
    void synchronousAndVoidResultsWorkAndNullStageIsFailure() {
        var provider = new Provider();
        var service = registered(provider);
        var set = new LLMToolSet(mapper, service.getTools().values().stream().map(LLMToolBinding::from).toList());
        try (var control = ExecutionControl.create()) {
            var context = LLMToolSetTest.context(control);
            var contextual = set.execute(LLMToolSetTest.call("context_sync", "{}"), context).toCompletableFuture().join();
            assertEquals(SUCCESS, contextual.status());
            assertEquals(context.requestId().toString(), contextual.output());
            assertSame(context, provider.received);
            assertEquals("sync-value", set.execute(LLMToolSetTest.call("sync", "{\"text\":\"sync-value\"}"), context)
                    .toCompletableFuture().join().output());
            var voidResult = set.execute(LLMToolSetTest.call("async_void", "{}"), context).toCompletableFuture().join();
            assertEquals(SUCCESS, voidResult.status());
            assertEquals("", voidResult.output());
            assertEquals(FAILED, set.execute(LLMToolSetTest.call("null_stage", "{}"), context).toCompletableFuture().join().status());
            var failure = set.execute(LLMToolSetTest.call("throwing", "{}"), context).toCompletableFuture().join();
            assertEquals(FAILED, failure.status());
            assertFalse(failure.output().contains("secret-not-for-model"));
        }
    }

    /**
     * 验证注册表重载不会替换已绑定的执行器或 provider 实例。
     */
    @Test
    void reloadingRegistryDoesNotReplaceAnAlreadyBoundExecutor() {
        var original = new Provider();
        var replacement = new Provider();
        var scanner = mock(IBeanScanner.class);
        when(scanner.getBeansOfType(ILLMTool.class)).thenReturn(Map.of());
        when(scanner.getBeansOfType(ILLMToolProvider.class)).thenReturn(Map.of("provider", original))
                .thenReturn(Map.of("provider", replacement));
        when(scanner.getBeansWithAnnotation(org.zexnocs.teanekoagent.llm.framework.tool.api.LLMToolProvider.class))
                .thenReturn(Map.of());
        doReturn(Provider.class).when(scanner).getBeanClass(any(Provider.class));
        var service = new LLMToolService(scanner, mapper);
        service.init();
        var oldSet = new LLMToolSet(mapper, List.of(LLMToolBinding.from(service.getTool("remember"))));
        service.reload();
        var newSet = new LLMToolSet(mapper, List.of(LLMToolBinding.from(service.getTool("remember"))));
        try (var control = ExecutionControl.create()) {
            var context = LLMToolSetTest.context(control);
            var first = oldSet.execute(LLMToolSetTest.call("remember", "{\"text\":\"old\"}"), context).toCompletableFuture();
            var second = newSet.execute(LLMToolSetTest.call("remember", "{\"text\":\"new\"}"), context).toCompletableFuture();
            original.pending.complete(Map.of("version", "old"));
            assertTrue(first.isDone());
            assertFalse(second.isDone());
            replacement.pending.complete(Map.of("version", "new"));
            assertEquals("old", mapper.readTree(first.join().output()).path("version").asString());
            assertEquals("new", mapper.readTree(second.join().output()).path("version").asString());
            assertEquals("old", original.text);
            assertEquals("new", replacement.text);
        }
    }

    /**
     * 验证直接反射同步入口在方法调用前拒绝异步或可信上下文签名。
     *
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     */
    @Test
    void directReflectiveSyncEntryRejectsAsyncAndContextBeforeInvocation() throws Exception {
        var provider = new Provider();
        var method = Provider.class.getMethod("remember", String.class, ToolInvocationContext.class);
        var executor = new ReflectiveLLMToolExecutor(mapper, provider, method);
        assertThrows(UnsupportedOperationException.class, () -> executor.call("{\"text\":\"no\"}"));
        assertEquals(0, provider.calls);
        try (var control = ExecutionControl.create()) {
            var context = LLMToolSetTest.context(control);
            control.cancel();
            assertThrows(org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException.class,
                    () -> executor.call(LLMToolSetTest.call("remember", "{\"text\":\"no\"}"), context));
            assertEquals(0, provider.calls);
        }
    }

    /**
     * 验证反射工具取消后不再序列化迟到业务对象。
     */
    @Test
    void cancelledReflectiveCallDoesNotSerializeLateBusinessObject() {
        var provider = new Provider();
        var service = registered(provider);
        var set = new LLMToolSet(mapper, List.of(LLMToolBinding.from(service.getTool("remember"))));
        var reads = new AtomicInteger();
        Map<String, String> late = new AbstractMap<>() {
            /**
             * 记录测试业务对象的实际遍历次数，用于证明取消后不会序列化迟到结果。
             *
             * @return 保持本方法顺序和筛选规则的结果集合
             */
            @Override
            @NonNull
            public Set<Entry<String, String>> entrySet() {
                reads.incrementAndGet();
                return Set.of(Map.entry("status", "late"));
            }
        };
        try (var control = ExecutionControl.create()) {
            var result = set.execute(LLMToolSetTest.call("remember", "{\"text\":\"fact\"}"),
                    LLMToolSetTest.context(control)).toCompletableFuture();
            control.cancel();
            assertEquals(CANCELLED, result.join().status());
            provider.pending.complete(late);
            assertEquals(0, reads.get());
            assertEquals(CANCELLED, result.join().status());
        }
    }
}
