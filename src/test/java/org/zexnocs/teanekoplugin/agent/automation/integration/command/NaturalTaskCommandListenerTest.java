package org.zexnocs.teanekoplugin.agent.automation.integration.command;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoagent.integration.command.AgentChatCommandEvent;
import org.zexnocs.teanekoagent.integration.command.AgentCommand;
import org.zexnocs.teanekoagent.integration.command.AgentCommandGateway;
import org.zexnocs.teanekoapp.message.TeaNekoContent;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.message.api.content.ITextTeaNekoContentPart;
import org.zexnocs.teanekoapp.sender.api.sender_box.IEasyMessageSenderBuilder;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.event.interfaces.IEventService;
import org.zexnocs.teanekocore.plugin.PluginAccess;
import org.zexnocs.teanekocore.plugin.PluginDescriptor;
import org.zexnocs.teanekocore.plugin.PluginRegistry;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 验证原生正文扩展、普通聊天回落和插件停启后的迟到反馈隔离。
 * <br>2026/11/07：通过真实命令入口和插件监听器覆盖自然任务接线，不连接模型或客户端。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
class NaturalTaskCommandListenerTest {
    /**
     * 普通正文不被自动化认领，并保留用户原始内部空白。
     * @throws Exception 有限等待失败时
     */
    @Test
    void ordinaryTextFallsBackWithoutChangingWhitespace() throws Exception {
        var fixture = new Fixture("你好  世界");
        when(fixture.tasks.natural(fixture.source, "你好  世界")).thenReturn(Optional.empty());
        when(fixture.gateway.chat(fixture.source, "你好  世界", null))
                .thenReturn(CompletableFuture.completedStage(null));
        fixture.command.chat(fixture.input, List.of("你好", "世界"))
                .toCompletableFuture().get(5, TimeUnit.SECONDS);
        verify(fixture.gateway).chat(fixture.source, "你好  世界", null);
        verifyNoInteractions(fixture.sender);
    }

    /**
     * 命令结果包含业务完成和实际反馈发送，不能在业务认领后提前完成。
     * @throws Exception 有限等待失败时
     */
    @Test
    void waitsForTaskAndActualFeedback() throws Exception {
        var fixture = new Fixture("10分钟后提醒我喝水");
        var work = new CompletableFuture<String>();
        var sent = new CompletableFuture<Void>();
        when(fixture.tasks.natural(fixture.source, "10分钟后提醒我喝水")).thenReturn(Optional.of(work));
        when(fixture.sender.sendReplyMessageAsync("提醒已创建")).thenReturn(sent);
        var result = fixture.command.chat(fixture.input, List.of("10分钟后提醒我喝水")).toCompletableFuture();
        assertFalse(result.isDone());
        verifyNoInteractions(fixture.sender, fixture.gateway);
        work.complete("提醒已创建");
        verify(fixture.sender).sendReplyMessageAsync("提醒已创建");
        assertFalse(result.isDone());
        sent.complete(null);
        result.get(5, TimeUnit.SECONDS);
        verifyNoInteractions(fixture.gateway);
    }

    /**
     * 关闭再开启也不能使旧代次结果恢复发送资格，异常映射不能绕过此边界。
     * @throws Exception 有限等待失败时
     */
    @Test
    void rejectsLateFeedbackAfterDisableAndEnable() throws Exception {
        var fixture = new Fixture("10分钟后提醒我喝水");
        var work = new CompletableFuture<String>();
        when(fixture.tasks.natural(any(), anyString())).thenReturn(Optional.of(work));
        var result = fixture.command.chat(fixture.input, List.of("10分钟后提醒我喝水")).toCompletableFuture();
        fixture.plugins.setEnabled("agent.automation", false);
        fixture.plugins.setEnabled("agent.automation", true);
        work.completeExceptionally(new IllegalStateException("旧任务已失效。"));
        assertThrows(ExecutionException.class, () -> result.get(5, TimeUnit.SECONDS));
        verifyNoInteractions(fixture.sender, fixture.gateway);
    }

    /**
     * 派生调用只使用显式参数，并保留父控制，不进入自然任务认领。
     * @throws Exception 有限等待失败时
     */
    @Test
    void derivedCommandKeepsParentAndSkipsNaturalTask() throws Exception {
        var fixture = new Fixture("不可复用的原文");
        try (var parent = ExecutionControl.create()) {
            var input = CommandData.<ITeaNekoMessageData>builder().body("/agent")
                    .rawData(fixture.source).parentExecution(parent).build();
            when(fixture.gateway.chat(fixture.source, "10分钟后 提醒", parent))
                    .thenReturn(CompletableFuture.completedStage(null));
            fixture.command.chat(input, List.of("10分钟后", "提醒")).toCompletableFuture().get(5, TimeUnit.SECONDS);
            verify(fixture.gateway).chat(fixture.source, "10分钟后 提醒", parent);
            verifyNoInteractions(fixture.events, fixture.tasks, fixture.sender);
        }
    }

    /**
     * 隔离外部设施的命令夹具，事件通知仍调用真实插件监听并等待登记阶段。
     *
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    private static final class Fixture {
        private final ITeaNekoMessageData source = mock(ITeaNekoMessageData.class);
        private final IEasyMessageSenderBuilder sender = mock(IEasyMessageSenderBuilder.class);
        private final IAgentTaskCommands tasks = mock(IAgentTaskCommands.class);
        private final AgentCommandGateway gateway = mock(AgentCommandGateway.class);
        private final IEventService events = mock(IEventService.class);
        private final PluginRegistry plugins = new PluginRegistry(List.of(
                () -> new PluginDescriptor("agent.automation", "1", 1, Set.of())));
        private final AgentCommand command = new AgentCommand(gateway, events, null);
        private final CommandData<ITeaNekoMessageData> input;

        /**
         * 准备原文和同步事件通知，业务阶段由测试显式完成。
         * @param original 用户正文
         */
        private Fixture(String original) {
            var part = mock(ITextTeaNekoContentPart.class);
            when(part.getText()).thenReturn("/agent chat " + original);
            doReturn(List.of(new TeaNekoContent("text", part))).when(source).getMessages();
            when(source.getMessageSender()).thenReturn(sender);
            input = CommandData.<ITeaNekoMessageData>builder().body("/agent").rawData(source).build();
            var listener = new NaturalTaskCommandListener(tasks, new PluginAccess(plugins));
            when(events.pushEventWithCompletion(any(AgentChatCommandEvent.class))).thenAnswer(call -> {
                var event = call.<AgentChatCommandEvent>getArgument(0);
                listener.onChat(event);
                return event.processingCompletion();
            });
        }
    }
}
