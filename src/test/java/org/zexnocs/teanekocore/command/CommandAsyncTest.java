package org.zexnocs.teanekocore.command;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskConfig;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskService;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.command.easydata.CommandEasyData;
import org.zexnocs.teanekocore.command.event.CommandDispatchEvent;
import org.zexnocs.teanekocore.command.event.CommandExecuteEvent;
import org.zexnocs.teanekocore.command.interfaces.*;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataDto;
import org.zexnocs.teanekocore.event.EventService;
import org.zexnocs.teanekocore.event.core.EventHandler;
import org.zexnocs.teanekocore.event.core.EventHandlerScanner;
import org.zexnocs.teanekocore.event.interfaces.IEvent;
import org.zexnocs.teanekocore.framework.pair.HashPair;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.zexnocs.teanekocore.command.CommandResult.Status.*;

/**
 * 使用真实参数处理、扫描、分发、执行和事件服务；仅替换任务调度与外部权限存储。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public class CommandAsyncTest {
    /**
     * 命令声明允许使用的客户端标记，用于原客户端校验。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public static class AllowedClient implements ICommandClient {}
    /**
     * 未被命令声明支持的客户端标记，用于拒绝分支验证。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public static class OtherClient implements ICommandClient {}
    /**
     * 用于命令参数注入的基础可信来源数据。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public static class Input {}
    /**
     * 用于验证原始输入类型继承与实例保持的派生数据。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public static class DerivedInput extends Input {}

    // 非 static 的测试类不会成为 Spring 自动扫描候选。
    /**
     * 提供受控异步方法与非法返回分支的命令夹具。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    @Command(value = {"/async", "/alias"}, permission = CommandPermission.ADMIN, scope = CommandScope.GROUP,
            supportedClients = AllowedClient.class, taskNamespace = "test-command-stage")
    public class Handler {
        final CompletableFuture<String> business = new CompletableFuture<>();
        int calls;
        int number;
        List<String> tail;
        CommandData<?> input;

        /**
         * 记录可信输入与绑定参数，返回由用例主动完成的业务阶段。
         *
         * @param data 本次处理的输入数据
         * @param count 用于本次测试的 {@code count} 数据
         * @param values 本用例当前定位的暂存数据
         * @return 本用例受控的完成阶段，可由测试推进成功、拒绝或异常
         */
        @DefaultCommand
        public CompletionStage<String> run(CommandData<Input> data, @DefaultValue("7") int count, List<String> values) {
            calls++;
            input = data;
            number = count;
            tail = values;
            return business.minimalCompletionStage();
        }

        /**
         * 提供别名子命令入口，复用受控业务阶段以验证参数与事件链。
         *
         * @param data 本次处理的输入数据
         * @return 本用例受控的完成阶段，可由测试推进成功、拒绝或异常
         */
        @SubCommand({"sub", "s"})
        public CompletionStage<String> sub(CommandData<Input> data) { calls++; input = data; return business; }

        /**
         * 主动抛出同步业务异常，验证反射包装能够正确解开。
         *
         * @return 本用例受控的完成阶段，可由测试推进成功、拒绝或异常
         * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
         */
        @SubCommand("throw")
        public CompletionStage<Void> throwing() { throw new IllegalArgumentException("synchronous business failure"); }

        /**
         * 故意返回 null 完成阶段，验证非法命令实现会明确失败。
         *
         * @return 本用例受控的完成阶段，可由测试推进成功、拒绝或异常
         */
        @SubCommand("null")
        public CompletionStage<Void> missingStage() { return null; }

        /**
         * 提供必需参数入口，供参数不足时不调用处理方法的断言使用。
         *
         * @param value 待处理的值
         * @return 本用例受控的完成阶段，可由测试推进成功、拒绝或异常
         */
        @SubCommand("required")
        public CompletionStage<String> required(int value) { calls++; return business; }
    }

    /**
     * 用于验证正则命令绑定规则的独立夹具。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    @Command(value = "echo \\d+", mode = Command.CommandMode.REGEX, scope = CommandScope.ALL)
    public class RegexHandler {
        /**
         * 提供受控正则命令入口，验证其不误用默认参数绑定路径。
         *
         * @return 本用例受控的完成阶段，可由测试推进成功、拒绝或异常
         */
        @DefaultCommand public CompletionStage<String> run() { return CompletableFuture.completedStage("regex result"); }
    }

    /**
     * 用于验证已关闭命令不会进入业务方法的夹具。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    @Command(value = "/closed", enable = false)
    public class ClosedHandler {
        /**
         * 作为关闭命令的保护夹具；正常分发不应进入本方法。
         *
         * @return 本用例受控的完成阶段，可由测试推进成功、拒绝或异常
         */
        @DefaultCommand public CompletionStage<Void> run() { fail("disabled command invoked"); return null; }
    }

    /**
     * 没有默认处理方法的子命令夹具。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    @Command(value = "/sub-only")
    public class SubOnly {
        /**
         * 提供只有子命令的夹具，用于验证缺少默认处理方法的终止回执。
         *
         * @return 本用例受控的完成阶段，可由测试推进成功、拒绝或异常
         */
        @SubCommand("known") public CompletionStage<Void> run() { return CompletableFuture.completedStage(null); }
    }

    /**
     * 装配真实事件、扫描、参数、分发及执行服务的离线命令夹具。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private class Fixture {
        final ILogger logger = mock(ILogger.class);
        final TaskRunner tasks = new TaskRunner(logger);
        final ICommandPermissionManager permissions = mock(ICommandPermissionManager.class);
        final ICommandScopeManager scopes = mock(ICommandScopeManager.class);
        final Handler handler = new Handler();
        final CommandExecutor executor = new CommandExecutor(tasks.service);
        final CommandScanner scanner;
        final EventService events;
        final CommandDispatcher dispatcher;
        Consumer<IEvent<?>> listener = event -> {};

        /**
         * 通过构造参数固定当前实例的依赖与初始状态，避免执行时再从全局环境查找协作者。
         *
         * @throws Exception 当底层业务、反射调用或结果转换失败时
         */
        @SuppressWarnings({"unchecked"})
        Fixture() throws Exception {
            var beans = mock(IBeanScanner.class);
            var registry = new HashMap<String, org.zexnocs.teanekocore.framework.pair.Pair<Command, Object>>();
            for (Object bean : List.of(handler, new RegexHandler(), new ClosedHandler(), new SubOnly())) {
                registry.put(bean.getClass().getName(), HashPair.of(bean.getClass().getAnnotation(Command.class), bean));
            }
            doReturn(registry).when(beans).getBeansWithAnnotation(Command.class);
            when(beans.getBeanClass(any())).thenAnswer(invocation -> invocation.getArgument(0).getClass());
            scanner = new CommandScanner(logger, beans);
            scanner._scan();
            when(scopes.inScope(any(Command.class), any(DefaultCommand.class), any())).thenReturn(true);
            when(scopes.inScope(any(Command.class), any(SubCommand.class), any())).thenReturn(true);
            when(permissions.hasPermission(any(Command.class), any(DefaultCommand.class), any())).thenReturn(true);
            when(permissions.hasPermission(any(Command.class), any(SubCommand.class), any())).thenReturn(true);

            var eventScanner = mock(EventHandlerScanner.class);
            var patch = mock(EventHandlerScanner.EventHandlerPatch.class);
            var annotation = mock(EventHandler.class);
            when(patch.getAnnotation()).thenReturn(annotation); // 同步处理，保持原 EventService 行为。
            doAnswer(invocation -> { listener.accept(invocation.getArgument(0)); return null; }).when(patch).invoke(any());
            doReturn(List.of(patch)).when(eventScanner).getEventHandlerList(any());
            events = new EventService(logger, eventScanner, tasks.service);
            dispatcher = new CommandDispatcher(logger, permissions, scopes, executor,
                    new CommandArgumentProcessor(logger), scanner, events);
        }

        /**
         * 通过真实分发器提交受控命令输入，供事件、权限与异步完成链验证。
         *
         * @param body 原始命令文本
         * @param args 待绑定的命令参数
         * @return 包含分发状态与实际处理结果的完成阶段；反馈或业务失败以异常完成
         */
        CompletionStage<CommandResult> dispatch(String body, String... args) {
            return dispatcher.dispatch(data(body, new DerivedInput(), args), null, null);
        }
    }

    /**
     * 构造固定可信来源与原始参数的命令数据，保持派生类型用于绑定测试。
     *
     * @param body 原始命令文本
     * @param raw 未替换可信来源的原始输入
     * @param args 待绑定的命令参数
     * @return 满足本方法约束的 {@code CommandData<?>} 结果
     */
    private CommandData<?> data(String body, Object raw, String... args) {
        return CommandData.builder().body(body).args(args).scope(CommandScope.GROUP).scopeId("trusted-scope")
                .senderId("trusted-user").permission(CommandPermission.ADMIN).clientClass(AllowedClient.class).rawData(raw).build();
    }

    /**
     * 验证命令分发等待业务完成，保留返回值和可信输入。
     *
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     */
    @Test
    void dispatchWaitsForBusinessStageAndPreservesItsValueAndTrustedInput() throws Exception {
        var f = new Fixture();
        var input = data("/alias", new DerivedInput(), "9", "first", "second");
        var result = f.dispatcher.dispatch(input, null, null);
        assertFalse(result.toCompletableFuture().isDone());
        assertEquals(0, f.handler.calls);
        f.tasks.runAll();
        assertEquals(1, f.handler.calls);
        assertSame(input, f.handler.input);
        assertEquals(9, f.handler.number);
        assertEquals(List.of("first", "second"), f.handler.tail);
        assertTrue(f.tasks.namespaces.contains("test-command-stage"));
        assertFalse(result.toCompletableFuture().isDone());

        f.handler.business.complete("actual business value");

        assertEquals(new CommandResult(COMPLETED, "actual business value"), result.toCompletableFuture().join());
    }

    /**
     * 验证参数默认值与子命令别名保持既有语义。
     *
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     */
    @Test
    void parameterDefaultsAndSubcommandAliasesStillWork() throws Exception {
        var first = new Fixture();
        var defaults = first.dispatch("/async");
        first.tasks.runAll();
        assertEquals(7, first.handler.number);
        assertTrue(first.handler.tail.isEmpty());
        first.handler.business.complete("default");
        assertEquals("default", defaults.toCompletableFuture().join().value());

        var second = new Fixture();
        var sub = second.dispatch("/async", "s");
        second.tasks.runAll();
        assertFalse(sub.toCompletableFuture().isDone());
        second.handler.business.complete("sub");
        assertEquals("sub", sub.toCompletableFuture().join().value());
        verify(second.permissions).hasPermission(any(Command.class), any(SubCommand.class), same(second.handler.input));
    }

    /**
     * 验证处理方法异步失败到达命令分发调用者。
     *
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     */
    @Test
    void asynchronousHandlerFailureReachesDispatchCaller() throws Exception {
        var f = new Fixture();
        var result = f.dispatch("/async");
        f.tasks.runAll();
        var expected = new IllegalStateException("asynchronous failure");
        f.handler.business.completeExceptionally(expected);
        assertSame(expected, failure(result));
    }

    /**
     * 验证同步反射异常被解包，null 阶段被拒绝。
     *
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     */
    @Test
    void synchronousFailureIsUnwrappedAndNullStageIsRejected() throws Exception {
        var f = new Fixture();
        var thrown = f.dispatch("/async", "throw");
        f.tasks.runAll();
        assertEquals("synchronous business failure", failure(thrown).getMessage());
        var missing = f.dispatch("/async", "null");
        f.tasks.runAll();
        assertInstanceOf(IllegalStateException.class, failure(missing));
    }

    /**
     * 验证分发与执行事件取消均结束阶段且不调用处理方法。
     *
     * @param dispatchEvent 用于本次测试的 {@code dispatchEvent} 数据
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void dispatchAndExecuteEventCancellationSettleWithoutInvokingHandler(boolean dispatchEvent) throws Exception {
        var f = new Fixture();
        f.listener = event -> {
            if (dispatchEvent && event instanceof CommandDispatchEvent
                    || !dispatchEvent && event instanceof CommandExecuteEvent) event.setCancelled(true);
        };
        var result = f.dispatch("/async");
        f.tasks.runAll();
        assertEquals(CANCELLED, result.toCompletableFuture().join().status());
        assertEquals(0, f.handler.calls);
    }

    /**
     * 验证监听器失败能结束分发等待而非永久挂起。
     *
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     */
    @Test
    void listenerFailureSettlesDispatchRatherThanLeavingItPending() throws Exception {
        var f = new Fixture();
        var expected = new IllegalStateException("listener failure");
        f.listener = event -> { throw expected; };
        var result = f.dispatch("/async");
        f.tasks.runAll();
        var cause = failure(result);
        while (cause.getCause() != null) cause = cause.getCause();
        assertSame(expected, cause);
        assertEquals(0, f.handler.calls);
    }

    /**
     * 验证权限拒绝等待反馈并且不会调用业务方法。
     *
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     */
    @Test
    void permissionDenialWaitsForFeedbackAndNeverInvokesHandler() throws Exception {
        var f = new Fixture();
        when(f.permissions.hasPermission(any(Command.class), any(DefaultCommand.class), any())).thenReturn(false);
        var feedback = new CompletableFuture<Void>();
        var errors = new ICommandErrorHandler() {
            /**
             * 返回受控权限拒绝反馈阶段，用于验证分发等待与发送失败传播。
             *
             * @param data 本次处理的输入数据
             * @return 本用例受控的完成阶段，可由测试推进成功、拒绝或异常
             */
            @Override public CompletionStage<Void> handleNoPermission(CommandData<?> data) { return feedback; }
        };
        var result = f.dispatcher.dispatch(data("/async", new Input()), errors, null);
        f.tasks.runAll();
        assertEquals(0, f.handler.calls);
        assertFalse(result.toCompletableFuture().isDone());
        feedback.complete(null);
        assertEquals(PERMISSION_DENIED, result.toCompletableFuture().join().status());
    }

    /**
     * 验证反馈失败不会被转换为成功的拒绝回执。
     *
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     */
    @Test
    void feedbackFailureIsNotConvertedToSuccessfulDenial() throws Exception {
        var f = new Fixture();
        when(f.permissions.hasPermission(any(Command.class), any(DefaultCommand.class), any())).thenReturn(false);
        var expected = new IllegalArgumentException("feedback failed");
        var errors = new ICommandErrorHandler() {
            /**
             * 返回受控权限拒绝反馈阶段，用于验证分发等待与发送失败传播。
             *
             * @param data 本次处理的输入数据
             * @return 本用例受控的完成阶段，可由测试推进成功、拒绝或异常
             */
            @Override public CompletionStage<Void> handleNoPermission(CommandData<?> data) {
                return CompletableFuture.failedStage(expected);
            }
        };
        var result = f.dispatcher.dispatch(data("/async", new Input()), errors, null);
        f.tasks.runAll();
        assertSame(expected, failure(result));
    }

    /**
     * 验证非法参数等待错误反馈且跳过业务方法。
     *
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     */
    @Test
    void invalidArgumentsWaitForErrorFeedbackAndSkipHandler() throws Exception {
        var f = new Fixture();
        var sent = new CompletableFuture<Void>();
        var errors = new ICommandErrorHandler() {
            /**
             * 返回受控参数错误反馈阶段，验证错误分支不会提前完成。
             *
             * @param data 本次处理的输入数据
             * @return 本用例受控的完成阶段，可由测试推进成功、拒绝或异常
             */
            @Override public CompletionStage<Void> handleArgsError(CommandData<?> data) { return sent; }
        };
        var result = f.dispatcher.dispatch(data("/async", new Input(), "required", "invalid-number"), errors, null);
        f.tasks.runAll();
        assertFalse(result.toCompletableFuture().isDone());
        assertEquals(0, f.handler.calls);
        sent.complete(null);
        assertEquals(INVALID_ARGUMENTS, result.toCompletableFuture().join().status());
    }

    /**
     * 验证作用域拒绝后不继续权限和业务处理。
     *
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     */
    @Test
    void rejectedScopeDoesNotCheckPermissionOrInvokeHandler() throws Exception {
        var f = new Fixture();
        when(f.scopes.inScope(any(Command.class), any(DefaultCommand.class), any())).thenReturn(false);
        var result = f.dispatch("/async");
        f.tasks.runAll();
        assertEquals(OUT_OF_SCOPE, result.toCompletableFuture().join().status());
        assertEquals(0, f.handler.calls);
        verifyNoInteractions(f.permissions);
    }

    /**
     * 验证原作用域管理器继续拒绝不支持的客户端与范围。
     *
     * @param clientMismatch 用于本次测试的 {@code clientMismatch} 数据
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void nativeScopeManagerStillRejectsUnsupportedClientOrScope(boolean clientMismatch) throws Exception {
        var f = new Fixture();
        var scopes = new CommandScopeManager();
        var dispatcher = new CommandDispatcher(f.logger, f.permissions, scopes, f.executor,
                new CommandArgumentProcessor(f.logger), f.scanner, f.events);
        try (var factory = mockStatic(CommandEasyData.class)) {
            var store = mock(CommandEasyData.class);
            var dto = mock(IEasyDataDto.class);
            factory.when(() -> CommandEasyData.of(anyString())).thenReturn(store);
            when(store.get(anyString())).thenReturn(dto);
            var input = CommandData.builder().body("/async").args(new String[0]).scopeId("scope").senderId("user")
                    .scope(clientMismatch ? CommandScope.GROUP : CommandScope.PRIVATE)
                    .clientClass(clientMismatch ? OtherClient.class : AllowedClient.class)
                    .permission(CommandPermission.ADMIN).rawData(new Input()).build();
            var result = dispatcher.dispatch(input, null, null);
            f.tasks.runAll();
            assertEquals(OUT_OF_SCOPE, result.toCompletableFuture().join().status());
            assertEquals(0, f.handler.calls);
            verifyNoInteractions(f.permissions);
        }
    }

    /**
     * 验证未匹配、关闭、方法缺失与数据错型均明确终止。
     *
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     */
    @Test
    void unmatchedDisabledMissingAndMismatchedDataAllTerminate() throws Exception {
        var f = new Fixture();
        var unknown = f.dispatch("/unknown");
        var disabled = f.dispatch("/closed");
        var missing = f.dispatch("/sub-only", "unknown");
        var mismatch = f.dispatcher.dispatch(data("/async", "wrong raw type"), null, null);
        f.tasks.runAll();
        assertEquals(NOT_MATCHED, unknown.toCompletableFuture().join().status());
        assertEquals(DISABLED, disabled.toCompletableFuture().join().status());
        assertEquals(METHOD_NOT_FOUND, missing.toCompletableFuture().join().status());
        assertEquals(DATA_TYPE_MISMATCH, mismatch.toCompletableFuture().join().status());
        assertEquals(0, f.handler.calls);
    }

    /**
     * 验证正则及帮助分支返回回执且不误调默认方法。
     *
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     */
    @Test
    void regexAndHelpReturnResultsWithoutInvokingDefaultHandler() throws Exception {
        var f = new Fixture();
        var regex = f.dispatch("echo", "12");
        var sent = new CompletableFuture<Void>();
        var help = new IHelpSubCommandHandler() {
            /**
             * 返回受控子命令帮助阶段，验证帮助不会进入默认业务方法。
             *
             * @param data 本次处理的输入数据
             * @param map 按当前操作语义定位的输入映射
             * @param args 待绑定的命令参数
             * @return 本用例受控的完成阶段，可由测试推进成功、拒绝或异常
             */
            @Override public CompletionStage<Void> handleSubHelp(CommandData<?> data, CommandMapData map, String[] args) {
                assertArrayEquals(new String[]{"extra"}, args);
                return sent;
            }
        };
        var helped = f.dispatcher.dispatch(data("/async", new Input(), "help", "extra"), null, help);
        f.tasks.runAll();
        assertEquals(new CommandResult(COMPLETED, "regex result"), regex.toCompletableFuture().join());
        assertFalse(helped.toCompletableFuture().isDone());
        sent.complete(null);
        assertEquals(HELP, helped.toCompletableFuture().join().status());
        assertEquals(0, f.handler.calls);
    }

    /**
     * 提取已结束的分发阶段异常，验证原始失败没有被包装或吞掉。
     *
     * @param stage 需要观察或组合的完成阶段
     * @return 满足本方法约束的 {@code Throwable} 结果
     */
    static Throwable failure(CompletionStage<?> stage) {
        return TaskFuture.unwrapException(assertThrows(CompletionException.class, () -> stage.toCompletableFuture().join()));
    }

    /**
     * 捕获并由用例主动推进的任务执行队列。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    static class TaskRunner {
        final ITaskService service = mock(ITaskService.class, CALLS_REAL_METHODS);
        final Queue<Runnable> queued = new ArrayDeque<>();
        final List<String> namespaces = new ArrayList<>();

        /**
         * 通过构造参数固定当前实例的依赖与初始状态，避免执行时再从全局环境查找协作者。
         *
         * @param logger 项目日志接口
         */
        @SuppressWarnings({"rawtypes", "unchecked"})
        TaskRunner(ILogger logger) {
            doAnswer(invocation -> {
                ITaskConfig<?> config = invocation.getArgument(1);
                namespaces.add(config.getTaskStageNamespace());
                var result = new CompletableFuture<ITaskResult<?>>();
                queued.add(() -> {
                    try { result.complete(config.getCallable().call()); }
                    catch (Throwable failure) { result.completeExceptionally(failure); }
                });
                return new TaskFuture(logger, config.getName(), result);
            }).when(service).subscribeWithFuture(any(UUID.class), any(ITaskConfig.class), any(Class.class));
        }

        /**
         * 主动执行当前测试捕获的任务队列，精确控制业务开始与后续完成时机。
         */
        void runAll() { while (!queued.isEmpty()) queued.remove().run(); }
    }
}
