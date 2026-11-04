package org.zexnocs.teanekoapp.web.files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.StandardEnvironment;
import org.zexnocs.teanekoapp.client.TeaNekoClientScanner;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.client.api.TeaNekoClient;
import org.zexnocs.teanekoapp.web.WebSecurityFileConfig;
import org.zexnocs.teanekoapp.web.client.ClientManagementDirectory;
import org.zexnocs.teanekoapp.web.client.IClientManagementAdapter;
import org.zexnocs.teanekocore.file_config.FileConfigService;
import org.zexnocs.teanekocore.file_config.FileTypeScanner;
import org.zexnocs.teanekocore.file_config.api.FileConfig;
import org.zexnocs.teanekocore.file_config.api.FileConfigType;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;
import org.zexnocs.teanekocore.file_config.parser.YAMLFileTypeParser;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IClassScanner;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 使用真实 YAML、类型锁及持久回执验证机器令牌局部保存、并发仲裁和受限应用。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.1-l
 * @version 4.7.1-l
 */
class MachineTokenServiceTest {
    @TempDir
    Path root;

    /**
     * 局部修改保留其他秘密与模板，重试不可更换秘密，保存和运行应用分开。
     * @throws Exception 当临时文件操作失败时
     */
    @Test
    void savesOnlyChosenTokenAndUsesDurableSecretAwareReceipts() throws Exception {
        var fixture = new Fixture(root);
        var view = fixture.service.read(() -> {}).toCompletableFuture().join();
        assertFalse(fixture.mapper.writeValueAsString(view).contains("original-token"));
        var change = new MachineTokens.Change(UUID.randomUUID(), view.revision(), "SET", "short");
        var saved = fixture.service.save("onebot", change, () -> {}).toCompletableFuture().join();
        assertEquals("SAVED", saved.status());
        assertEquals("original-token", fixture.files.requireCurrent(WebSecurityFileConfig.class).snapshot().machineTokens().get("onebot"));
        String raw = Files.readString(fixture.path);
        assertTrue(raw.contains("${UNSET_MACHINE_TEST:untouched-token}"));
        assertTrue(raw.contains("${UNSET_ORIGIN_TEST:https://console.example}"));
        assertTrue(raw.contains("unknown-setting"));
        assertTrue(fixture.service.save("onebot", change, () -> {}).toCompletableFuture().join().replayed());
        assertThrows(CompletionException.class, () -> fixture.service.save("onebot",
                new MachineTokens.Change(change.operationId(), view.revision(), "SET", "changed-secret"), () -> {}).toCompletableFuture().join());
        assertThrows(CompletionException.class, () -> fixture.service.save("onebot",
                new MachineTokens.Change(UUID.randomUUID(), view.revision(), "OPEN", null), () -> {}).toCompletableFuture().join());
        var application = new FileSettings.Application(UUID.randomUUID(), saved.diskRevision());
        assertEquals("APPLIED", fixture.service.apply(application, () -> {}).toCompletableFuture().join().status());
        assertEquals("short", fixture.files.requireCurrent(WebSecurityFileConfig.class).snapshot().machineTokens().get("onebot"));
        assertEquals("short", fixture.service.reveal("onebot", () -> {}).toCompletableFuture().join().value());
        String journal = Files.readString(root.resolve("journal/operations.json"));
        assertFalse(journal.contains("original-token"));
        assertFalse(journal.contains("short"));
        assertTrue(fixture.service.operation(application.operationId(), () -> {}).toCompletableFuture().join().replayed());
    }

    /**
     * 空白与移除分别映射开放和拒绝，不能顺带应用认证根的其他本机改动。
     * @throws Exception 当临时配置操作失败时
     */
    @Test
    void distinguishesBlankFromMissingAndRejectsUnrelatedRootChanges() throws Exception {
        var fixture = new Fixture(root);
        var view = fixture.service.read(() -> {}).toCompletableFuture().join();
        var saved = fixture.service.save("onebot", new MachineTokens.Change(UUID.randomUUID(), view.revision(), "SET", "   "), () -> {}).toCompletableFuture().join();
        assertEquals("OPEN", fixture.service.read(() -> {}).toCompletableFuture().join().clients().getFirst().disk());
        fixture.service.apply(new FileSettings.Application(UUID.randomUUID(), saved.diskRevision()), () -> {}).toCompletableFuture().join();
        assertEquals("", fixture.files.requireCurrent(WebSecurityFileConfig.class).snapshot().machineTokens().get("onebot"));
        view = fixture.service.read(() -> {}).toCompletableFuture().join();
        fixture.service.save("onebot", new MachineTokens.Change(UUID.randomUUID(), view.revision(), "DENY", null), () -> {}).toCompletableFuture().join();
        assertEquals("DENIED", fixture.service.read(() -> {}).toCompletableFuture().join().clients().getFirst().disk());
        Files.writeString(fixture.path, Files.readString(fixture.path).replace("console.example", "other.example"));
        String revision = fixture.service.read(() -> {}).toCompletableFuture().join().revision();
        assertThrows(CompletionException.class, () -> fixture.service.apply(new FileSettings.Application(UUID.randomUUID(), revision), () -> {}).toCompletableFuture().join());
        assertEquals("https://console.example", fixture.files.requireCurrent(WebSecurityFileConfig.class).snapshot().publicOrigin());
    }

    /**
     * 固定临时文件和同步执行域，不启动平台或模型。
     *
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.1-l
     * @version 4.7.1-l
     */
    private static final class Fixture {
        final ObjectMapper mapper = new ObjectMapper();
        final Path path;
        final FileConfigService files;
        final MachineTokenService service;

        /**
         * @param root 本测试唯一拥有的临时目录
         * @throws Exception 当临时配置创建失败时
         */
        Fixture(Path root) throws Exception {
            var parsers = mock(FileTypeScanner.class);
            when(parsers.get(FileConfigType.YAML)).thenReturn(new YAMLFileTypeParser(new StandardEnvironment()));
            var classes = mock(IClassScanner.class);
            when(classes.getClassesWithAnnotationAndInterface(FileConfig.class, IFileConfigData.class)).thenReturn(
                    Map.of(WebSecurityFileConfig.class, WebSecurityFileConfig.class.getAnnotation(FileConfig.class)));
            var config = root.resolve("config");
            path = config.resolve("web/security.yml");
            Files.createDirectories(path.getParent());
            Files.writeString(path, """
                    enabled: true
                    admin-token: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
                    public-origin: '${UNSET_ORIGIN_TEST:https://console.example}'
                    machine-tokens:
                      onebot: original-token
                      other: '${UNSET_MACHINE_TEST:untouched-token}'
                    unknown-setting: preserved
                    """);
            files = new FileConfigService(parsers, classes, mock(ILogger.class), config);
            files.reload();
            var clients = mock(ClientManagementDirectory.class);
            when(clients.list()).thenReturn(List.of(new IClientManagementAdapter.View("onebot", "OneBot", "CONNECTED", 1, "已连接", Instant.now(), true)));
            when(clients.guard(eq("onebot"), any())).thenAnswer(call -> call.getArgument(1));
            var scanner = mock(TeaNekoClientScanner.class);
            var client = mock(ITeaNekoClient.class);
            var socket = mock(org.zexnocs.teanekoapp.client.AbstractWebsocketClient.class);
            when(client.getClient()).thenReturn(socket);
            when(scanner.getPair("onebot")).thenReturn(new IndependentPair<>(mock(TeaNekoClient.class), client));
            service = new MachineTokenService(files, clients, scanner,
                    new FileConfigOperations(mapper, root.resolve("journal")), Runnable::run);
        }
    }
}
