package org.zexnocs.teanekoapp.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.zexnocs.teanekoagent.adapter.web.AgentConsole;
import org.zexnocs.teanekoagent.adapter.web.AgentConsoleResources;
import org.zexnocs.teanekoagent.adapter.web.AgentConsoleService;
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
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * 使用原管理网关和客户端注册表验证 Web 授权能力的归属、撤权和白名单。
 *
 * 2026/11/03：全局资源独立授权，不借用聊天目标或原生身份。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
class WebManagementCallsTest {
    /** 伪造同文本上下文仍无权访问；在途会话撤销后不能返回成功或保留可用能力。 */
    @Test
    void requiresTheExactHostCapabilityAndRechecksRevokedSessions() {
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
        when(service.memory(any(), any())).thenAnswer(call -> {
            captured.set(call.getArgument(0));
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
        var mapper = new ObjectMapper();
        var calls = new WebManagementCalls(gateway, clients, plugins, files, mapper, resources);
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
