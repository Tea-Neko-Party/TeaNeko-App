package org.zexnocs.teanekocore.file_config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zexnocs.teanekocore.file_config.api.FileConfig;
import org.zexnocs.teanekocore.file_config.api.FileConfigType;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;
import org.zexnocs.teanekocore.file_config.exception.FileConfigDataNotFoundException;
import org.zexnocs.teanekocore.file_config.interfaces.IFileDocumentParser;
import org.zexnocs.teanekocore.file_config.interfaces.IFileTypeParser;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IClassScanner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 用临时文件和受控解析器验证最新加载状态，保留兼容读法并覆盖失败后恢复。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/05：覆盖嵌套、并发文档访问只扫描一次，重载仍发布新代次且拒绝未注册类型。
 * <br>2026/11/05：验证专用类型重扫、失败保留目录和无文档运行查询。
 * <br>2026/11/10：失效线程上下文类加载器不影响配置模板读取及首次初始化。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.9.1-l
 */
class FileConfigCurrentReadTest {
    @TempDir Path directory;
    private final FileTypeScanner parsers = mock(FileTypeScanner.class);
    private final IClassScanner scanner = mock(IClassScanner.class);
    private final ILogger logger = mock(ILogger.class);
    private final IFileTypeParser parser = mock(IFileTypeParser.class);
    private final FileConfig annotation = mock(FileConfig.class);

    /**
     * 2026/11/10：复现 Web 容器停止后继承的线程加载器，模板仍由配置类型的应用加载器提供。
     * @throws Exception 当模板或临时文件访问失败时
     */
    @Test
    void templateReadsAndInitialLoadIgnoreStoppedContextClassLoader() throws Exception {
        var documentParser = mock(IFileDocumentParser.class);
        when(annotation.path()).thenReturn("");
        when(annotation.value()).thenReturn("loader-test");
        when(annotation.type()).thenReturn(FileConfigType.YAML);
        when(scanner.getClassesWithAnnotationAndInterface(FileConfig.class, IFileConfigData.class))
                .thenReturn(Map.of(Value.class, annotation));
        when(parsers.get(FileConfigType.YAML)).thenReturn(documentParser);
        when(documentParser.getSuffix()).thenReturn("yml");
        var expected = new Value("模板值");
        when(documentParser.fromFileToData(any(InputStream.class), eq(Value.class))).thenReturn(expected);
        var service = new FileConfigService(parsers, new FileConfigScanner(scanner), logger, directory);
        var stopped = mock(ClassLoader.class);
        when(stopped.getResource(anyString())).thenThrow(new IllegalStateException("测试 Web 容器已停止。"));
        var thread = Thread.currentThread();
        var previous = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(stopped);
            var preview = service.withDocument(Value.class, FileConfigDocument::read);
            assertFalse(preview.exists());
            assertTrue(new String(preview.content(), StandardCharsets.UTF_8).contains("value: 模板值"));
            service.reload();
            assertSame(expected, service.requireCurrent(Value.class));
            assertArrayEquals(preview.content(), Files.readAllBytes(directory.resolve("loader-test.yml")));
        } finally {
            thread.setContextClassLoader(previous);
        }
        verifyNoInteractions(stopped);
        verify(documentParser, never()).defaultDocument(any());
        verifyNoInteractions(logger);
    }

    /**
     * 只为当前临时文件装配真实服务，不扫描项目配置或连接数据库。
     * 2026/11/05：通过专用目录注入受控类型发现结果。
     * @return 真实配置服务
     * @throws Exception 当临时文件创建失败时
     */
    private FileConfigService service() throws Exception {
        Files.writeString(directory.resolve("current.yml"), "受控解析输入");
        when(annotation.path()).thenReturn(directory.toString());
        when(annotation.value()).thenReturn("current");
        when(annotation.type()).thenReturn(FileConfigType.YAML);
        when(scanner.getClassesWithAnnotationAndInterface(FileConfig.class, IFileConfigData.class))
                .thenReturn(Map.of(Value.class, annotation));
        when(parsers.get(FileConfigType.YAML)).thenReturn(parser);
        when(parser.getSuffix()).thenReturn("yml");
        return new FileConfigService(parsers, new FileConfigScanner(scanner), logger);
    }

    /**
     * 验证重载失败后严格读取拒绝旧值，兼容 get 仍保留最后成功值，修复后恢复。
     * 2026/11/05：运行查询在不支持文档解析的格式下仍可嵌套取得一致版本，并保留失败状态。
     * @throws Exception 当临时文件创建失败时
     */
    @Test void latestFailureRejectsStrictReadsWithoutDiscardingLastGoodValue() throws Exception {
        var service = service();
        var first = new Value("首次值"); var next = new Value("修复值");
        when(parser.fromFileToData(any(InputStream.class), eq(Value.class))).thenReturn(first)
                .thenThrow(new IllegalStateException("测试敏感值")).thenReturn(next);
        service.reload();
        assertSame(first, service.requireCurrent(Value.class));
        var original = service.withRuntime(Value.class, outer -> {
            assertSame(first, service.requireCurrent(Value.class));
            assertEquals(outer, service.withRuntime(Value.class, inner -> inner));
            return outer;
        });
        assertTrue(original.current());
        service.reload();
        assertSame(first, service.get(Value.class));
        assertFalse(service.withRuntime(Value.class, FileConfigDocument.Stamp::current));
        var failure = assertThrows(FileConfigDataNotFoundException.class, () -> service.requireCurrent(Value.class));
        assertFalse(failure.getMessage().contains("测试敏感值")); assertNull(failure.getCause());
        service.reload();
        assertSame(next, service.requireCurrent(Value.class));
    }

    /**
     * 验证解析器缺失会标记最新读取失败，而不是继续以旧数据通过严格检查。
     * @throws Exception 当临时文件创建失败时
     */
    @Test void missingParserInvalidatesCurrentStatus() throws Exception {
        var service = service(); var first = new Value("旧值");
        when(parser.fromFileToData(any(InputStream.class), eq(Value.class))).thenReturn(first);
        service.reload();
        when(parsers.get(FileConfigType.YAML)).thenReturn(null);
        service.reload();
        assertSame(first, service.get(Value.class));
        assertThrows(FileConfigDataNotFoundException.class, () -> service.requireCurrent(Value.class));
    }

    /**
     * 验证第一次加载失败时不存在可继续使用的默认成功数据。
     * @throws Exception 当临时文件创建失败时
     */
    @Test void initialFailureDoesNotInventValidConfiguration() throws Exception {
        var service = service();
        when(parser.fromFileToData(any(InputStream.class), eq(Value.class))).thenThrow(new IllegalStateException("测试解析失败。"));
        service.reload();
        assertThrows(FileConfigDataNotFoundException.class, () -> service.get(Value.class));
        assertThrows(FileConfigDataNotFoundException.class, () -> service.requireCurrent(Value.class));
    }

    /**
     * 敏感配置失败不得把解析器的原始值或异常链写入日志，严格状态仍标记不可用。
     * @throws Exception 当临时配置无法创建时
     */
    @Test void sensitiveFailureDoesNotLogParserValues() throws Exception {
        var service = service(); when(annotation.sensitive()).thenReturn(true);
        when(parser.fromFileToData(any(InputStream.class), eq(Value.class))).thenThrow(new IllegalArgumentException("仅测试的秘密原值"));
        service.reload();
        verify(logger).error(anyString(), argThat(message -> !message.contains("仅测试的秘密原值")));
        verify(logger, never()).error(anyString(), anyString(), any(Throwable.class));
        assertThrows(FileConfigDataNotFoundException.class, () -> service.requireCurrent(Value.class));
    }

    /**
     * 验证解析器返回空值同样使严格状态失败，旧有效数据仍只用于兼容读取。
     * @throws Exception 当临时文件创建失败时
     */
    @Test void nullParserResultCannotMasqueradeAsSuccessfulReload() throws Exception {
        var service = service(); var first = new Value("旧值");
        when(parser.fromFileToData(any(InputStream.class), eq(Value.class))).thenReturn(first).thenReturn(null);
        service.reload(); service.reload();
        assertSame(first, service.get(Value.class));
        assertThrows(FileConfigDataNotFoundException.class, () -> service.requireCurrent(Value.class));
    }

    /**
     * 验证根目录故障后必须完成该类型重载才恢复，不能在目录刚恢复时放行旧值。
     * @throws Exception 当临时文件创建失败时
     */
    @Test void rootFailureRequiresSuccessfulTypeReloadBeforeStrictRecovery() throws Exception {
        var service = service(); var first = new Value("旧值"); var next = new Value("重载值");
        when(parser.fromFileToData(any(InputStream.class), eq(Value.class))).thenReturn(first);
        service.reload();
        try (var files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.createDirectories(FileConfigService.ROOT_PATH)).thenThrow(new IOException("测试目录不可用。"));
            service.reload();
        }
        assertSame(first, service.get(Value.class));
        assertThrows(FileConfigDataNotFoundException.class, () -> service.requireCurrent(Value.class));
        when(parser.fromFileToData(any(InputStream.class), eq(Value.class))).thenAnswer(invocation -> {
            assertThrows(FileConfigDataNotFoundException.class, () -> service.requireCurrent(Value.class));
            return next;
        });
        service.reload();
        assertSame(next, service.requireCurrent(Value.class));
    }

    /**
     * 2026/11/05：模型校验式的嵌套、并发访问复用注册目录，重载不丢失版本校验与类型边界。
     *
     * @throws Exception 当临时文件操作或有界异步等待失败时
     */
    @Test
    void documentChecksReuseDiscoveryWhileReloadStillPublishesNewGeneration() throws Exception {
        var documentParser = mock(IFileDocumentParser.class);
        when(annotation.path()).thenReturn("");
        when(annotation.value()).thenReturn("current");
        when(annotation.type()).thenReturn(FileConfigType.YAML);
        var registered = new HashMap<Class<? extends IFileConfigData>, FileConfig>();
        registered.put(Value.class, annotation);
        when(scanner.getClassesWithAnnotationAndInterface(FileConfig.class, IFileConfigData.class))
                .thenReturn(registered);
        when(parsers.get(FileConfigType.YAML)).thenReturn(documentParser);
        when(documentParser.getSuffix()).thenReturn("yml");
        when(documentParser.writeDocument(any())).thenReturn("{}\n".getBytes(StandardCharsets.UTF_8));
        var first = new Value("首次值");
        var next = new Value("重载值");
        when(documentParser.fromFileToData(any(InputStream.class), eq(Value.class))).thenReturn(first, next);
        Files.writeString(directory.resolve("current.yml"), "value: 首次值\n");
        var service = new FileConfigService(parsers, new FileConfigScanner(scanner), logger, directory);
        service.reload();
        var original = service.withDocument(Value.class, FileConfigDocument::runtime);

        var checks = IntStream.range(0, 16).mapToObj(index -> CompletableFuture.runAsync(() -> {
            var actual = service.withDocument(Value.class, outer -> {
                var nested = service.withDocument(Value.class, FileConfigDocument::runtime);
                assertEquals(outer.runtime(), nested);
                return nested;
            });
            assertEquals(original, actual);
        })).toArray(CompletableFuture<?>[]::new);
        CompletableFuture.allOf(checks).get(5, TimeUnit.SECONDS);

        service.reload();
        var reloaded = service.withDocument(Value.class, FileConfigDocument::runtime);
        assertTrue(reloaded.current());
        assertTrue(reloaded.generation() > original.generation());
        assertSame(next, service.requireCurrent(Value.class));
        // 2026/11/05：上游扫描结果后续变动不能把未注册类型加入当前容器的编辑入口。
        registered.put(DefaultTestFileConfig.class, annotation);
        assertThrows(IllegalArgumentException.class,
                () -> service.withDocument(DefaultTestFileConfig.class, FileConfigDocument::runtime));
        verify(scanner, times(1)).getClassesWithAnnotationAndInterface(FileConfig.class, IFileConfigData.class);
    }

    /** 2026/11/05：手动重扫完整替换目录；发现失败不清空仍可查询的旧目录。 */
    @Test
    void explicitTypeRescanKeepsPreviousDirectoryOnFailure() {
        when(scanner.getClassesWithAnnotationAndInterface(FileConfig.class, IFileConfigData.class))
                .thenReturn(Map.of(Value.class, annotation))
                .thenThrow(new IllegalStateException("测试重扫失败。"))
                .thenReturn(Map.of());
        var types = new FileConfigScanner(scanner);
        var original = types.declarations();
        types.init();
        assertThrows(IllegalStateException.class, types::reload);
        assertSame(original, types.declarations());
        types.reload();
        assertTrue(types.declarations().isEmpty());
        verify(scanner, times(3)).getClassesWithAnnotationAndInterface(FileConfig.class, IFileConfigData.class);
    }

    /**
     * 受控解析器返回的不可变测试配置。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param value 配置内容
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.5.0-l
     * @version 4.5.0-l
     */
    private record Value(String value) implements IFileConfigData {}
}
