package org.zexnocs.teanekocore.command;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.zexnocs.teanekocore.actuator.task.EmptyTaskResult;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.interfaces.ITaskResult;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.command.api.DefaultCommand;
import org.zexnocs.teanekocore.command.api.SubCommand;
import org.zexnocs.teanekocore.command.easydata.CommandEasyData;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataDto;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataDtoTaskConfig;
import org.zexnocs.teanekocore.logger.ILogger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.zexnocs.teanekocore.command.CommandPermissionManager.DISABLE_NAMESPACE;
import static org.zexnocs.teanekocore.command.CommandPermissionManager.ENABLE_NAMESPACE;

/**
 * 验证权限读写键布局、异步提交与历史记录优先级。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class CommandPermissionAsyncTest {
    /**
     * 声明顶级、继承、独立与 DEBUG 权限的测试命令集合。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    static class Commands {
        /**
         * 提供继承顶级权限的默认命令夹具。
         *
         * @return 本用例受控的完成阶段，可由测试推进成功、拒绝或异常
         */
        @DefaultCommand public CompletionStage<Void> inheritedDefault() { return CompletableFuture.completedStage(null); }
        /**
         * 提供沿用顶级权限的子命令夹具。
         *
         * @return 本用例受控的完成阶段，可由测试推进成功、拒绝或异常
         */
        @SubCommand("inherited") public CompletionStage<Void> inheritedSub() { return CompletableFuture.completedStage(null); }
        /**
         * 提供独立子命令权限声明，验证其与原权限包规则一致。
         *
         * @return 本用例受控的完成阶段，可由测试推进成功、拒绝或异常
         */
        @SubCommand(value = "specific", permissionPackage = "test.specific") public CompletionStage<Void> specificSub() { return CompletableFuture.completedStage(null); }
        /**
         * 提供 DEBUG 入口，验证普通附加授权不能取得该身份权限。
         *
         * @return 本用例受控的完成阶段，可由测试推进成功、拒绝或异常
         */
        @SubCommand(value = "debug", permission = CommandPermission.DEBUG) public CompletionStage<Void> debugSub() { return CompletableFuture.completedStage(null); }
    }

    private final CommandPermissionManager manager = new CommandPermissionManager();
    private final Command command = mock(Command.class);

    /**
     * 通过构造参数固定当前实例的依赖与初始状态，避免执行时再从全局环境查找协作者。
     */
    CommandPermissionAsyncTest() {
        when(command.permission()).thenReturn(CommandPermission.ADMIN);
        when(command.permissionPackage()).thenReturn(new String[]{"test.manage"});
    }

    /**
     * 构造不同用户、作用域与原角色的可信命令输入，供权限隔离断言。
     *
     * @param scope 操作所属的原作用域
     * @param user 原操作者标识
     * @param role 来源中经过校验的成员角色
     * @return 满足本方法约束的 {@code CommandData<?>} 结果
     */
    private CommandData<?> actor(String scope, String user, CommandPermission role) {
        return CommandData.builder().scopeId(scope).senderId(user).permission(role).rawData(new Object()).build();
    }

    /**
     * 按参数选择授权、撤销、禁止或解禁写入，供相同提交契约验证。
     *
     * @param action 需要运行的处理回调
     * @return 本用例受控的完成阶段，可由测试推进成功、拒绝或异常
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     */
    private CompletionStage<Void> mutate(String action) {
        return switch (action) {
            case "grant" -> manager.addPermissionAsync("user", "scope", "test.manage");
            case "revoke" -> manager.removePermissionAsync("user", "scope", "test.manage");
            case "ban" -> manager.banPermissionAsync("user", "scope", "test.manage");
            case "unban" -> manager.unbanPermissionAsync("user", "scope", "test.manage");
            default -> throw new IllegalArgumentException(action);
        };
    }

    /**
     * 验证权限变更等待提交并与读取使用相同键布局。
     *
     * @param action 需要运行的处理回调
     */
    @ParameterizedTest
    @ValueSource(strings = {"grant", "revoke", "ban", "unban"})
    void permissionChangesWaitForCommitAndReadTheSameKeys(String action) {
        try (var storage = new PermissionStorage()) {
            boolean enabling = action.equals("grant") || action.equals("revoke");
            String namespace = enabling ? ENABLE_NAMESPACE : DISABLE_NAMESPACE;
            var role = enabling ? CommandPermission.MEMBER : CommandPermission.ADMIN;
            var actor = actor("scope", "user", role);
            if (action.equals("revoke") || action.equals("unban")) {
                storage.values(namespace, "scope@test.manage").put("user", true);
            }
            boolean before = manager.hasPermission(command, actor);
            var stage = mutate(action);

            assertFalse(stage.toCompletableFuture().isDone());
            assertEquals(before, manager.hasPermission(command, actor));

            storage.writes.getFirst().commit();

            assertNull(stage.toCompletableFuture().join());
            assertEquals(!before, manager.hasPermission(command, actor));
            verifyNoInteractions(storage.logger);
        }
    }

    /**
     * 验证权限写入失败保留原权限并继续传播异常。
     *
     * @param action 需要运行的处理回调
     */
    @ParameterizedTest
    @ValueSource(strings = {"grant", "revoke", "ban", "unban"})
    void failedPermissionWriteKeepsPreviousAuthorityAndPropagatesFailure(String action) {
        try (var storage = new PermissionStorage()) {
            var actor = actor("scope", "user", CommandPermission.MEMBER);
            boolean before = manager.hasPermission(command, actor);
            var stage = mutate(action);
            var expected = new IllegalStateException("database unavailable");
            storage.writes.getFirst().result.completeExceptionally(expected);

            assertSame(expected, assertThrows(CompletionException.class,
                    () -> stage.toCompletableFuture().join()).getCause());
            assertEquals(before, manager.hasPermission(command, actor));
            verifyNoInteractions(storage.logger);
        }
    }

    /**
     * 验证授权按用户、作用域和权限包隔离。
     *
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     */
    @Test
    void grantsRemainIsolatedByUserScopeAndPermissionPackage() throws Exception {
        try (var storage = new PermissionStorage()) {
            manager.addPermissionAsync("user", "scope", "test.manage");
            storage.writes.getFirst().commit();

            assertTrue(manager.hasPermission(command, actor("scope", "user", CommandPermission.MEMBER)));
            assertFalse(manager.hasPermission(command, actor("other-scope", "user", CommandPermission.MEMBER)));
            assertFalse(manager.hasPermission(command, actor("scope", "other-user", CommandPermission.MEMBER)));
            var specific = Commands.class.getDeclaredMethod("specificSub").getAnnotation(SubCommand.class);
            assertFalse(manager.hasPermission(command, specific, actor("scope", "user", CommandPermission.MEMBER)));
        }
    }

    /**
     * 验证默认与子命令权限继承保留，DEBUG 不能经普通授权取得。
     *
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     */
    @Test
    void defaultAndSubcommandInheritanceRetainPermissionsAndDebugCannotBeGranted() throws Exception {
        try (var storage = new PermissionStorage()) {
            storage.values(ENABLE_NAMESPACE, "scope@test.manage").put("user", true);
            var actor = actor("scope", "user", CommandPermission.MEMBER);
            var defaultCommand = Commands.class.getDeclaredMethod("inheritedDefault").getAnnotation(DefaultCommand.class);
            var sub = Commands.class.getDeclaredMethod("inheritedSub").getAnnotation(SubCommand.class);
            var debug = Commands.class.getDeclaredMethod("debugSub").getAnnotation(SubCommand.class);

            assertTrue(manager.hasPermission(command, defaultCommand, actor));
            assertTrue(manager.hasPermission(command, sub, actor));
            assertFalse(manager.hasPermission(command, debug, actor));
            assertTrue(manager.hasPermission(command, debug, actor("scope", "user", CommandPermission.DEBUG)));
            storage.values(DISABLE_NAMESPACE, "scope@test.manage").put("user", true);
            assertFalse(manager.hasPermission(command, defaultCommand, actor));
            assertFalse(manager.hasPermission(command, sub, actor));
        }
    }

    /**
     * 验证旧权限可读，显式撤销优先于历史授权。
     */
    @Test
    void legacyRecordsRemainReadableAndExplicitRevocationOverridesLegacyGrant() {
        try (var storage = new PermissionStorage()) {
            storage.values(ENABLE_NAMESPACE, "test.manage").put("scope@user", true);
            var actor = actor("scope", "user", CommandPermission.MEMBER);
            assertTrue(manager.hasPermission(command, actor));

            var stage = manager.removePermissionAsync("user", "scope", "test.manage");
            storage.writes.getFirst().commit();

            stage.toCompletableFuture().join();
            assertFalse(manager.hasPermission(command, actor));
        }
    }

    /**
     * 验证显式解禁覆盖旧禁止记录且不附带额外授权。
     */
    @Test
    void explicitUnbanOverridesLegacyBanWithoutGrantingExtraAuthority() {
        try (var storage = new PermissionStorage()) {
            storage.values(DISABLE_NAMESPACE, "test.manage").put("scope@user", true);
            var admin = actor("scope", "user", CommandPermission.ADMIN);
            assertFalse(manager.hasPermission(command, admin));

            manager.unbanPermissionAsync("user", "scope", "test.manage");
            storage.writes.getFirst().commit();

            assertTrue(manager.hasPermission(command, admin));
            assertFalse(manager.hasPermission(command, actor("scope", "user", CommandPermission.MEMBER)));
        }
    }

    /**
     * 模拟提交后才更新 DTO 缓存，不启动 Spring 或替换 EasyData 的全局服务。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private static class PermissionStorage implements AutoCloseable {
        /**
         * 测试存储的不可变 namespace 与 target 定位。
         *
         * @param namespace 原存储协议使用的命名空间
         * @param target 原存储协议使用的数据目标
         * @author zExNocs
         * @date 2026/10/28
         * @since 4.0.0
         * @version 4.5.0-l
         */
        private record Address(String namespace, String target) {}
        private final ILogger logger = mock(ILogger.class);
        private final Map<Address, Map<String, Boolean>> flags = new HashMap<>();
        private final Map<Address, IEasyDataDto> dtos = new HashMap<>();
        private final List<PendingWrite> writes = new ArrayList<>();
        private final MockedStatic<CommandEasyData> factories = mockStatic(CommandEasyData.class);

        /**
         * 通过构造参数固定当前实例的依赖与初始状态，避免执行时再从全局环境查找协作者。
         */
        private PermissionStorage() {
            for (var namespace : List.of(ENABLE_NAMESPACE, DISABLE_NAMESPACE)) {
                var store = mock(CommandEasyData.class);
                factories.when(() -> CommandEasyData.of(namespace)).thenReturn(store);
                when(store.get(anyString())).thenAnswer(invocation -> dto(namespace, invocation.getArgument(0)));
            }
        }

        /**
         * 按原存储 namespace 与 target 取得本用例独立的数据映射。
         *
         * @param namespace 原存储协议使用的命名空间
         * @param target 原存储协议使用的数据目标
         * @return 按当前定位规则生成的映射；是否可修改遵循本方法说明
         */
        private Map<String, Boolean> values(String namespace, String target) {
            return flags.computeIfAbsent(new Address(namespace, target), ignored -> new HashMap<>());
        }

        /**
         * 为当前定位返回受控数据对象，保持原权限读取键布局。
         *
         * @param namespace 原存储协议使用的命名空间
         * @param target 原存储协议使用的数据目标
         * @return 满足本方法约束的 {@code IEasyDataDto} 结果
         */
        private IEasyDataDto dto(String namespace, String target) {
            return dtos.computeIfAbsent(new Address(namespace, target), ignored -> {
                var dto = mock(IEasyDataDto.class);
                var values = values(namespace, target);
                when(dto.has(anyString())).thenAnswer(invocation -> values.containsKey(invocation.getArgument(0)));
                when(dto.getBoolean(anyString())).thenAnswer(invocation -> values.getOrDefault(invocation.getArgument(0), false));
                when(dto.getTaskConfig(anyString())).thenAnswer(invocation -> newTask(values));
                return dto;
            });
        }

        /**
         * 捕获尚未提交的权限写任务，等待测试主动确认事务结果。
         *
         * @param values 本用例当前定位的暂存数据
         * @return 满足本方法约束的 {@code IEasyDataDtoTaskConfig} 结果
         */
        private IEasyDataDtoTaskConfig newTask(Map<String, Boolean> values) {
            var write = new PendingWrite(values);
            writes.add(write);
            var task = mock(IEasyDataDtoTaskConfig.class);
            when(task.setBoolean(anyString(), anyBoolean())).thenAnswer(invocation -> {
                write.user = invocation.getArgument(0);
                write.value = invocation.getArgument(1);
                return task;
            });
            when(task.getName()).thenReturn("permission mutation");
            when(task.pushWithFuture()).thenReturn(new TaskFuture<>(logger, "permission mutation", write.result));
            when(task.pushAsync()).thenCallRealMethod();
            return task;
        }

        /**
         * 释放静态工厂替身的作用域，防止测试服务引用泄漏到其他用例。
         */
        @Override public void close() { factories.close(); }
    }

    /**
     * 暂存提交前的权限写入，等待用例确认事务结果。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private static class PendingWrite {
        private final Map<String, Boolean> values;
        private String user;
        private boolean value;
        private final CompletableFuture<ITaskResult<Void>> result = new CompletableFuture<>();

        /**
         * 通过构造参数固定当前实例的依赖与初始状态，避免执行时再从全局环境查找协作者。
         *
         * @param values 本用例当前定位的暂存数据
         */
        private PendingWrite(Map<String, Boolean> values) { this.values = values; }

        /**
         * 在用例确认提交后应用暂存权限变更并完成任务，模拟真实事务边界。
         */
        private void commit() {
            values.put(user, value);
            result.complete(EmptyTaskResult.INSTANCE);
        }
    }
}
