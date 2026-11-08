package org.zexnocs.teanekoapp.plugin;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mock.env.MockEnvironment;
import org.zexnocs.teanekoapp.client.TeaNekoClientScanner;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.file_config.FileConfigScanner;
import org.zexnocs.teanekocore.file_config.FileConfigService;
import org.zexnocs.teanekocore.file_config.FileTypeScanner;
import org.zexnocs.teanekocore.file_config.api.FileConfig;
import org.zexnocs.teanekocore.file_config.api.FileConfigType;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;
import org.zexnocs.teanekocore.file_config.parser.YAMLFileTypeParser;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.plugin.PluginDescriptor;
import org.zexnocs.teanekocore.plugin.IPluginLifecycle;
import org.zexnocs.teanekocore.plugin.IPluginModule;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.plugin.PluginSwitchMode;
import org.zexnocs.teanekocore.plugin.management.ManagementContext;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IClassScanner;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 使用真实 YAML 原子替换、持久账本与可控制异步资源验证插件管理闭环。
 * <br>2026/11/07：覆盖依赖联动、重启恢复、失败清理、重复编号与真实资源终态。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
class PluginManagementTest {
    @TempDir
    Path root;

    /**
     * 停用先撤销依赖链，再等待真实结束；恢复保留依赖方意愿且旧令牌失效。
     * @throws Exception 当有界异步等待或临时文件访问失败时
     */
    @Test
    void disableDrainsAndRestoresDependentsWithoutRevivingTokens() throws Exception {
        var fixture = new Fixture(root, new MockEnvironment(), PluginSwitchMode.HOT);
        fixture.initialize();
        var token = fixture.registry.capture("consumer");
        var pending = new CompletableFuture<Void>();
        fixture.consumer.stopResult = pending;
        try (var control = ExecutionControl.create()) {
            var context = context("admin", control);
            String revision = fixture.service.catalog(context).revision();
            var preview = fixture.service.preview(new PluginManagement.Request("provider", false, revision), context);
            assertEquals(Set.of("provider", "consumer"), Set.copyOf(preview.affected()));
            var change = new PluginManagement.Change(UUID.randomUUID(), "provider", false, revision, true);
            var result = fixture.service.change(change, context).toCompletableFuture();
            assertFalse(result.isDone());
            assertFalse(fixture.registry.isEnabled("provider"));
            assertFalse(fixture.registry.isEnabled("consumer"));
            assertEquals("APPLYING", fixture.service.operation(change.operationId(), context).status());
            pending.complete(null);
            assertEquals("APPLIED", result.get(2, TimeUnit.SECONDS).status());
            assertTrue(fixture.store.snapshot().enabled().getOrDefault("consumer", true));
            fixture.consumer.stopResult = CompletableFuture.completedStage(null);
            var restore = new PluginManagement.Change(UUID.randomUUID(), "provider", true,
                    fixture.service.catalog(context).revision(), true);
            assertEquals("APPLIED", fixture.service.change(restore, context).toCompletableFuture()
                    .get(2, TimeUnit.SECONDS).status());
            assertTrue(fixture.registry.isEnabled("consumer"));
            assertEquals(List.of("start:provider", "start:consumer", "stop:consumer", "stop:provider",
                    "start:provider", "start:consumer"), fixture.events);
            assertThrows(IllegalStateException.class, () -> fixture.registry.check(token));
        }
    }

    /**
     * 原编号不重复执行，不允许替换请求或跨主体读取；文件意愿跨实例重启保持。
     * @throws Exception 当有界启动等待失败时
     */
    @Test
    void persistsIntentAndProtectsOperationIdentity() throws Exception {
        var fixture = new Fixture(root, new MockEnvironment(), PluginSwitchMode.HOT);
        fixture.initialize();
        try (var control = ExecutionControl.create()) {
            var context = context("admin", control);
            var request = new PluginManagement.Change(UUID.randomUUID(), "provider", false,
                    fixture.service.catalog(context).revision(), true);
            var receipt = fixture.service.change(request, context).toCompletableFuture().get(2, TimeUnit.SECONDS);
            int completed = fixture.events.size();
            assertEquals(receipt, fixture.service.change(request, context).toCompletableFuture().get(2, TimeUnit.SECONDS));
            assertEquals(completed, fixture.events.size());
            assertThrows(IllegalArgumentException.class, () -> fixture.service.change(new PluginManagement.Change(
                    request.operationId(), "provider", true, request.expectedRevision(), true), context));
            assertThrows(SecurityException.class,
                    () -> fixture.service.operation(request.operationId(), context("other", control)));
            var restarted = new Fixture(root, new MockEnvironment(), PluginSwitchMode.HOT);
            restarted.initialize();
            assertFalse(restarted.registry.isEnabled("provider"));
            assertFalse(restarted.registry.isEnabled("consumer"));
            assertEquals(receipt, restarted.service.operation(request.operationId(), context));
        }
    }

    /**
     * 消费者清理失败时保留依赖资源且不重新开放入口，显式重试关闭后才继续逆序释放。
     * @throws Exception 当有界初始化或切换等待失败时
     */
    @Test
    void failedConsumerStopRetainsProviderResourcesUntilExplicitRetry() throws Exception {
        var fixture = new Fixture(root, new MockEnvironment(), PluginSwitchMode.HOT);
        fixture.initialize();
        fixture.consumer.stopResult = CompletableFuture.failedStage(new IllegalStateException("测试清理失败。"));
        try (var control = ExecutionControl.create()) {
            var context = context("admin", control);
            var request = new PluginManagement.Change(UUID.randomUUID(), "provider", false,
                    fixture.service.catalog(context).revision(), true);
            assertEquals("FAILED", fixture.service.change(request, context).toCompletableFuture()
                    .get(2, TimeUnit.SECONDS).status());
            assertFalse(fixture.registry.isEnabled("provider"));
            assertFalse(fixture.registry.isEnabled("consumer"));
            assertFalse(fixture.events.contains("stop:provider"));
            fixture.consumer.stopResult = CompletableFuture.completedStage(null);
            var retry = new PluginManagement.Change(UUID.randomUUID(), "provider", false,
                    fixture.service.catalog(context).revision(), true);
            assertEquals("APPLIED", fixture.service.change(retry, context).toCompletableFuture()
                    .get(2, TimeUnit.SECONDS).status());
            assertEquals(List.of("start:provider", "start:consumer", "stop:consumer", "stop:consumer", "stop:provider"),
                    fixture.events);
        }
    }

    /**
     * 应用停止复用同一次排空，等待真实结束且不把最终销毁写成用户停用意愿。
     * @throws Exception 当有界停止等待失败时
     */
    @Test
    void applicationShutdownDrainsOnceWithoutChangingPersistedIntent() throws Exception {
        var fixture = new Fixture(root, new MockEnvironment(), PluginSwitchMode.RESTART_REQUIRED);
        fixture.initialize();
        var physical = new CompletableFuture<Void>();
        fixture.consumer.stopResult = physical;
        var first = new CompletableFuture<Void>();
        var repeated = new CompletableFuture<Void>();
        fixture.service.stop(() -> first.complete(null));
        fixture.service.stop(() -> repeated.complete(null));
        assertFalse(first.isDone());
        assertFalse(repeated.isDone());
        assertTrue(fixture.service.isRunning());
        assertFalse(fixture.registry.isEnabled("provider"));
        physical.complete(null);
        first.get(2, TimeUnit.SECONDS);
        repeated.get(2, TimeUnit.SECONDS);
        assertFalse(fixture.service.isRunning());
        assertEquals(List.of("start:provider", "start:consumer", "stop:consumer", "stop:provider"), fixture.events);
        assertTrue(fixture.store.snapshot().enabled().getOrDefault("provider", true));
        var restarted = new Fixture(root, new MockEnvironment(), PluginSwitchMode.RESTART_REQUIRED);
        restarted.initialize();
        assertTrue(restarted.registry.isEnabled("consumer"));
    }

    /**
     * 请求方取消等待不能取消内部排空与回执提交，也不能让管理器永久停留在切换中。
     * @throws Exception 当有界实际切换等待失败时
     */
    @Test
    void cancellingObserverKeepsLifecycleAndReceiptOwnership() throws Exception {
        var fixture = new Fixture(root, new MockEnvironment(), PluginSwitchMode.HOT);
        fixture.initialize();
        var physical = new CompletableFuture<Void>();
        fixture.consumer.stopResult = physical;
        try (var control = ExecutionControl.create()) {
            var context = context("admin", control);
            var request = new PluginManagement.Change(UUID.randomUUID(), "provider", false,
                    fixture.service.catalog(context).revision(), true);
            var operation = fixture.service.change(request, context);
            assertTrue(operation.toCompletableFuture().cancel(false));
            assertEquals("APPLYING", fixture.service.operation(request.operationId(), context).status());
            physical.complete(null);
            assertEquals("APPLIED", operation.toCompletableFuture().get(2, TimeUnit.SECONDS).status());
            assertEquals("APPLIED", fixture.service.operation(request.operationId(), context).status());
            var restore = new PluginManagement.Change(UUID.randomUUID(), "provider", true,
                    fixture.service.catalog(context).revision(), true);
            assertEquals("APPLIED", fixture.service.change(restore, context).toCompletableFuture()
                    .get(2, TimeUnit.SECONDS).status());
        }
    }

    /**
     * 2026/11/07：连接关闭尚未完成时立即执行停止钩子，且普通调用实际结束前不释放依赖资源。
     * @throws Exception 当有界启动或切换等待失败时
     */
    @Test
    void stopHooksRunBeforeConnectionsAndInvocationsFinish() throws Exception {
        var fixture = new Fixture(root, new MockEnvironment(), PluginSwitchMode.HOT);
        fixture.initialize();
        var connection = new CompletableFuture<Void>();
        when(fixture.connections.stop("consumer")).thenReturn(connection);
        var business = new CompletableFuture<Void>();
        var call = fixture.registry.open("consumer");
        var observed = call.track(business);
        try (var control = ExecutionControl.create()) {
            var context = context("admin", control);
            var request = new PluginManagement.Change(UUID.randomUUID(), "provider", false,
                    fixture.service.catalog(context).revision(), true);
            var operation = fixture.service.change(request, context).toCompletableFuture();
            assertTrue(fixture.events.contains("stop:consumer"));
            assertFalse(fixture.events.contains("stop:provider"));
            connection.complete(null);
            assertFalse(operation.isDone());
            assertTrue(observed.toCompletableFuture().cancel(false));
            assertFalse(operation.isDone());
            business.complete(null);
            assertEquals("APPLIED", operation.get(2, TimeUnit.SECONDS).status());
            assertTrue(fixture.events.contains("stop:provider"));
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> observed.toCompletableFuture().get(2, TimeUnit.SECONDS));
        } finally {
            connection.complete(null);
            business.complete(null);
        }
    }

    /**
     * 2026/11/07：外部修改导致预写前冲突时刷新目录，原操作不覆盖文件，后续新预览能够继续应用。
     * @throws Exception 当临时文件访问或有界切换失败时
     */
    @Test
    void externalFileConflictRefreshesSnapshotBeforeNextPreview() throws Exception {
        var fixture = new Fixture(root, new MockEnvironment(), PluginSwitchMode.HOT);
        fixture.initialize();
        try (var control = ExecutionControl.create()) {
            var context = context("admin", control);
            String original = fixture.service.catalog(context).revision();
            java.nio.file.Files.writeString(root.resolve("config/plugins.yml"),
                    "version: 1\nmigrated: true\nenabled:\n  provider: false\n");
            var request = new PluginManagement.Change(UUID.randomUUID(), "provider", false, original, true);
            assertThrows(java.util.ConcurrentModificationException.class, () -> fixture.service.change(request, context));
            assertFalse(fixture.store.snapshot().enabled().get("provider"));
            String refreshed = fixture.service.catalog(context).revision();
            assertNotEquals(original, refreshed);
            assertTrue(fixture.registry.isEnabled("provider"));
            var retry = new PluginManagement.Change(UUID.randomUUID(), "provider", false, refreshed, true);
            assertEquals("APPLIED", fixture.service.change(retry, context).toCompletableFuture()
                    .get(2, TimeUnit.SECONDS).status());
            assertFalse(fixture.registry.isEnabled("provider"));
        }
    }

    /**
     * 预写回执后提交撤权立即确认未应用，不让原编号只能等待重启恢复。
     * @throws Exception 当初始化或后续显式重试失败时
     */
    @Test
    void revokedCommitCompletesOriginalReceiptWithoutRestart() throws Exception {
        var fixture = new Fixture(root, new MockEnvironment(), PluginSwitchMode.HOT);
        fixture.initialize();
        try (var control = ExecutionControl.create()) {
            var admin = context("admin", control);
            var checks = new java.util.concurrent.atomic.AtomicInteger();
            var revoking = new ManagementContext("admin", "global", UUID.randomUUID(), control, () -> {
                if (checks.incrementAndGet() >= 3) {
                    throw new SecurityException("测试提交前撤权。");
                }
            });
            var request = new PluginManagement.Change(UUID.randomUUID(), "provider", false,
                    fixture.service.catalog(admin).revision(), true);
            assertThrows(SecurityException.class, () -> fixture.service.change(request, revoking));
            assertEquals("NOT_APPLIED", fixture.service.operation(request.operationId(), admin).status());
            assertTrue(fixture.registry.isEnabled("provider"));
            assertEquals(2, fixture.events.size());
            var retry = new PluginManagement.Change(UUID.randomUUID(), "provider", false,
                    fixture.service.catalog(admin).revision(), true);
            assertEquals("APPLIED", fixture.service.change(retry, admin).toCompletableFuture()
                    .get(2, TimeUnit.SECONDS).status());
        }
    }

    /**
     * 最终停止失败不报告排空成功，资源仍由宿主拥有且允许明确再次停止。
     * @throws Exception 当初始化失败时
     */
    @Test
    void finalShutdownFailureRemainsVisibleAndCanBeRetried() throws Exception {
        var fixture = new Fixture(root, new MockEnvironment(), PluginSwitchMode.HOT);
        fixture.initialize();
        fixture.consumer.stopResult = CompletableFuture.failedStage(new IllegalStateException("测试停止失败。"));
        assertThrows(java.util.concurrent.CompletionException.class, fixture.service::stop);
        assertTrue(fixture.service.isRunning());
        assertFalse(fixture.events.contains("stop:provider"));
        fixture.consumer.stopResult = CompletableFuture.completedStage(null);
        fixture.service.stop();
        assertFalse(fixture.service.isRunning());
        assertTrue(fixture.events.contains("stop:provider"));
    }

    /**
     * 旧禁用列表只迁移一次，后续启动不能覆盖通过管理保存的新意愿。
     * @throws Exception 当有界启动或应用失败时
     */
    @Test
    void importsLegacyDisabledOnlyOnce() throws Exception {
        var environment = new MockEnvironment().withProperty("tea-neko.plugins.disabled", "provider");
        var first = new Fixture(root, environment, PluginSwitchMode.HOT);
        first.initialize();
        assertFalse(first.registry.isEnabled("provider"));
        try (var control = ExecutionControl.create()) {
            var context = context("admin", control);
            var request = new PluginManagement.Change(UUID.randomUUID(), "provider", true,
                    first.service.catalog(context).revision(), true);
            assertEquals("APPLIED", first.service.change(request, context).toCompletableFuture()
                    .get(2, TimeUnit.SECONDS).status());
        }
        var restarted = new Fixture(root, environment, PluginSwitchMode.HOT);
        restarted.initialize();
        assertTrue(restarted.registry.isEnabled("provider"));
        assertTrue(restarted.registry.isEnabled("consumer"));
    }

    /**
     * 重启型插件保存关闭意愿后仍按实际状态运行，下一次启动才停止装配业务。
     * @throws Exception 当有界启动或应用失败时
     */
    @Test
    void restartOnlyPluginKeepsActualStateUntilRestart() throws Exception {
        var fixture = new Fixture(root, new MockEnvironment(), PluginSwitchMode.RESTART_REQUIRED);
        fixture.initialize();
        try (var control = ExecutionControl.create()) {
            var context = context("admin", control);
            var request = new PluginManagement.Change(UUID.randomUUID(), "provider", false,
                    fixture.service.catalog(context).revision(), true);
            assertEquals("PENDING_RESTART", fixture.service.change(request, context).toCompletableFuture()
                    .get(2, TimeUnit.SECONDS).status());
            assertTrue(fixture.registry.isEnabled("provider"));
            assertTrue(fixture.registry.isEnabled("consumer"));
            assertEquals(2, fixture.events.size());
        }
        var restarted = new Fixture(root, new MockEnvironment(), PluginSwitchMode.RESTART_REQUIRED);
        restarted.initialize();
        assertFalse(restarted.registry.isEnabled("provider"));
    }

    /**
     * 失败启动先清理部分资源，保持门禁关闭，只有显式新操作才重新尝试。
     * @throws Exception 当有界等待失败时
     */
    @Test
    void failedStartDoesNotExposeCapabilitiesAndCanRetry() throws Exception {
        var fixture = new Fixture(root, new MockEnvironment(), PluginSwitchMode.HOT);
        fixture.provider.startResult = CompletableFuture.failedStage(new IllegalStateException("测试启动失败"));
        fixture.initialize();
        assertEquals(List.of("start:provider", "stop:provider"), fixture.events);
        assertFalse(fixture.registry.isEnabled("provider"));
        assertFalse(fixture.registry.isEnabled("consumer"));
        try (var control = ExecutionControl.create()) {
            var context = context("admin", control);
            assertEquals("FAILED", fixture.service.catalog(context).items().stream()
                    .filter(item -> item.id().equals("provider")).findFirst().orElseThrow().state());
            fixture.provider.startResult = CompletableFuture.completedStage(null);
            var request = new PluginManagement.Change(UUID.randomUUID(), "provider", true,
                    fixture.service.catalog(context).revision(), true);
            assertEquals("APPLIED", fixture.service.change(request, context).toCompletableFuture()
                    .get(2, TimeUnit.SECONDS).status());
            assertTrue(fixture.registry.isEnabled("consumer"));
        }
    }

    /**
     * 缺失普通依赖保持可诊断，客户端提供者形成的环拒绝发布并保留原快照。
     */
    @Test
    void missingDependenciesAndClientCyclesStayExplicit() {
        var registry = new PluginRegistry(List.of(module("first", Set.of(), PluginSwitchMode.HOT),
                module("second", Set.of("first"), PluginSwitchMode.HOT),
                module("missing", Set.of("uninstalled"), PluginSwitchMode.HOT)));
        assertFalse(registry.isEnabled("missing"));
        assertEquals(List.of("缺少依赖插件：uninstalled"), registry.blockers("missing"));
        long revision = registry.revision();
        assertThrows(IllegalArgumentException.class,
                () -> registry.publishAvailability(Map.of("first", Set.of("second")), Map.of()));
        assertEquals(revision, registry.revision());
        assertTrue(registry.dependencies("first").isEmpty());
        assertTrue(registry.isEnabled("second"));
    }

    /**
     * 创建不连接外部服务的受信任管理来源。
     * @param subject 主体
     * @param control 测试拥有的控制
     * @return 管理上下文
     */
    private static ManagementContext context(String subject, ExecutionControl control) {
        return new ManagementContext(subject, "global", UUID.randomUUID(), control);
    }

    /**
     * 声明测试插件，不需要实际平台或模型。
     * @param id 插件标识
     * @param dependencies 必需依赖
     * @param mode 切换方式
     * @return 模块入口
     */
    private static IPluginModule module(String id, Set<String> dependencies, PluginSwitchMode mode) {
        return () -> new PluginDescriptor(id, "1", 1, dependencies, false, "测试插件", "测试资源生命周期。", "测试", mode);
    }

    /**
     * 2026/11/07：依赖宿主的业务正常启停，宿主始终可用且不进入生命周期钩子。
     * @throws Exception 当有界协调等待失败时
     */
    @Test
    void hostDependenciesDoNotBlockBusinessLifecycle() throws Exception {
        var registry = new PluginRegistry(List.of(module("business", Set.of("teaneko.agent"), PluginSwitchMode.HOT)),
                List.of(new org.zexnocs.teanekocore.plugin.HostCapability("teaneko.agent", "1", Set.of())));
        var connections = mock(PluginClientConnections.class);
        when(connections.stop("business")).thenReturn(CompletableFuture.completedStage(null));
        var runtime = new PluginRuntime(registry, connections, mock(ILogger.class));
        runtime.configure(List.of());
        runtime.reconcile(Map.of("business", true), null, true).toCompletableFuture().get(2, TimeUnit.SECONDS);
        assertTrue(registry.isEnabled("business"));
        runtime.reconcile(Map.of("business", false), "business", false).toCompletableFuture().get(2, TimeUnit.SECONDS);
        assertFalse(registry.isEnabled("business"));
        assertTrue(registry.isEnabled("teaneko.agent"));
        verify(connections, never()).stop("teaneko.agent");
        var rejecting = new PluginRuntime(registry, connections, mock(ILogger.class));
        assertThrows(IllegalArgumentException.class,
                () -> rejecting.configure(List.of(new Hook("teaneko.agent", new ArrayList<>()))));
    }

    /**
     * 两个有依赖的插件及真实持久配置夹具。
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    private static final class Fixture {
        private final List<String> events = new ArrayList<>();
        private final Hook provider = new Hook("provider", events);
        private final Hook consumer = new Hook("consumer", events);
        private final PluginRegistry registry;
        private final PluginStateStore store;
        private final PluginManagementService service;
        private final PluginClientConnections connections = mock(PluginClientConnections.class);

        /**
         * 构建临时目录和真实文件服务，不访问工作区业务数据。
         * @param root 临时根目录
         * @param environment 旧配置来源
         * @param providerMode 提供者切换方式
         */
        private Fixture(Path root, MockEnvironment environment, PluginSwitchMode providerMode) {
            registry = new PluginRegistry(List.of(module("provider", Set.of(), providerMode),
                    module("consumer", Set.of("provider"), PluginSwitchMode.HOT)));
            var parsers = mock(FileTypeScanner.class);
            when(parsers.get(FileConfigType.YAML)).thenReturn(new YAMLFileTypeParser(environment));
            var classes = mock(IClassScanner.class);
            when(classes.getClassesWithAnnotationAndInterface(FileConfig.class, IFileConfigData.class))
                    .thenReturn(Map.of(PluginSettings.class, PluginSettings.class.getAnnotation(FileConfig.class)));
            var files = new FileConfigService(parsers, new FileConfigScanner(classes), mock(ILogger.class), root.resolve("config"));
            files.reload();
            var mapper = new ObjectMapper();
            store = new PluginStateStore(files, mapper, registry, environment);
            var scanner = mock(TeaNekoClientScanner.class);
            when(scanner.installed()).thenReturn(new TeaNekoClientScanner.Installed(Map.of(), Map.of()));
            var clients = new PluginClientDirectory(scanner, registry, List.of());
            when(connections.stop(anyString())).thenReturn(CompletableFuture.completedStage(null));
            var runtime = new PluginRuntime(registry, connections, mock(ILogger.class));
            var beans = new DefaultListableBeanFactory();
            beans.registerSingleton("provider", provider);
            beans.registerSingleton("consumer", consumer);
            service = new PluginManagementService(registry, runtime, store, clients,
                    new PluginOperationJournal(mapper, root.resolve("journal")), beans.getBeanProvider(IPluginLifecycle.class));
        }

        /**
         * 等待可观察启动结果，不使用轮询或无界等待。
         * @throws Exception 当初始化失败或超过测试期限时
         */
        private void initialize() throws Exception {
            service.initialize().toCompletableFuture().get(2, TimeUnit.SECONDS);
        }
    }

    /**
     * 可控制物理完成时机的生命周期，不伪造资源已释放。
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    private static final class Hook implements IPluginLifecycle {
        private final String owner;
        private final List<String> events;
        private CompletionStage<Void> startResult = CompletableFuture.completedStage(null);
        private CompletionStage<Void> stopResult = CompletableFuture.completedStage(null);

        /**
         * 保存本夹具的事件观察器。
         * @param owner 所属插件
         * @param events 可观察事件
         */
        private Hook(String owner, List<String> events) {
            this.owner = owner;
            this.events = events;
        }

        /** @return 测试所属插件 */
        @Override
        public String pluginId() {
            return owner;
        }

        /** @return 插件内唯一资源标识 */
        @Override
        public String id() {
            return "resource";
        }

        /** @return 测试控制的实际启动阶段 */
        @Override
        public CompletionStage<Void> start() {
            events.add("start:" + owner);
            return startResult;
        }

        /** @return 测试控制的实际停用阶段 */
        @Override
        public CompletionStage<Void> stop() {
            events.add("stop:" + owner);
            return stopResult;
        }
    }
}
