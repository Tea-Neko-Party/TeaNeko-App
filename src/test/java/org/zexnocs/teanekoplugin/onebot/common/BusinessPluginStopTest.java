package org.zexnocs.teanekoplugin.onebot.common;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContent;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.message.api.content.IReplyTeaNekoContentPart;
import org.zexnocs.teanekoapp.sender.api.sender_box.IEasyMessageSenderBuilder;
import org.zexnocs.teanekoclient.onebot.core.OnebotDebuggerService;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotMessageData;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotRawMessageData;
import org.zexnocs.teanekoclient.onebot.data.response.params.StrangerInfoGetResponseData;
import org.zexnocs.teanekoclient.onebot.sender.group.GroupBanSender;
import org.zexnocs.teanekoclient.onebot.sender.message.DeleteMessageSender;
import org.zexnocs.teanekoclient.onebot.sender.message.OnebotGetMsgSender;
import org.zexnocs.teanekoclient.onebot.sender.private_.StrangerInfoGetSender;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.TaskResult;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.plugin.IPluginModule;
import org.zexnocs.teanekocore.plugin.PluginAccess;
import org.zexnocs.teanekocore.plugin.PluginDescriptor;
import org.zexnocs.teanekocore.plugin.PluginRegistry;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 用可控平台查询和发送阶段验证业务插件关闭后不再发起新的外部动作。
 * <br>2026/11/07：覆盖资料查询迟到、消息撤回迟到及反馈之后的禁言续接。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
class BusinessPluginStopTest {
    /** 查询进行中关闭并重新开启插件，旧查询不能触发回复。 */
    @Test
    void restartedPluginCannotDeliverLateStrangerQuery() {
        var plugins = registry();
        var query = new CompletableFuture<StrangerInfoGetResponseData>();
        var stranger = mock(StrangerInfoGetSender.class);
        when(stranger.getPlatformUserInfo("42")).thenReturn(new TaskFuture<>(mock(ILogger.class), "资料查询", query));
        var sender = mock(IEasyMessageSenderBuilder.class);
        var data = mock(OnebotMessageData.class);
        when(data.getMessageSender()).thenReturn(sender);
        var command = new StrangerGetCommand(stranger, new PluginAccess(plugins));
        var result = command.execute(CommandData.<OnebotMessageData>builder().rawData(data).build(), 42);
        plugins.setEnabled("onebot.common", false);
        plugins.setEnabled("onebot.common", true);
        query.complete(null);
        var failure = assertThrows(ExecutionException.class,
                () -> result.toCompletableFuture().get(2, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, failure.getCause());
        verifyNoInteractions(sender);
    }

    /** 关闭时尚未结束的消息查询不能继续撤回消息或发送失败提示。 */
    @Test
    void disabledPluginCannotDeleteAfterLateMessageQuery() {
        var plugins = registry();
        var query = new CompletableFuture<ITaskResult<ITeaNekoMessageData>>();
        var reading = mock(OnebotGetMsgSender.class);
        when(reading.getMsg("17")).thenReturn(new TaskFuture<>(mock(ILogger.class), "消息查询", query));
        var delete = mock(DeleteMessageSender.class);
        var ban = mock(GroupBanSender.class);
        var sender = mock(IEasyMessageSenderBuilder.class);
        var command = new MessageDeleteCommand(delete, reading, ban, mock(OnebotDebuggerService.class),
                new PluginAccess(plugins));
        var result = command.delete(input(sender));
        plugins.setEnabled("onebot.common", false);
        query.complete(new TaskResult<>(false, null));
        assertThrows(ExecutionException.class, () -> result.toCompletableFuture().get(2, TimeUnit.SECONDS));
        verifyNoInteractions(delete, ban, sender);
    }

    /** 前一步平台反馈已开始时，停用不会取消既有发送，但阻止后续禁言。 */
    @Test
    void disableDuringFeedbackPreventsFollowingBan() {
        var plugins = registry();
        var response = mock(ITeaNekoMessageData.class, RETURNS_DEEP_STUBS);
        when(response.getUserData().getUserIdInPlatform()).thenReturn("99");
        var reading = mock(OnebotGetMsgSender.class);
        when(reading.getMsg("17")).thenReturn(TaskFuture.fromStage(mock(ILogger.class), "消息查询",
                CompletableFuture.completedStage(new TaskResult<>(true, response))));
        var debug = mock(OnebotDebuggerService.class);
        when(debug.isDebugger(99)).thenReturn(true);
        var feedback = new CompletableFuture<Void>();
        var sender = mock(IEasyMessageSenderBuilder.class);
        when(sender.sendAtReplyMessageAsync(anyString())).thenReturn(feedback);
        var delete = mock(DeleteMessageSender.class);
        var ban = mock(GroupBanSender.class);
        var command = new MessageDeleteCommand(delete, reading, ban, debug, new PluginAccess(plugins));
        var result = command.delete(input(sender));
        plugins.setEnabled("onebot.common", false);
        plugins.setEnabled("onebot.common", true);
        feedback.complete(null);
        assertThrows(ExecutionException.class, () -> result.toCompletableFuture().get(2, TimeUnit.SECONDS));
        verifyNoInteractions(delete, ban);
    }

    /**
     * 只装配被测插件，生产依赖和平台网络由各自契约测试覆盖。
     * @return 可真实启停和换代的目录
     */
    private static PluginRegistry registry() {
        IPluginModule module = () -> new PluginDescriptor("onebot.common", "4.9.0-l", 1, Set.of());
        return new PluginRegistry(List.of(module));
    }

    /**
     * 创建携带引用目标的命令数据，发送器只作为受控外部边界使用。
     * @param sender 反馈发送端口
     * @return 引用消息 17 的真实命令数据形状
     */
    private static CommandData<OnebotMessageData> input(IEasyMessageSenderBuilder sender) {
        var reply = mock(IReplyTeaNekoContentPart.class);
        when(reply.getId()).thenReturn("17");
        var content = mock(ITeaNekoContent.class);
        when(content.getContentPart()).thenReturn(reply);
        var data = mock(OnebotMessageData.class);
        when(data.getMessages()).thenAnswer(ignored -> List.of(content));
        when(data.getMessageSender()).thenReturn(sender);
        when(data.getOnebotRawMessageData()).thenReturn(mock(OnebotRawMessageData.class));
        return CommandData.<OnebotMessageData>builder().rawData(data).build();
    }
}
