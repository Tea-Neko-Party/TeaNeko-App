package org.zexnocs.teanekoapp.web.files;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.zexnocs.teanekoagent.component.llm.configuration.FileLLMCredentialResolver;
import org.zexnocs.teanekoagent.component.llm.file_config.LLMFileConfigService;
import org.zexnocs.teanekoagent.component.llm.file_config.LLMFileConfigurationEpoch;
import org.zexnocs.teanekoagent.component.llm.file_config.LLMModelFileConfig;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMConfigurationException;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelId;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.integration.web.AgentFileSettings;
import org.zexnocs.teanekoapp.web.WebSecurityFileConfig;
import org.zexnocs.teanekocore.file_config.FileConfigService;
import org.zexnocs.teanekocore.file_config.FileTypeScanner;
import org.zexnocs.teanekocore.file_config.api.FileConfig;
import org.zexnocs.teanekocore.file_config.api.FileConfigType;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;
import org.zexnocs.teanekocore.file_config.parser.YAMLFileTypeParser;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.utils.StableDigest;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IClassScanner;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// 2026/11/05：跟随模型配置装配迁移更新凭据解析器引用。

/**
 * 使用真实 YAML、文件锁、原子移动和私有账本验证关键保存与恢复路径，不启动机器人。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：自动类型投影与明确的热更新适配器共享真实文件事务。
 *
 * <br>2026/11/04：模型文件与表单夹具提供必需的上下文及输出容量声明。
 *
 * <br>2026/11/04��У������ʵ�ֵİ汾����Ϊ v4.7.2-l��
 *
 * <br>2026/11/04：验证原文件类型说明及字段注解通过秘密保护投影。
 *
 * <br>2026/11/04：更新分层重构后的类型引用，保持原调用与生命周期行为。
 *
 * <br>2026/11/05：适配模型专属 configuration 包，保留原凭据和文件边界验证。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.8.0-l
 */
class FileSettingsIntegrationTest {
    /**
     * 自动表单使用真实 YAML 命名并保守要求重启；专用合同可以确认原模块的热更新。
     * @throws Exception 当临时文件操作失败时
     */
    @Test
    void discoversRawFileSchemaAndKeepsHotApplyExplicit() throws Exception {
        var fixture = new Fixture(root);
        var security = fixture.files.get(WebSecurityFileConfig.class);
        security.setFileSections(List.of("file.runtime.observability"));
        var view = result(fixture.service.read("file.runtime.observability", () -> {}));
        assertEquals("RESTART", view.application());
        assertTrue(view.fields().stream().anyMatch(field -> field.path().equals("/max-runs")));
        var saved = result(fixture.service.save("file.runtime.observability", new FileSettings.Change(UUID.randomUUID(),
                view.revision(), List.of(new FileSettings.Edit("/max-runs", "SET", mapper.valueToTree(1500)))), () -> {}));
        assertEquals("RESTART_REQUIRED", result(fixture.service.apply("file.runtime.observability",
                new FileSettings.Application(UUID.randomUUID(), saved.diskRevision()), () -> {})).status());
        assertEquals(1000, fixture.files.get(org.zexnocs.teanekocore.actuator.runtime.RuntimeSettings.class).getMaxRuns());
        var runtime = new RuntimeFileSettingsAdapter(fixture.parsers);
        var declarations = new LinkedHashMap<>(fixture.scanner.getBeansWithAnnotationAndInterface(TeaNekoFileSettings.class, IFileSettingsAdapter.class));
        declarations.put("runtime", new IndependentPair<>(RuntimeFileSettingsAdapter.class.getAnnotation(TeaNekoFileSettings.class), runtime));
        when(fixture.scanner.getBeansWithAnnotationAndInterface(TeaNekoFileSettings.class, IFileSettingsAdapter.class)).thenReturn(declarations);
        doReturn(RuntimeFileSettingsAdapter.class).when(fixture.scanner).getBeanClass(runtime);
        security.setFileSections(List.of("core.runtime"));
        var service = new FileSettingsService(new FileSettingsDirectory(fixture.scanner, fixture.classes, fixture.parsers),
                fixture.files, mock(PluginRegistry.class), fixture.journal, Runnable::run);
        var current = result(service.read("core.runtime", () -> {}));
        assertEquals("APPLIED", result(service.apply("core.runtime",
                new FileSettings.Application(UUID.randomUUID(), current.revision()), () -> {})).status());
        assertEquals(1500, fixture.files.get(org.zexnocs.teanekocore.actuator.runtime.RuntimeSettings.class).getMaxRuns());
    }

    @TempDir Path root;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 保留原始模板、秘密和未知节点；预检、保存、应用有不同的真实效果。
     * @throws Exception 当临时文件操作失败时
     */
    @Test
    void saveRetainsRawSecretsAndUnknownNodesAndApplyIsSeparate() throws Exception {
        var fixture = new Fixture(root);
        var before = result(fixture.service.read("test.value", () -> {}));
        // 2026/11/04：注解说明独立于原文件和秘密投影。
        assertEquals("测试文件用途说明", before.description());
        assertTrue(before.fields().stream().anyMatch(field -> field.path().equals("/name")
                && field.description().contains("测试名称的填写解释")));
        assertFalse(mapper.writeValueAsString(before).contains("不应回显的值"));
        var change = new FileSettings.Change(UUID.randomUUID(), before.revision(),
                List.of(new FileSettings.Edit("/name", "SET", mapper.valueToTree("新名称"))));
        byte[] original = Files.readAllBytes(fixture.path);
        result(fixture.service.validate("test.value", change, () -> {}));
        assertArrayEquals(original, Files.readAllBytes(fixture.path));
        var saved = result(fixture.service.save("test.value", change, () -> {}));
        assertEquals("SAVED", saved.status());
        assertEquals("原名称", fixture.files.requireCurrent(Value.class).name);
        String disk = Files.readString(fixture.path);
        assertTrue(disk.contains("${TEST_SECRET}"));
        assertTrue(disk.contains("不应回显的值"));
        assertTrue(disk.contains("未知字段"));
        assertThrows(ConcurrentModificationException.class, () -> fixture.files.write(fixture.files.get(Value.class)));
        assertEquals(saved.diskRevision(), result(fixture.service.read("test.value", () -> {})).revision());
        var applied = result(fixture.service.apply("test.value", new FileSettings.Application(UUID.randomUUID(), saved.diskRevision()), () -> {}));
        assertEquals("APPLIED", applied.status());
        assertEquals("新名称", fixture.files.requireCurrent(Value.class).name);
        assertEquals("测试环境秘密", fixture.files.requireCurrent(Value.class).secret);
        assertTrue(result(fixture.service.save("test.value", change, () -> {})).replayed());
        assertFalse(Files.readString(root.resolve("journal/operations.json")).contains("新名称"));
    }

    /**
     * 原子移动前撤权不替换原文件，同编号不同请求和过期版本均被拒绝。
     * @throws Exception 当临时文件操作失败时
     */
    @Test
    void conflictsAndRevocationCannotOverwriteExistingFile() throws Exception {
        var fixture = new Fixture(root);
        var view = result(fixture.service.read("test.value", () -> {}));
        byte[] original = Files.readAllBytes(fixture.path);
        fixture.files.withDocument(Value.class, document -> {
            try (var staged = document.prepare("name: 被撤权修改\n".getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                assertThrows(SecurityException.class, () -> staged.commit(view.revision(), () -> { throw new SecurityException("撤权"); }));
            }
            return true;
        });
        assertArrayEquals(original, Files.readAllBytes(fixture.path));
        var id = UUID.randomUUID();
        var first = new FileSettings.Change(id, view.revision(), List.of(new FileSettings.Edit("/name", "SET", mapper.valueToTree("第一次"))));
        result(fixture.service.save("test.value", first, () -> {}));
        assertThrows(RuntimeException.class, () -> result(fixture.service.save("test.value",
                new FileSettings.Change(id, view.revision(), List.of(new FileSettings.Edit("/name", "SET", mapper.valueToTree("另一请求")))), () -> {})));
        assertThrows(RuntimeException.class, () -> result(fixture.service.save("test.value",
                new FileSettings.Change(UUID.randomUUID(), view.revision(), first.edits()), () -> {})));
        assertThrows(IllegalArgumentException.class, () -> fixture.files.withDocument(Unregistered.class, ignored -> true));
        var invalid = new FileSettings.Change(UUID.randomUUID(), result(fixture.service.read("test.value", () -> {})).revision(),
                List.of(new FileSettings.Edit("/unknown", "SET", mapper.valueToTree("不可开放"))));
        assertThrows(RuntimeException.class, () -> result(fixture.service.save("test.value", invalid, () -> {})));
    }

    /**
     * 真实文件替换之后进程丢失末次回执，重建账本服务仍可准确恢复且不再次改写文件。
     * @throws Exception 当临时文件操作失败时
     */
    @Test
    void preparedJournalRecoversAfterRealAtomicReplacement() throws Exception {
        var fixture = new Fixture(root);
        var id = UUID.randomUUID();
        fixture.files.withDocument(Value.class, document -> fixture.journal.locked(journal -> {
            var old = document.read();
            try (var staged = document.prepare("name: 中断前已保存\n".getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                var receipt = new FileSettings.Receipt(id, "PREPARED", staged.revision(), document.runtime(), "测试中断", List.of("/name"), Instant.now(), false);
                journal.put(id, new FileConfigOperations.Entry(StableDigest.sha256("web.local-admin"), "test.value", "测试摘要", old.revision(), staged.revision(), receipt));
                staged.commit(old.revision(), () -> {});
            }
            return true;
        }));
        var recoveredService = fixture.service(new FileConfigOperations(mapper, root.resolve("journal")));
        var receipt = result(recoveredService.operation("test.value", id, () -> {}));
        assertEquals("SAVED", receipt.status());
        assertTrue(receipt.replayed());
        assertEquals("原名称", fixture.files.get(Value.class).name);
        assertEquals(receipt.diskRevision(), result(recoveredService.read("test.value", () -> {})).revision());
    }

    /**
     * 模块确认失败可能已发布缓存，不伪造回滚；重启型配置不发布缓存。
     * @throws Exception 当临时文件操作失败时
     */
    @Test
    void failedConfirmationAndRestartReturnTruthfulRuntimeStates() throws Exception {
        var fixture = new Fixture(root);
        var version = result(fixture.service.read("test.value", () -> {})).revision();
        fixture.adapter.confirm.set(false);
        assertEquals("APPLY_FAILED", result(fixture.service.apply("test.value", new FileSettings.Application(UUID.randomUUID(), version), () -> {})).status());
        long generation = result(fixture.service.read("test.value", () -> {})).runtime().generation();
        fixture.adapter.restart.set(true);
        assertEquals("RESTART_REQUIRED", result(fixture.service.apply("test.value", new FileSettings.Application(UUID.randomUUID(), version), () -> {})).status());
        assertEquals(generation, result(fixture.service.read("test.value", () -> {})).runtime().generation());
    }

    /**
     * 使用同一真实文件缓存换代后，旧解析参数拒绝新凭据，新解析参数得到新凭据。
     * @throws Exception 当临时文件操作失败时
     */
    @Test
    void modelEpochPreventsOldParametersUsingNewCredential() throws Exception {
        var fixture = new Fixture(root);
        var epoch = new LLMFileConfigurationEpoch(fixture.files);
        var options = new LLMFileConfigService(fixture.files);
        options.installEpoch(epoch);
        var credentials = new FileLLMCredentialResolver(fixture.files);
        credentials.installEpoch(epoch);
        var old = options.getDefaultOptions(LLMModelId.of("deepseek"), LLMModelOptions.builder().build());
        assertEquals("first-key", credentials.require("deepseek", old).apiKey());
        fixture.files.withDocument(LLMModelFileConfig.class, document -> {
            var snapshot = document.read();
            var bytes = new String(snapshot.content(), java.nio.charset.StandardCharsets.UTF_8).replace("first-key", "second-key").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            try (var staged = document.prepare(bytes)) {
                staged.commit(snapshot.revision(), () -> {});
                document.publish(staged.revision(), document.parse(bytes), () -> {});
            }
            return true;
        });
        assertThrows(LLMConfigurationException.class, () -> credentials.require("deepseek", old));
        var current = options.getDefaultOptions(LLMModelId.of("deepseek"), LLMModelOptions.builder().build());
        assertEquals("second-key", credentials.require("deepseek", current).apiKey());
    }

    /**
     * 真实模型表单允许增删已声明条目，秘密不回显且不能通过普通设置动作覆盖。
     * @throws Exception 当临时文件操作失败时
     */
    @Test
    void modelCollectionUsesRealSchemaAndExplicitSecretActions() throws Exception {
        var fixture = new Fixture(root);
        var view = result(fixture.service.read("agent.models", () -> {}));
        assertFalse(mapper.writeValueAsString(view).contains("first-key"));
        // 2026/11/04：新增模型条目同时提供必需的输出额度及真实容量声明。
        var inserted = mapper.readTree("{\"id\":\"second\",\"provider\":\"deepseek\",\"model\":\"other-model\",\"max-tokens\":2048,\"context-window-tokens\":131072,\"api-key\":\"second-key\"}");
        var change = new FileSettings.Change(UUID.randomUUID(), view.revision(), List.of(
                new FileSettings.Edit("/models", "APPEND", inserted)));
        var receipt = result(fixture.service.save("agent.models", change, () -> {}));
        var after = result(fixture.service.read("agent.models", () -> {}));
        assertEquals(2, after.collectionSizes().get("/models"));
        assertFalse(mapper.writeValueAsString(after).contains("second-key"));
        var invalid = new FileSettings.Change(UUID.randomUUID(), receipt.diskRevision(), List.of(
                new FileSettings.Edit("/models/0/api-key", "SET", mapper.valueToTree("覆盖秘密"))));
        assertThrows(RuntimeException.class, () -> result(fixture.service.save("agent.models", invalid, () -> {})));
        var deletion = new FileSettings.Change(UUID.randomUUID(), receipt.diskRevision(), List.of(
                new FileSettings.Edit("/models/1", "REMOVE", null)));
        result(fixture.service.save("agent.models", deletion, () -> {}));
        assertEquals(1, result(fixture.service.read("agent.models", () -> {})).collectionSizes().get("/models"));
    }

    /**
     * @param stage 实际阶段
     * @param <T> 结果类型
     * @return 原结果，失败保留完成异常
     */
    private static <T> T result(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }

    /**
     * 所有写入均限制到 JUnit 临时目录，使用原扫描契约和真实配置服务。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     * <br>2026/11/04：共用实际文件解析器，支持自动目录与专用热应用适配器。
     * <br>2026/11/04：测试模型文件显式声明上下文容量和输出额度。
     * <br>2026/11/04：本次夹具调整归入 v4.7.2-l。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.2-l
     */
    private final class Fixture {
        // 2026/11/04：自动元数据与文件服务共用扫描器，避免测试另一套命名策略。
        final FileTypeScanner parsers;
        final IClassScanner classes;
        final Path path;
        final FileConfigService files;
        final FileConfigOperations journal;
        // 2026/11/04：专用与自动投影使用同一实际解析器目录。
        final FileSettingsDirectory directory;
        final Adapter adapter = new Adapter();
        final IBeanScanner scanner = mock(IBeanScanner.class);
        final FileSettingsService service;

        /**
         * @param directory 测试拥有的根目录
         * @throws Exception 当临时文件创建失败时
         */
        Fixture(Path directory) throws Exception {
            var environment = new StandardEnvironment();
            environment.getPropertySources().addFirst(new MapPropertySource("测试环境", Map.of("TEST_SECRET", "测试环境秘密")));
            var parser = new YAMLFileTypeParser(environment);
            parsers = mock(FileTypeScanner.class);
            when(parsers.get(FileConfigType.YAML)).thenReturn(parser);
            classes = mock(IClassScanner.class);
            when(classes.getClassesWithAnnotationAndInterface(FileConfig.class, IFileConfigData.class)).thenReturn(Map.of(
                    Value.class, Value.class.getAnnotation(FileConfig.class), WebSecurityFileConfig.class, WebSecurityFileConfig.class.getAnnotation(FileConfig.class),
                    LLMModelFileConfig.class, LLMModelFileConfig.class.getAnnotation(FileConfig.class),
                    org.zexnocs.teanekocore.actuator.runtime.RuntimeSettings.class,
                    org.zexnocs.teanekocore.actuator.runtime.RuntimeSettings.class.getAnnotation(FileConfig.class)));
            var config = directory.resolve("config");
            Files.createDirectories(config.resolve("agent"));
            path = config.resolve("value.yml");
            Files.writeString(path, "name: 原名称\nsecret: '${TEST_SECRET}'\nunknown: {保留: 未知字段}\nmetadata: 不应回显的值\n");
            // 2026/11/04：文件换代夹具声明容量，继续验证旧参数不可获取新凭据。
            Files.writeString(config.resolve("agent/model.yml"), "default-model-id: deepseek\nmodels:\n  - id: deepseek\n    model: test-model\n    max-tokens: 2048\n    context-window-tokens: 131072\n    api-key: first-key\n");
            files = new FileConfigService(parsers, classes, mock(ILogger.class), config);
            files.reload();
            files.get(WebSecurityFileConfig.class).setFileSections(List.of("test.value", "agent.models"));
            IFileSettingsAdapter modelAdapter = new AgentFileSettings.Models(files, new LLMFileConfigService(files));
            when(scanner.getBeansWithAnnotationAndInterface(TeaNekoFileSettings.class, IFileSettingsAdapter.class))
                    .thenReturn(Map.of("test", new IndependentPair<>(Adapter.class.getAnnotation(TeaNekoFileSettings.class), adapter),
                            "models", new IndependentPair<>(AgentFileSettings.Models.class.getAnnotation(TeaNekoFileSettings.class), modelAdapter)));
            doReturn(Adapter.class).when(scanner).getBeanClass(adapter);
            doReturn(AgentFileSettings.Models.class).when(scanner).getBeanClass(modelAdapter);
            journal = new FileConfigOperations(mapper, directory.resolve("journal"));
            this.directory = new FileSettingsDirectory(scanner, classes, parsers);
            service = service(journal);
        }

        /**
         * @param operations 真实私有账本
         * @return 使用同步执行域的真实管理服务
         */
        FileSettingsService service(FileConfigOperations operations) {
            var plugins = mock(PluginRegistry.class);
            when(plugins.isEnabled("teaneko.agent")).thenReturn(true);
            return new FileSettingsService(directory, files, plugins, operations, Runnable::run);
        }
    }

    /**
     * 仅开放名称和一个秘密，消费端确认可以模拟实际故障。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    @TeaNekoFileSettings("test.value")
    private static final class Adapter implements IFileSettingsAdapter {
        final AtomicBoolean confirm = new AtomicBoolean(true);
        final AtomicBoolean restart = new AtomicBoolean();
        /** @return 测试中文区名 */
        @Override
        public String name() {
            return "测试配置";
        }
        /** @return 已注册配置类型 */
        @Override
        public Class<? extends IFileConfigData> type() {
            return Value.class;
        }
        /** @return 测试字段白名单 */
        @Override
        public List<FileSettings.Rule> fields() {
            return List.of(new FileSettings.Rule("/name", "名称", "测试名称", "TEXT", false), new FileSettings.Rule("/secret", "秘密", "测试秘密", "TEXT", true));
        }
        /** @return 默认按需应用 */
        @Override
        public String application() {
            return "NEXT_USE";
        }
        /**
         * @param candidate 原候选
         * @return 本用例指定的应用方式
         */
        @Override
        public String applicationFor(IFileConfigData candidate) {
            return restart.get() ? "RESTART" : "NEXT_USE";
        }
        /** @param candidate 原候选 */
        @Override
        public void validate(IFileConfigData candidate) {
            if (Value.class.cast(candidate).name.isBlank()) {
                throw new IllegalArgumentException("名称不能为空。");
            }
        }
        /**
         * @param published 已发布候选
         * @return 模块真实确认
         */
        @Override
        public String verifyApplied(IFileConfigData published) {
            if (!confirm.get()) {
                throw new IllegalStateException("测试模块确认失败。");
            }
            return "模块已确认";
        }
    }

/**
     * 简单文件类型，未知字段由原解析器保留在原树而不绑定。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * 修改记录：<br>
     * 2026/11/04：提供文件及字段说明用于真实投影验证。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.6-l
     */
    @FileConfig(value = "value", type = FileConfigType.YAML, sensitive = true)
    // 2026/11/04：通过实际文件类型读取公开说明。
    @org.zexnocs.teanekocore.framework.description.Description("测试文件用途说明")
    public static class Value implements IFileConfigData {
        @org.zexnocs.teanekocore.framework.description.Description("测试名称的填写解释")
        public String name = "默认";
        public String secret = "";
        /** 创建文件默认值。 */
        public Value() {}
    }

    /**
     * 未登记类型不得获得文档能力。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    private static class Unregistered implements IFileConfigData {}
}
