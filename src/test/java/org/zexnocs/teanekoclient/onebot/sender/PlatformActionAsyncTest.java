package org.zexnocs.teanekoclient.onebot.sender;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.zexnocs.teanekoapp.sender.interfaces.ISenderService;
import org.zexnocs.teanekoclient.onebot.core.OnebotClient;
import org.zexnocs.teanekoclient.onebot.sender.group.GroupAddRequestSender;
import org.zexnocs.teanekoclient.onebot.sender.group.GroupBanSender;
import org.zexnocs.teanekoclient.onebot.sender.group.GroupKickSender;
import org.zexnocs.teanekoclient.onebot.sender.group.SetGroupSpecialTitleSender;
import org.zexnocs.teanekoclient.onebot.sender.message.DeleteMessageSender;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.TaskResult;
import org.zexnocs.teanekocore.actuator.task.exception.TaskUnsuccessfulException;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.event.interfaces.IEvent;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * 验证 OneBot 管理动作等待真实回执，不把失败或空响应报告为成功。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class PlatformActionAsyncTest {
    private final ISenderService sender = mock(ISenderService.class);
    private final OnebotClient client = mock(OnebotClient.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final ILogger logger = mock(ILogger.class);
    private final CompletableFuture<ITaskResult<List<Map>>> response = new CompletableFuture<>();

    /**
     * 按用例参数选择对应平台动作，统一核对其回执等待和失败传播。
     *
     * @param action 需要运行的处理回调
     * @return 本用例受控的完成阶段，可由测试推进成功、拒绝或异常
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     */
    private CompletionStage<Void> action(String action) {
        doReturn(new TaskFuture<>(logger, "action", response)).when(sender)
                .send(any(IEvent.class), any(Duration.class), anyInt(), any(Duration.class));
        return switch (action) {
            case "ban" -> new GroupBanSender(sender, client, mapper).banAsync(1, 2, 60);
            case "kick" -> new GroupKickSender(sender, client, mapper).kickAsync("1", "2");
            case "delete" -> new DeleteMessageSender(sender, client, mapper).deleteAsync(3);
            case "title" -> new SetGroupSpecialTitleSender(sender, client, mapper).setGroupSpecialTitleAsync(1, 2, "猫");
            case "approve" -> new GroupAddRequestSender(sender, client, mapper).approveAsync("flag");
            case "reject" -> new GroupAddRequestSender(sender, client, mapper).rejectAsync("flag", "reason");
            default -> throw new IllegalArgumentException(action);
        };
    }

    /**
     * 验证平台管理动作等待真实回执后完成。
     *
     * @param action 需要运行的处理回调
     */
    @ParameterizedTest
    @ValueSource(strings = {"ban", "kick", "delete", "title", "approve", "reject"})
    void platformActionsWaitForTheRealResponse(String action) {
        var result = action(action);
        assertFalse(result.toCompletableFuture().isDone());
        response.complete(new TaskResult<>(true, List.of(Map.of())));
        assertNull(result.toCompletableFuture().join());
        verifyNoInteractions(logger);
    }

    /**
     * 验证底层失败状态不能转换为成功的 Void。
     *
     * @param action 需要运行的处理回调
     */
    @ParameterizedTest
    @ValueSource(strings = {"ban", "kick", "delete", "title", "approve", "reject"})
    void reportedFailureDoesNotBecomeSuccessfulVoid(String action) {
        var result = action(action);
        response.complete(new TaskResult<>(false, List.of()));
        assertInstanceOf(TaskUnsuccessfulException.class, assertThrows(CompletionException.class,
                () -> result.toCompletableFuture().join()).getCause());
    }

    /**
     * 验证取消动作或缺失回执不能被报告为成功。
     *
     * @param action 需要运行的处理回调
     */
    @ParameterizedTest
    @ValueSource(strings = {"ban", "kick", "delete", "title", "approve", "reject"})
    void cancelledActionWithNoResponseDoesNotClaimSuccess(String action) {
        var result = action(action);
        response.complete(new TaskResult<>(true, null));
        assertInstanceOf(NullPointerException.class, assertThrows(CompletionException.class,
                () -> result.toCompletableFuture().join()).getCause());
    }
}
