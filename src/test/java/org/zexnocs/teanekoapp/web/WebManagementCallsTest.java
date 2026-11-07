package org.zexnocs.teanekoapp.web;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.zexnocs.teanekoagent.integration.web.AgentConsole;
import org.zexnocs.teanekoagent.integration.web.AgentConsoleResources;
import org.zexnocs.teanekoagent.integration.web.AgentConsoleService;
import org.zexnocs.teanekoagent.integration.web.AgentLayersConsole;
import org.zexnocs.teanekoagent.integration.web.AgentLayersConsoleService;
import org.zexnocs.teanekoagent.integration.web.AgentLibraryResources;
import org.zexnocs.teanekoapp.web.client.ChatTargets;
import org.zexnocs.teanekoapp.web.client.ClientManagementDirectory;
import org.zexnocs.teanekoapp.web.client.IClientManagementAdapter;
import org.zexnocs.teanekoapp.web.client.TeaNekoClientManagement;
import org.zexnocs.teanekoclient.onebot.core.OnebotManagementAdapter;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.plugin.management.ManagementAuthority;
import org.zexnocs.teanekocore.plugin.management.ManagementContext;
import org.zexnocs.teanekocore.plugin.management.ManagementGateway;
import org.zexnocs.teanekocore.plugin.management.ManagementResource;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 使用原管理网关和客户端注册表验证 Web 授权能力的归属、撤权和白名单。
 *
 * 2026/11/03：全局资源独立授权，不借用聊天目标或原生身份。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：更新分层重构后的类型引用，保持原调用与生命周期行为。
 * <br>2026/11/07：覆盖同步阻塞、异步挂起、资源隔离、超时及实际回收。
 * <br>2026/11/07：画像查询从实例管理入口验证可选筛选项缺省与严格参数绑定。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.8.7-l
 */
class WebManagementCallsTest {
    /**
     * 2026/11/07：旧页面省略事件筛选时仍可读取个人画像，不放宽代次等其他字段的空值校验。
     * @throws Exception 当管理调用未在测试期限内完成时
     */
    @Test
    void globalProfileQueriesDefaultOnlyTheOptionalEventFilter() throws Exception {
        try (var scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
             var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var mapper = JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();
            var plugins = mock(PluginRegistry.class);
            when(plugins.isEnabled("teaneko.agent")).thenReturn(true);
            when(plugins.capture("teaneko.agent")).thenReturn(new PluginRegistry.Token(Map.of("teaneko.agent", 0L)));
            var files = mock(IFileConfigService.class);
            var security = new WebSecurityFileConfig();
            security.setManagementResources(List.of("teaneko.agent:layers-space"));
            when(files.requireCurrent(WebSecurityFileConfig.class)).thenReturn(security);
            when(files.requireCurrent(WebManagementFileConfig.class)).thenReturn(new WebManagementFileConfig());
            var service = mock(AgentLayersConsoleService.class);
            when(service.execute(any(), any())).thenAnswer(call ->
                    CompletableFuture.completedStage(call.getArgument(1, AgentLayersConsole.Query.class)));
            List<ManagementResource<?, ?>> resources = List.of(new AgentLibraryResources.Layers(service));
            var beans = new StaticListableBeanFactory();
            var gateway = new ManagementGateway(plugins, resources, beans.getBeanProvider(ManagementAuthority.class));
            var clients = mock(ClientManagementDirectory.class);
            try (var calls = new WebManagementCalls(gateway, clients, plugins, files, mapper, resources,
                    scheduler, workers)) {
                beans.addBean("authority", calls);
                var subject = UUID.randomUUID();
                var body = mapper.createObjectNode().put("action", "READ").put("namespace", "shared")
                        .put("generation", -1).put("revision", -1).put("subjectId", subject.toString()).put("topic", "");
                for (var request : List.of(body, body.deepCopy().putNull("eventsOnly"),
                        body.deepCopy().put("eventsOnly", false))) {
                    var parsed = assertInstanceOf(AgentLayersConsole.Query.class,
                            calls.invokeGlobal("teaneko.agent:layers-space", request, () -> {})
                                    .toCompletableFuture().get(3, TimeUnit.SECONDS));
                    assertEquals(subject, parsed.subjectId());
                    assertFalse(parsed.eventsOnly());
                }
                var events = body.deepCopy().putNull("subjectId").put("eventsOnly", true);
                var parsed = assertInstanceOf(AgentLayersConsole.Query.class,
                        calls.invokeGlobal("teaneko.agent:layers-space", events, () -> {})
                                .toCompletableFuture().get(3, TimeUnit.SECONDS));
                assertTrue(parsed.eventsOnly());
                assertNull(parsed.subjectId());
                assertThrows(MismatchedInputException.class, () -> calls.invokeGlobal("teaneko.agent:layers-space",
                        events.deepCopy().putNull("generation"), () -> {}));
                assertThrows(MismatchedInputException.class, () -> calls.invokeGlobal("teaneko.agent:layers-space",
                        body.deepCopy().put("eventsOnly", "invalid"), () -> {}));
                verifyNoInteractions(clients);
            }
        }
    }

    /**
     * 2026/11/06：挂起资源截止后仍占物理槽，健康资源可用，迟到完成和关闭都不重复归还。
     * @throws Exception 当有界等待或资源关闭失败时
     */
    @Test
    void timeoutKeepsPhysicalSlotsAndIsolatesResources() throws Exception {
        try (var scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
             var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var plugins = mock(PluginRegistry.class);
            var beans = new StaticListableBeanFactory();
            var mapper = new ObjectMapper();
            var files = mock(IFileConfigService.class);
            var security = new WebSecurityFileConfig();
            security.setManagementResources(List.of("teaneko.core:slow", "teaneko.core:healthy"));
            var policy = new WebManagementFileConfig();
            policy.setTimeoutMillis(100);
            policy.setMaxInFlight(4);
            policy.setMaxPerResource(1);
            when(files.requireCurrent(WebSecurityFileConfig.class)).thenReturn(security);
            when(files.requireCurrent(WebManagementFileConfig.class)).thenReturn(policy);
            org.zexnocs.teanekocore.plugin.management.GlobalManagementResource<Query, String> slow = mock();
            org.zexnocs.teanekocore.plugin.management.GlobalManagementResource<Query, String> healthy = mock();
            when(slow.descriptor()).thenReturn(new ManagementResource.Descriptor("teaneko.core", "slow", 1, "test", true, List.of()));
            when(healthy.descriptor()).thenReturn(new ManagementResource.Descriptor("teaneko.core", "healthy", 1, "test", false, List.of()));
            when(slow.requestType()).thenReturn(Query.class);
            when(healthy.requestType()).thenReturn(Query.class);
            var work = new CompletableFuture<String>();
            var release = new java.util.concurrent.CountDownLatch(1);
            var captured = new AtomicReference<ManagementContext>();
            var invoked = new CompletableFuture<Void>();
            when(slow.execute(any(), any())).thenAnswer(call -> {
                captured.set(call.getArgument(0));
                invoked.complete(null);
                if (!release.await(3, java.util.concurrent.TimeUnit.SECONDS)) {
                    throw new IllegalStateException("测试同步资源等待超时。");
                }
                return work;
            });
            when(healthy.execute(any(), any())).thenReturn(CompletableFuture.completedStage("正常"));
            List<ManagementResource<?, ?>> resources = List.of(slow, healthy);
            var gateway = new ManagementGateway(plugins, resources, beans.getBeanProvider(ManagementAuthority.class));
            try (var calls = new WebManagementCalls(gateway, mock(ClientManagementDirectory.class), plugins,
                files, mapper, resources, scheduler, workers)) {
                beans.addBean("authority", calls);
                var waiting = calls.invokeGlobal("teaneko.core:slow", mapper.readTree("{}"), () -> {
                });
                assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> waiting.toCompletableFuture().get(3, java.util.concurrent.TimeUnit.SECONDS));
                assertEquals(1, calls.inFlight().size());
                assertTrue(calls.inFlight().getFirst().waitingEnded());
                assertThrows(org.zexnocs.teanekocore.actuator.execution.ExecutionStoppedException.class,
                    captured.get()::checkActive);
                assertEquals("正常", calls.invokeGlobal("teaneko.core:healthy", mapper.readTree("{}"), () -> {
                    })
                    .toCompletableFuture().get(3, java.util.concurrent.TimeUnit.SECONDS));
                var rejected = calls.invokeGlobal("teaneko.core:slow", mapper.readTree("{}"), () -> {
                });
                assertInstanceOf(org.zexnocs.teanekocore.actuator.execution.ExecutionBusyException.class,
                    assertThrows(CompletionException.class, () -> rejected.toCompletableFuture().join()).getCause());
                release.countDown();
                work.complete("迟到结果");
                org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(3))
                    .untilAsserted(() -> assertTrue(calls.inFlight().isEmpty()));
                assertThrows(CompletionException.class, () -> waiting.toCompletableFuture().join());
                var closingWork = new CompletableFuture<String>();
                var closingStarted = new CompletableFuture<Void>();
                when(slow.execute(any(), any())).thenAnswer(call -> {
                    closingStarted.complete(null);
                    return closingWork;
                });
                var closing = calls.invokeGlobal("teaneko.core:slow", mapper.readTree("{}"), () -> {
                });
                closingStarted.get(3, java.util.concurrent.TimeUnit.SECONDS);
                calls.close();
                assertThrows(CompletionException.class, () -> closing.toCompletableFuture().join());
                assertEquals(1, calls.inFlight().size());
                closingWork.complete("关闭后收尾");
                org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(3))
                    .untilAsserted(() -> assertTrue(calls.inFlight().isEmpty()));
            }
        }
    }

    /**
     * 无业务数据的生命周期测试请求。
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.7-l
     * @version 4.8.7-l
     */
    public record Query() {}

    /** 伪造同文本上下文仍无权访问；在途会话撤销后不能返回成功或保留可用能力。 */
    @Test
    void requiresTheExactHostCapabilityAndRechecksRevokedSessions() {
        try (var scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
             var workers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var plugins = mock(PluginRegistry.class);
            when(plugins.isEnabled("teaneko.agent")).thenReturn(true);
            when(plugins.capture("teaneko.agent")).thenReturn(new PluginRegistry.Token(Map.of("teaneko.agent", 0L)));
            // 2026/11/03：实例级资源仍使用原插件代次和同一唯一授权适配器。
            when(plugins.isEnabled("teaneko.core")).thenReturn(true);
            when(plugins.capture("teaneko.core")).thenReturn(new PluginRegistry.Token(Map.of("teaneko.core", 0L)));
            var beans = new StaticListableBeanFactory();
            beans.addBean("plugins", plugins);
            var adapter = mock(IClientManagementAdapter.class);
            when(adapter.resolve("a", "t")).thenReturn(CompletableFuture.completedStage(new ChatTargets.Resolved(
                "onebot", "a", "t", "onebot-group@123", "开发群", "GROUP", "123", true)));
            var scanner = mock(IBeanScanner.class);
            when(scanner.getBeansWithAnnotationAndInterface(TeaNekoClientManagement.class, IClientManagementAdapter.class))
                .thenReturn(Map.of("onebot", new IndependentPair<>(
                    OnebotManagementAdapter.class.getAnnotation(TeaNekoClientManagement.class), adapter)));
            doReturn(OnebotManagementAdapter.class).when(scanner).getBeanClass(adapter);
            var clients = new ClientManagementDirectory(scanner, beans.getBeanProvider(PluginRegistry.class));
            var service = mock(AgentConsoleService.class);
            var pending = new CompletableFuture<>();
            var captured = new AtomicReference<ManagementContext>();
            var invoked = new CompletableFuture<Void>();
            when(service.memory(any(), any())).thenAnswer(call -> {
                captured.set(call.getArgument(0));
                invoked.complete(null);
                return pending;
            });
            var monitor = new org.zexnocs.teanekocore.actuator.runtime.RuntimeMonitor(java.time.Clock.systemUTC(),
                () -> new org.zexnocs.teanekocore.actuator.runtime.RuntimeSettings().snapshot());
            List<ManagementResource<?, ?>> resources = List.of(new AgentConsoleResources.Memory(service),
                new org.zexnocs.teanekocore.actuator.runtime.RuntimeManagement.Runtime(monitor,
                    new org.zexnocs.teanekocore.actuator.timer.TimerDirectory()));
            var gateway = new ManagementGateway(plugins, resources, beans.getBeanProvider(ManagementAuthority.class));
            var files = mock(IFileConfigService.class);
            when(files.requireCurrent(WebSecurityFileConfig.class)).thenReturn(new WebSecurityFileConfig());
            when(files.requireCurrent(WebManagementFileConfig.class)).thenReturn(new WebManagementFileConfig());
            var mapper = new ObjectMapper();
            var calls = new WebManagementCalls(gateway, clients, plugins, files, mapper, resources, scheduler, workers);
            beans.addBean("webAuthority", calls);
            var valid = new AtomicBoolean(true);
            Runnable session = () -> {
                if (!valid.get()) {
                    throw new SecurityException("测试会话已经撤销。");
                }
            };
            var body = mapper.readTree("{\"action\":\"READ\",\"namespace\":\"\",\"generation\":-1,\"revision\":-1}");
            var globalBody = mapper.readTree("{\"action\":\"TIMERS\",\"limit\":10,\"before\":0}");
            assertNotNull(calls.invokeGlobal("teaneko.core:runtime", globalBody, session).toCompletableFuture().join());
            assertThrows(SecurityException.class, () -> calls.invokeGlobal("teaneko.agent:memory", body, session));
            assertThrows(SecurityException.class, () -> calls.invoke("onebot", "a", "t", "teaneko.core:runtime", globalBody, session));
            var result = calls.invoke("onebot", "a", "t", "teaneko.agent:memory", body, session);
            invoked.orTimeout(3, java.util.concurrent.TimeUnit.SECONDS).join();
            var context = Objects.requireNonNull(captured.get());
            var forged = new ManagementContext(context.subject(), context.scopeRef(), context.requestId(), context.control());
            assertThrows(CompletionException.class, () -> gateway.execute("teaneko.agent:memory", forged,
                mapper.treeToValue(body, AgentConsole.MemoryQuery.class)).toCompletableFuture().join());
            assertThrows(SecurityException.class, () -> calls.invoke("onebot", "a", "t",
                "teaneko.agent:stickers", body, session));
            valid.set(false);
            assertThrows(SecurityException.class, context::checkActive);
            pending.complete("不应交付的迟到结果");
            assertThrows(CompletionException.class, () -> result.toCompletableFuture().join());
            assertThrows(CompletionException.class, () -> calls.authorize(context, resources.getFirst().descriptor())
                .toCompletableFuture().join());
        }
    }
}
