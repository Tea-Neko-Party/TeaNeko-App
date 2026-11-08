package org.zexnocs.teanekoclient.general.agent;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoplugin.agent.social.contact.adapter.PrivateContactTransport;
import org.junit.jupiter.api.Timeout;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoapp.client.TeaNekoClientScanner;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.client.api.TeaNekoClient;
import org.zexnocs.teanekoapp.client.tools.IMessageSenderTools;
import org.zexnocs.teanekoapp.client.tools.ITeaNekoToolbox;
import org.zexnocs.teanekoapp.response.api.IMessageSendResponseData;
import org.zexnocs.teanekoapp.sender.api.sender_box.IEasyMessageSenderBuilder;
import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserService;
import org.zexnocs.teanekoapp.utils.TeaNekoScopeService;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 使用原客户端注册表验证私聊目标定位、账号切换拒绝与实际发送器选项。
 * <br>2026/11/06：只模拟平台传输，不创建用户或执行高频发现扫描。
 * <br>2026/11/08：夹具通过真实插件组件契约准备参数并验证改造后的端口。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.8.6-l
 */
@Timeout(10)
class PrivateContactTransportTest {
    /** 只使用已有映射和机器人账号，发送关闭重试，账号变化后原目标立即失效。 */
    @Test
    void usesExistingMappingAndRejectsChangedBotIdentity() {
        var scanner = mock(IBeanScanner.class);
        var clients = mock(TeaNekoClientScanner.class);
        var client = mock(ITeaNekoClient.class);
        // 2026/11/06：目录查询夹具提供不可变快照，不绕过正式扫描器修改注册表。
        doReturn(java.util.Map.of("test", IndependentPair.of(mock(TeaNekoClient.class), client))).when(clients).getHandlerMap();
        doAnswer(call -> clients.getHandlerMap().get(call.getArgument(0))).when(clients).getPair(anyString());
        var users = mock(ITeaUserService.class);
        var user = UUID.randomUUID();
        var scope = new AgentScope("test", "test-group@42");
        var toolbox = mock(ITeaNekoToolbox.class);
        when(client.getTeaNekoToolbox()).thenReturn(toolbox);
        when(users.getPlatformId(client, user)).thenReturn("123");
        when(users.get(client, "123")).thenReturn(user);
        when(toolbox.getSelfPlatformId()).thenReturn(Optional.of("bot"));
        var service = new PrivateContactTransport(clients, users, new TeaNekoScopeService(clients));
        var target = service.resolve(scope, user).orElseThrow();
        assertEquals(Optional.of(user), service.user(target.scope(), "123"));
        assertTrue(service.user(scope, "123").isEmpty());
        assertTrue(service.resolve(new AgentScope("unknown", "42"), user).isEmpty());

        var messages = mock(IMessageSenderTools.class);
        var sender = mock(IEasyMessageSenderBuilder.class, RETURNS_SELF);
        when(toolbox.getMessageSenderTools()).thenReturn(messages);
        when(messages.getPrivateBuilder("123")).thenReturn(sender);
        var response = mock(IMessageSendResponseData.class);
        when(response.getMessageId()).thenReturn("confirmed");
        when(sender.sendWithFuture()).thenAnswer(ignored -> new TaskFuture<>(mock(ILogger.class), "离线私聊",
            CompletableFuture.completedFuture(response)));
        var guard = mock(Runnable.class);
        service.send(target, "邀请中的私聊内容", guard).toCompletableFuture().join();
        verify(guard, times(2)).run();
        verify(sender).setRetryCount(0);
        verify(sender).setRecordFailed(false);
        verify(sender).addTextMessage("邀请中的私聊内容");
        when(toolbox.getSelfPlatformId()).thenReturn(Optional.of("another-bot"));
        assertThrows(IllegalStateException.class, () -> service.send(target, "不能改投", guard));
        verify(sender).sendWithFuture();
        verify(users, never()).getOrCreate(any(), any());
        verifyNoInteractions(scanner);
    }
}
