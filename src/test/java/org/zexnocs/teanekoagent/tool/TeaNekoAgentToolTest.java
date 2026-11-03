package org.zexnocs.teanekoagent.tool;

import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.agent.*;
import org.zexnocs.teanekoagent.expression.ExpressionFeature;
import org.zexnocs.teanekoagent.expression.ExpressionRuntime;
import org.zexnocs.teanekoagent.llm.framework.LLMOperationRejectedException;
import org.zexnocs.teanekoagent.llm.framework.message.content.ImageLLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.llm.framework.tool.*;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMToolCall;
import org.zexnocs.teanekoagent.plugin.AgentPluginRuntime;
import org.zexnocs.teanekoagent.sticker.MediaReplyFeature;
import org.zexnocs.teanekoagent.sticker.StickerReplyService;
import org.zexnocs.teanekoagent.sticker.StickerReplyTools;
import org.zexnocs.teanekoagent.tool.api.TeaNekoAgentTool;
import org.zexnocs.teanekoagent.tool.api.TeaNekoAgentToolClass;
import org.zexnocs.teanekoagent.tool.api.TeaNekoAgentToolParam;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.command.api.DefaultValue;
import org.zexnocs.teanekocore.framework.composition.ComponentPatch;
import org.zexnocs.teanekocore.framework.composition.EntityId;
import org.zexnocs.teanekocore.framework.composition.EntitySnapshot;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.plugin.PluginDescriptor;
import org.zexnocs.teanekocore.plugin.PluginModule;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * 覆盖自动发现、代理、可信参数及异步结果边界，业务授权继续复用各工具原有测试。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：覆盖媒体工具和显式能力在真实 Spring 注册链中的联合装配。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.1-l
 * @version 4.7.0-l
 */
class TeaNekoAgentToolTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AgentDefinition definition = new AgentDefinition("注解测试", new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.values()));
    private final AgentRunRequest request = new AgentRunRequest(UUID.randomUUID(),
            new AgentActorContext(new AgentScope("onebot", "群"), AgentActorContext.Origin.USER, Optional.of(UUID.randomUUID()), "用户", "消息", "群"),
            AgentActivity.COMMAND, List.of());

    /** 自动能力进入真实 Spring 宿主目录，扫描不提前构造懒业务 Bean。 */
    @Test void discoversSpringServiceLazilyWithoutManualFeature() {
        var created = new AtomicInteger();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor(); var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ObjectMapper.class, () -> mapper);
            context.registerBean("virtualExecutor", ExecutorService.class, () -> executor, value -> value.setDestroyMethodName(""));
            context.registerBean(PluginModule.class, () -> () -> new PluginDescriptor("teaneko.agent", "4.6.0-l", 1, Set.of()));
            context.registerBean("demoTools", DemoTools.class, () -> { created.incrementAndGet(); return new DemoTools(); }, value -> value.setLazyInit(true));
            context.register(TeaNekoAgentToolRegistry.class, AgentPluginRuntime.class, PluginRegistry.class);
            context.refresh();
            assertEquals(Set.of("teaneko.agent:demo"), context.getBean(AgentPluginRuntime.class).featureIds());
            assertEquals(0, created.get());
            var features = context.getBean(TeaNekoAgentToolRegistry.class).combine(List.of());
            assertEquals(3, features.getFirst().tools().select(definition, request, null).size());
            assertEquals(1, created.get());
        }
    }

    /**
     * 2026/11/04：验证真实媒体工具归属能够通过启动注册，且不会重复注册自动能力。
     */
    @Test
    void startsWithMediaToolsOwnedByTheirExplicitFeature() {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor();
             var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ObjectMapper.class, () -> mapper);
            context.registerBean("virtualExecutor", ExecutorService.class, () -> executor,
                    value -> value.setDestroyMethodName(""));
            context.registerBean(PluginModule.class,
                    () -> () -> new PluginDescriptor("teaneko.agent", "4.7.0-l", 1, Set.of()));
            context.registerBean(StickerReplyService.class, () -> mock(StickerReplyService.class));
            context.registerBean(ExpressionRuntime.class, () -> mock(ExpressionRuntime.class));
            context.register(StickerReplyTools.class, MediaReplyFeature.class, ExpressionFeature.class,
                    TeaNekoAgentToolRegistry.class, AgentPluginRuntime.class, PluginRegistry.class);

            context.refresh();

            assertEquals(Set.of("teaneko.agent:expression", "teaneko.agent:media-replies"),
                    context.getBean(AgentPluginRuntime.class).featureIds());
        }
    }

    /** 保留 Spring 环绕，默认值、参数改名及宿主参数只进入实际调用，不接受模型伪造。 */
    @Test void bindsDescriptionsDefaultsAndTrustedContextThroughProxy() {
        var target = new DemoTools(); var intercepted = new AtomicInteger();
        var factory = new ProxyFactory(target); factory.setProxyTargetClass(true);
        factory.addAdvice((MethodInterceptor) invocation -> { intercepted.incrementAndGet(); return invocation.proceed(); });
        var bindings = select(factory.getProxy()); var echo = bindings.stream().filter(value -> value.definition().getName().equals("echo")).findFirst().orElseThrow();
        var schema = echo.definition().getParameters();
        assertEquals(Set.of("word", "count"), schema.getProperties().keySet());
        assertEquals(List.of("word"), schema.getRequired());
        assertEquals("待重复的文字。", schema.getProperties().get("word").getDescription());
        assertTrue(echo.supportsClient("任意客户端"));
        var other = new AgentRunRequest(UUID.randomUUID(), new AgentActorContext(new AgentScope("其他", "群"),
                AgentActorContext.Origin.USER, request.actor().userId(), "用户", "消息", "群"), AgentActivity.COMMAND, List.of());
        assertEquals(2, TeaNekoAgentToolMethods.select(target, definition, other, null, null, mapper, Runnable::run).size());
        try (var control = ExecutionControl.create()) {
            var context = invocation(control); var set = new LLMToolSet(mapper, bindings);
            var result = set.execute(call("echo", "{\"word\":\"茶\"}"), context).toCompletableFuture().join();
            assertEquals(ToolInvocationResult.Status.SUCCESS, result.status()); assertEquals("茶茶:原调用", result.output());
            assertTrue(intercepted.get() > 0); assertSame(request, target.seen);
            assertEquals(ToolInvocationResult.Status.INVALID_ARGUMENTS, set.execute(call("echo", "{\"word\":\"茶\",\"context\":{}}"), context).toCompletableFuture().join().status());
        }
    }

    /** 异步工具等待真实完成，图像按显式类型返回；等待期间撤销条件则停止交互。 */
    @Test void awaitsTypedMediaAndRejectsRevokedConditions() {
        var target = new DemoTools(); var set = new LLMToolSet(mapper, select(target));
        try (var control = ExecutionControl.create()) {
            var result = set.execute(call("image", "{}"), invocation(control)).toCompletableFuture();
            assertFalse(result.isDone());
            target.pending.complete(new LLMToolOutput("已找到图片", List.of(new ImageLLMContentPart("https://example.com/image.png"))));
            var output = result.join(); assertEquals(1, output.images().size()); assertEquals("已找到图片", output.output());
            target.pending = new CompletableFuture<>();
            var revoked = set.execute(call("image", "{}"), invocation(control)).toCompletableFuture();
            target.allowed = false; target.pending.complete(new LLMToolOutput("不得交付"));
            assertInstanceOf(LLMOperationRejectedException.class, assertThrows(CompletionException.class, revoked::join).getCause());
        }
        target.allowed = true; target.pending = new CompletableFuture<>();
        try (var control = ExecutionControl.create()) {
            var result = set.execute(call("image", "{}"), invocation(control)).toCompletableFuture(); control.cancel();
            assertEquals(ToolInvocationResult.Status.CANCELLED, result.join().status()); assertFalse(target.pending.isCancelled());
        }
    }

    /** 重名注解在注册目录阶段拒绝，避免模型根据不明确工具名调用错误实现。 */
    @Test void rejectsDuplicateToolsDuringDiscovery() {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor(); var context = new AnnotationConfigApplicationContext()) {
            context.registerBean("first", DemoTools.class); context.registerBean("second", DemoTools.class); context.refresh();
            var registry = new TeaNekoAgentToolRegistry(context.getBeanFactory(), mapper, executor);
            assertThrows(IllegalArgumentException.class, () -> registry.combine(List.of()));
        }
    }

    /**
     * 编译当前工具，显式宿主与自动注册使用同一实现。
     * @param source 提供者或代理
     * @return 当前工具
     */
    private List<LLMToolBinding> select(Object source) { return TeaNekoAgentToolMethods.select(source, definition, request, null, null, mapper, Runnable::run); }

    /**
     * 固定实际宿主身份，控制由各用例关闭。
     * @param control 借用控制
     * @return 真实工具上下文
     */
    private ToolInvocationContext invocation(ExecutionControl control) {
        return new ToolInvocationContext(request.requestId(), EntitySnapshot.of(EntityId.random(),
                ComponentPatch.builder().put(AgentComponents.ACTOR, request.actor()).put(AgentComponents.REQUEST, request).build()), control);
    }

    /**
     * 创建原始调用，保持实际调用 ID。
     * @param name 工具名称
     * @param arguments 业务参数
     * @return 原始工具调用
     */
    private static LLMToolCall call(String name, String arguments) { return LLMToolCall.builder().id("原调用").name(name).arguments(arguments).build(); }

    /**
     * 普通 Spring 服务示例，不实现工具接口、不构造 schema 或 AgentFeature。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.6.1-l
     * @version 4.6.0-l
     */
    @Service @TeaNekoAgentToolClass("demo")
    public static class DemoTools {
        private boolean allowed = true;
        private AgentRunRequest seen;
        private CompletableFuture<LLMToolOutput> pending = new CompletableFuture<>();
        /**
         * 同步工具同时展示默认值、重命名及原始调用注入。
         * @param text 业务文字
         * @param count 重复次数
         * @param context 宿主上下文
         * @param call 原始调用
         * @return 业务输出
         */
        @TeaNekoAgentTool @Description("重复文字。")
        public String echo(@TeaNekoAgentToolParam("word") @Description("待重复的文字。") String text,
                @DefaultValue("2") @Description("重复次数。") int count, TeaNekoAgentToolContext context, ILLMToolCall call) {
            seen = context.request(); return text.repeat(count) + ":" + call.getId();
        }
        /** @return 只适用 OneBot 的普通 DTO 结果 */
        @TeaNekoAgentTool(client = "onebot") @Description("仅 OneBot 可见。")
        public Map<String, String> platform() { return Map.of("client", "onebot"); }
        /** @return 等待实际图片读取的阶段 */
        @TeaNekoAgentTool(condition = "canRead") @Description("读取图片。")
        public CompletionStage<LLMToolOutput> image() { return pending; }
        /**
         * 由可信宿主条件决定当前可见性。
         * @param context 固定上下文
         * @return 当前允许时为真
         */
        public boolean canRead(TeaNekoAgentToolContext context) { return allowed && context.request().actor().origin() == AgentActorContext.Origin.USER; }
    }
}
