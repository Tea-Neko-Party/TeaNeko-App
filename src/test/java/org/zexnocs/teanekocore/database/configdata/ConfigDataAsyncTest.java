package org.zexnocs.teanekocore.database.configdata;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.zexnocs.teanekocore.actuator.task.EmptyTaskResult;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.TaskResult;
import org.zexnocs.teanekocore.actuator.task.exception.TaskUnsuccessfulException;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.database.configdata.api.IConfigData;
import org.zexnocs.teanekocore.database.configdata.api.IConfigFieldChecker;
import org.zexnocs.teanekocore.database.configdata.exception.ConfigDataNotFoundException;
import org.zexnocs.teanekocore.database.configdata.exception.ConfigFieldCheckerFailureException;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataGetService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataDto;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataDtoTaskConfig;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 验证配置注册、注销和字段写入的真实完成链及提交前校验。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class ConfigDataAsyncTest {
    /**
     * 供字段和列表写入测试使用的最小配置数据。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public static class Settings implements IConfigData {
        public int limit = 1;
        public List<String> names = new ArrayList<>(List.of("first", "second"));
    }

    /**
     * 没有默认构造器的非法配置夹具，用于提交前拒绝验证。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public static class NoDefaultConstructor implements IConfigData {
        /**
         * 通过构造参数固定当前实例的依赖与初始状态，避免执行时再从全局环境查找协作者。
         *
         * @param value 待处理的值
         */
        public NoDefaultConstructor(String value) {}
    }

    private final ConfigManager manager = mock(ConfigManager.class);
    private final ILogger logger = mock(ILogger.class);
    private final IEasyDataDto dto = mock(IEasyDataDto.class);
    private final IEasyDataDtoTaskConfig task = mock(IEasyDataDtoTaskConfig.class, RETURNS_SELF);
    private final CompletableFuture<ITaskResult<Void>> result = new CompletableFuture<>();
    private final ConfigDataRegisterService registration = spy(new ConfigDataRegisterService(logger));
    private final IConfigDataGetService reader = mock(IConfigDataGetService.class);
    private final IConfigFieldCheckerScanner checkers = mock(IConfigFieldCheckerScanner.class);

    /**
     * 通过构造参数固定当前实例的依赖与初始状态，避免执行时再从全局环境查找协作者。
     */
    ConfigDataAsyncTest() {
        when(manager.value()).thenReturn("test-config");
        doReturn(Settings.class).when(manager).configType();
        doReturn(IConfigFieldChecker.class).when(manager).fieldChecker();
    }

    /**
     * 校验目标配置与字段并构造尚未提交的写任务，供同步便捷和异步入口复用；此步骤不报告持久化成功。
     *
     * @return 满足本方法约束的 {@code ConfigDataSetService} 结果
     */
    private ConfigDataSetService prepare() {
        doReturn(dto).when(registration).getDto(manager);
        when(dto.getTaskConfig(anyString())).thenReturn(task);
        when(task.getName()).thenReturn("configuration mutation");
        when(task.pushWithFuture()).thenReturn(new TaskFuture<>(logger, "configuration mutation", result));
        when(task.pushAsync()).thenCallRealMethod();
        when(reader.getConfigData(manager, "scope")).thenAnswer(ignored -> Optional.of(new Settings()));
        return new ConfigDataSetService(reader, logger, new ObjectMapper(), checkers, registration);
    }

    /**
     * 按用例选择配置字段或列表写入口，统一验证提交时序与失败传播。
     *
     * @param service 由装配方提供的 {@code ConfigDataSetService} 协作依赖
     * @param action 需要运行的处理回调
     * @return 等待数据库提交与缓存更新结束的阶段；校验或提交失败继续传播
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     */
    private CompletionStage<Void> update(ConfigDataSetService service, String action) throws Exception {
        return switch (action) {
            case "set" -> service.setRuleConfigFieldAsync(manager, "scope", "limit", "7");
            case "add" -> service.addToRuleConfigListFiledAsync(manager, "scope", "names", "third");
            case "remove" -> service.removeFromRuleConfigListFiledAsync(manager, "scope", "names", 0);
            case "clear" -> service.clearRuleConfigListFiledAsync(manager, "scope", "names");
            default -> throw new IllegalArgumentException(action);
        };
    }

    /**
     * 验证配置字段更新等待提交后才进入后续处理。
     *
     * @param action 需要运行的处理回调
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     */
    @ParameterizedTest
    @ValueSource(strings = {"set", "add", "remove", "clear"})
    void fieldMutationsWaitForCommitBeforeContinuation(String action) throws Exception {
        var service = prepare();
        var stage = update(service, action);
        var continued = new ArrayList<String>();
        stage.thenRun(() -> continued.add("committed"));
        assertFalse(stage.toCompletableFuture().isDone());
        assertTrue(continued.isEmpty());
        verify(task, never()).push();
        var value = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(task).set(eq("scope"), value.capture());
        var settings = assertInstanceOf(Settings.class, value.getValue());
        switch (action) {
            case "set" -> assertEquals(7, settings.limit);
            case "add" -> assertEquals(List.of("first", "second", "third"), settings.names);
            case "remove" -> assertEquals(List.of("second"), settings.names);
            case "clear" -> assertTrue(settings.names.isEmpty());
        }

        result.complete(EmptyTaskResult.INSTANCE);

        assertNull(stage.toCompletableFuture().join());
        assertEquals(List.of("committed"), continued);
        verifyNoInteractions(logger);
    }

    /**
     * 验证字段写入失败不会产生成功确认。
     *
     * @param action 需要运行的处理回调
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     */
    @ParameterizedTest
    @ValueSource(strings = {"set", "add", "remove", "clear"})
    void failedFieldMutationDoesNotProduceSuccessConfirmation(String action) throws Exception {
        var service = prepare();
        var confirmations = new ArrayList<String>();
        var stage = update(service, action).thenRun(() -> confirmations.add("success"));
        result.complete(new TaskResult<>(false, null));

        var failure = assertThrows(CompletionException.class, () -> stage.toCompletableFuture().join());

        assertInstanceOf(TaskUnsuccessfulException.class, failure.getCause());
        assertTrue(confirmations.isEmpty());
        verifyNoInteractions(logger);
    }

    /**
     * 验证配置注册等待实际数据库提交。
     */
    @Test
    void registeringConfigurationWaitsForDatabaseCommit() {
        prepare();
        var stage = registration.registerConfigAsync(manager, "scope");
        assertFalse(stage.toCompletableFuture().isDone());
        verify(task).set(eq("scope"), isA(Settings.class));

        result.complete(EmptyTaskResult.INSTANCE);

        assertNull(stage.toCompletableFuture().join());
        verify(task, never()).push();
    }

    /**
     * 验证删除已有配置只在提交成功后返回 true。
     */
    @Test
    void removingExistingConfigurationOnlyReturnsTrueAfterCommit() {
        prepare();
        when(dto.has("scope")).thenReturn(true);
        var stage = registration.unregisterConfigAsync(manager, "scope");
        assertFalse(stage.toCompletableFuture().isDone());
        verify(task).remove("scope");

        result.complete(EmptyTaskResult.INSTANCE);

        assertTrue(stage.toCompletableFuture().join());
        verify(task, never()).push();
    }

    /**
     * 验证配置不存在时返回 false 且不提交删除。
     */
    @Test
    void missingConfigurationReturnsFalseWithoutSubmittingDeletion() {
        prepare();
        assertFalse(registration.unregisterConfigAsync(manager, "scope").toCompletableFuture().join());
        verify(dto, never()).getTaskConfig(anyString());
        verify(task, never()).pushWithFuture();
    }

    /**
     * 验证注册或注销失败保留原始异常。
     *
     * @param remove 用于本次测试的 {@code remove} 数据
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void failedRegistrationOrRemovalPropagatesTheOriginalException(boolean remove) {
        prepare();
        when(dto.has("scope")).thenReturn(true);
        var stage = remove ? registration.unregisterConfigAsync(manager, "scope")
                : registration.registerConfigAsync(manager, "scope");
        var expected = new IllegalStateException("commit failed");
        result.completeExceptionally(expected);

        assertSame(expected, assertThrows(CompletionException.class,
                () -> stage.toCompletableFuture().join()).getCause());
        verifyNoInteractions(logger);
    }

    /**
     * 验证配置构造器非法时在提交任何写任务前失败。
     */
    @Test
    void invalidConfigurationConstructorFailsBeforeSubmittingAnyTask() {
        var annotation = mock(ConfigManager.class);
        when(annotation.value()).thenReturn("bad-config");
        doReturn(NoDefaultConstructor.class).when(annotation).configType();
        assertThrows(IllegalArgumentException.class, () -> registration.registerConfigAsync(annotation, "scope"));
        verify(registration, never()).getDto(any());
    }

    /**
     * 验证缺少配置或非法字段不会提交写入。
     */
    @Test
    void missingConfigurationAndInvalidFieldDoNotSubmitWrites() {
        var service = prepare();
        when(reader.getConfigData(manager, "scope")).thenReturn(Optional.empty());
        assertThrows(ConfigDataNotFoundException.class,
                () -> service.setRuleConfigFieldAsync(manager, "scope", "limit", "7"));
        when(reader.getConfigData(manager, "scope")).thenReturn(Optional.of(new Settings()));
        assertThrows(NoSuchFieldException.class,
                () -> service.setRuleConfigFieldAsync(manager, "scope", "missing", "7"));
        assertThrows(IllegalArgumentException.class,
                () -> service.setRuleConfigFieldAsync(manager, "scope", "limit", "invalid"));
        verify(task, never()).pushWithFuture();
        verify(task, never()).push();
    }

    /**
     * 验证不可用或拒绝的字段检查器不能被绕过。
     */
    @Test
    void unavailableOrRejectingCheckerCannotBeBypassed() {
        var service = prepare();
        var unavailable = new IllegalStateException("checker unavailable");
        when(checkers.get(any())).thenThrow(unavailable);
        var error = assertThrows(IllegalStateException.class,
                () -> service.setRuleConfigFieldAsync(manager, "scope", "limit", "7"));
        assertSame(unavailable, error.getCause());

        var checker = mock(IConfigFieldChecker.class);
        doReturn(checker).when(checkers).get(any());
        when(checker.isValid("names", "third")).thenReturn("rejected");
        assertThrows(ConfigFieldCheckerFailureException.class,
                () -> service.addToRuleConfigListFiledAsync(manager, "scope", "names", "third"));
        verify(dto, never()).getTaskConfig(anyString());
    }

    /**
     * 验证配置读取失败不能成为成功写入阶段。
     */
    @Test
    void configurationLookupFailureCannotBecomeSuccessfulWriteStage() {
        var service = prepare();
        var expected = new IllegalArgumentException("invalid configuration declaration");
        when(reader.getConfigData(manager, "scope")).thenThrow(expected);

        assertSame(expected, assertThrows(IllegalArgumentException.class,
                () -> service.clearRuleConfigListFiledAsync(manager, "scope", "names")));
        verify(dto, never()).getTaskConfig(anyString());
    }
}
