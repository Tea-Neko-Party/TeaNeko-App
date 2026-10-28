package org.zexnocs.teanekoplugin.general.signin;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoapp.sender.api.sender_box.IEasyMessageSenderBuilder;
import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserCoinService;
import org.zexnocs.teanekocore.actuator.task.EmptyTaskResult;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.TaskResult;
import org.zexnocs.teanekocore.actuator.task.exception.TaskUnsuccessfulException;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerService;
import org.zexnocs.teanekocore.cache.interfaces.ICacheService;
import org.zexnocs.teanekocore.database.easydata.cleanable.CleanableEasyData;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataDto;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataDtoTaskConfig;
import org.zexnocs.teanekocore.database.easydata.general.GeneralEasyData;
import org.zexnocs.teanekocore.database.itemdata.interfaces.IItemDataDTO;
import org.zexnocs.teanekocore.database.itemdata.interfaces.IItemDataDtoTaskConfig;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.RandomUtil;
import org.zexnocs.teanekoplugin.general.signin.data.SignInChunkData;
import org.zexnocs.teanekoplugin.general.signin.data.SignInData;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 验证签到合并事务与反馈发送的先后顺序和异常传播。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class SignInCompletionTest {
    private final UUID user = UUID.randomUUID();
    private final CompletableFuture<ITaskResult<Void>> commit = new CompletableFuture<>();
    private final CompletableFuture<Void> reply = new CompletableFuture<>();
    private final IEasyMessageSenderBuilder sender = mock(IEasyMessageSenderBuilder.class);

    /**
     * 提供受控签到存储与静态工厂作用域，关闭后不影响其他用例。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private final class Storage implements AutoCloseable {
        final org.mockito.MockedStatic<GeneralEasyData> general = mockStatic(GeneralEasyData.class);
        final org.mockito.MockedStatic<CleanableEasyData> cleanable = mockStatic(CleanableEasyData.class);
        final SignInService service;

        /**
         * 通过构造参数固定当前实例的依赖与初始状态，避免执行时再从全局环境查找协作者。
         */
        Storage() {
            var generalStore = mock(GeneralEasyData.class);
            var recordStore = mock(CleanableEasyData.class);
            general.when(() -> GeneralEasyData.of(SignInService.NAMESPACE)).thenReturn(generalStore);
            cleanable.when(() -> CleanableEasyData.of(SignInService.NAMESPACE)).thenReturn(recordStore);
            var data = mock(IEasyDataDto.class);
            var record = mock(IEasyDataDto.class);
            when(generalStore.get(user.toString())).thenReturn(data);
            when(recordStore.get(user.toString())).thenReturn(record);
            doReturn(SignInData.builder().lastTime(Instant.EPOCH).totalDays(0).continuousDays(0).lastNumber(0).build())
                    .when(data).get(eq(SignInData.KEY), any(SignInData.class));
            when(data.getList(SignInChunkData.KEY, SignInChunkData.class)).thenReturn(List.of());
            when(data.getTaskConfig(anyString())).thenReturn(mock(IEasyDataDtoTaskConfig.class, RETURNS_SELF));
            when(record.getTaskConfig(anyString())).thenReturn(mock(IEasyDataDtoTaskConfig.class, RETURNS_SELF));
            var coins = mock(ITeaUserCoinService.class);
            var coin = mock(IItemDataDTO.class);
            var transaction = mock(IItemDataDtoTaskConfig.class, RETURNS_SELF);
            doReturn(coin).when(coins).getCoin(user);
            when(coin.getDatabaseTaskConfig(anyString())).thenReturn(transaction);
            when(transaction.getName()).thenReturn("sign-in transaction");
            when(transaction.pushWithFuture()).thenReturn(new TaskFuture<>(mock(ILogger.class), "sign-in transaction", commit));
            when(transaction.pushAsync()).thenCallRealMethod();
            when(sender.sendAtReplyMessageAsync(anyString())).thenReturn(reply);
            service = new SignInService(mock(ITimerService.class), mock(RandomUtil.class), coins, mock(ICacheService.class));
        }

        /**
         * 关闭当前静态存储替身作用域，避免测试配置影响后续用例。
         */
        @Override public void close() { cleanable.close(); general.close(); }
    }

    /**
     * 验证签到同时等待合并事务及成功回复。
     */
    @Test
    void signInWaitsForBothMergedTransactionAndSuccessReply() {
        try (var storage = new Storage()) {
            var result = storage.service.signIn(user, Instant.parse("2026-10-28T12:00:00Z"), sender);
            assertFalse(result.toCompletableFuture().isDone());
            verifyNoInteractions(sender);
            commit.complete(EmptyTaskResult.INSTANCE);
            verify(sender).sendAtReplyMessageAsync(contains("签到成功"));
            assertFalse(result.toCompletableFuture().isDone());
            reply.complete(null);
            assertNull(result.toCompletableFuture().join());
        }
    }

    /**
     * 验证事务失败不能发送签到成功消息。
     */
    @Test
    void failedTransactionCannotSendSuccessfulSignInMessage() {
        try (var storage = new Storage()) {
            var result = storage.service.signIn(user, Instant.parse("2026-10-28T12:00:00Z"), sender);
            commit.complete(new TaskResult<>(false, null));
            assertInstanceOf(TaskUnsuccessfulException.class, assertThrows(CompletionException.class,
                    () -> result.toCompletableFuture().join()).getCause());
            verifyNoInteractions(sender);
        }
    }

    /**
     * 验证事务异常到达调用者且不发送成功反馈。
     */
    @Test
    void transactionExceptionReachesCallerWithoutSendingSuccess() {
        try (var storage = new Storage()) {
            var result = storage.service.signIn(user, Instant.parse("2026-10-28T12:00:00Z"), sender);
            var expected = new IllegalArgumentException("transaction failed");
            commit.completeExceptionally(expected);
            assertSame(expected, TaskFuture.unwrapException(assertThrows(CompletionException.class,
                    () -> result.toCompletableFuture().join())));
            verifyNoInteractions(sender);
        }
    }
}
