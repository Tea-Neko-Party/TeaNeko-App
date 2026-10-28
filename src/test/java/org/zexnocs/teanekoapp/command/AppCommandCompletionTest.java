package org.zexnocs.teanekoapp.command;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.zexnocs.teanekoapp.config.TeaNekoConfigCommand;
import org.zexnocs.teanekoapp.config.TeaNekoConfigNamespaces;
import org.zexnocs.teanekoapp.fake_client.FakeClient;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.sender.api.sender_box.IEasyMessageSenderBuilder;
import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserService;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.command.api.CommandScope;
import org.zexnocs.teanekocore.command.interfaces.ICommandPermissionManager;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataQueryService;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManagerScanner;
import org.zexnocs.teanekocore.logger.ILogger;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * 验证应用配置、权限和拒绝反馈均等待真实异步完成。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class AppCommandCompletionTest {
    private final FakeClient client = new FakeClient(mock(ILogger.class));
    private final ITeaNekoMessageData source = client.handleGroupMessage("group", "user", "/cfg reg rule").getData();
    private final CommandData<ITeaNekoMessageData> data = CommandData.<ITeaNekoMessageData>builder()
            .scope(CommandScope.GROUP).scopeId(source.getScopeId()).senderId(source.getUserData().getUuid().toString())
            .permission(CommandPermission.OWNER).rawData(source).build();
    private final IConfigDataService config = mock(IConfigDataService.class);
    private final ConfigManagerScanner scanner = mock(ConfigManagerScanner.class);

    /**
     * 构造具有受控原消息来源的应用命令数据，供提交与发送顺序验证。
     *
     * @return 满足本方法约束的 {@code TeaNekoConfigCommand} 结果
     */
    private TeaNekoConfigCommand command() {
        var rule = mock(ConfigManager.class);
        when(rule.namespaces()).thenReturn(new String[]{TeaNekoConfigNamespaces.GENERAL});
        when(scanner.getConfigManager("rule")).thenReturn(rule);
        return new TeaNekoConfigCommand(mock(ILogger.class), scanner, config, mock(IConfigDataQueryService.class));
    }

    /**
     * 验证配置注册在提交完成前不发送成功反馈。
     *
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     */
    @Test
    void configurationRegistrationDoesNotSendSuccessBeforeCommit() throws Exception {
        var command = command();
        var committed = new CompletableFuture<Void>();
        when(config.registerConfigAsync(any(ConfigManager.class), anyString())).thenReturn(committed);
        var result = command.registerConfig(data, "rule");
        assertTrue(client.getSentRawMessages().isEmpty());
        assertFalse(result.toCompletableFuture().isDone());

        committed.complete(null);

        result.toCompletableFuture().join();
        assertEquals(1, client.getSentRawMessages().size());
        assertTrue(client.getSentRawMessages().getFirst().contains("成功"));
    }

    /**
     * 验证配置写入失败继续传播且不会发送成功提示。
     *
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     */
    @Test
    void failedConfigurationWritePropagatesWithoutSendingSuccess() throws Exception {
        var command = command();
        var committed = new CompletableFuture<Void>();
        when(config.registerConfigAsync(any(ConfigManager.class), anyString())).thenReturn(committed);
        var result = command.registerConfig(data, "rule");
        var expected = new IllegalStateException("commit failed");
        committed.completeExceptionally(expected);

        assertSame(expected, assertThrows(CompletionException.class, () -> result.toCompletableFuture().join()).getCause());
        assertTrue(client.getSentRawMessages().isEmpty());
    }

    /**
     * 验证配置注销等待实际结果，并按是否存在选择反馈。
     *
     * @param existed 用例中配置原先是否存在
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     */
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void configurationRemovalWaitsForResultAndUsesTheCorrectFeedback(boolean existed) throws Exception {
        var command = command();
        var committed = new CompletableFuture<Boolean>();
        when(config.unregisterConfigAsync(any(ConfigManager.class), anyString())).thenReturn(committed);
        var result = command.unregisterConfig(data, "rule");
        assertTrue(client.getSentRawMessages().isEmpty());
        assertFalse(result.toCompletableFuture().isDone());
        committed.complete(existed);
        result.toCompletableFuture().join();
        assertTrue(client.getSentRawMessages().getFirst().contains(existed ? "成功" : "未注册"));
    }

    /**
     * 验证权限命令等待真实写入完成。
     */
    @Test
    void permissionCommandWaitsForActualWrite() {
        var permissions = mock(ICommandPermissionManager.class);
        var users = mock(ITeaUserService.class);
        var uuid = UUID.randomUUID();
        when(users.get(source.getClient(), "target")).thenReturn(uuid);
        var committed = new CompletableFuture<Void>();
        when(permissions.addPermissionAsync(uuid.toString(), data.getScopeId(), "capability")).thenReturn(committed);
        var result = new TeaNekoPermissionCommand(permissions, users).addPermission(data, "target", "capability");
        assertFalse(result.toCompletableFuture().isDone());
        committed.complete(null);
        assertNull(result.toCompletableFuture().join());
        verify(permissions, never()).addPermission(anyString(), anyString(), anyString());
    }

    /**
     * 验证原生命令拒绝反馈被纳入发送完成链。
     */
    @Test
    void nativeErrorFeedbackWaitsForSendCompletion() {
        var raw = mock(ITeaNekoMessageData.class);
        var sender = mock(IEasyMessageSenderBuilder.class);
        when(raw.getMessageSender()).thenReturn(sender);
        var sent = new CompletableFuture<Void>();
        when(sender.sendAtReplyMessageAsync(anyString())).thenReturn(sent);
        var command = CommandData.builder().body("/example").rawData(raw).build();
        var result = new TeaNekoCommandErrorHandler(mock(ILogger.class)).handleNoPermission(command);
        assertFalse(result.toCompletableFuture().isDone());
        sent.complete(null);
        assertNull(result.toCompletableFuture().join());
    }
}
