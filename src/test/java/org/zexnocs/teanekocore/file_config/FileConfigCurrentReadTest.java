package org.zexnocs.teanekocore.file_config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zexnocs.teanekocore.file_config.api.FileConfig;
import org.zexnocs.teanekocore.file_config.api.FileConfigType;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;
import org.zexnocs.teanekocore.file_config.exception.FileConfigDataNotFoundException;
import org.zexnocs.teanekocore.file_config.interfaces.IFileTypeParser;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IClassScanner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 用临时文件和受控解析器验证最新加载状态，保留兼容读法并覆盖失败后恢复。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
class FileConfigCurrentReadTest {
    @TempDir Path directory;
    private final FileTypeScanner parsers = mock(FileTypeScanner.class);
    private final IClassScanner scanner = mock(IClassScanner.class);
    private final ILogger logger = mock(ILogger.class);
    private final IFileTypeParser parser = mock(IFileTypeParser.class);

    /**
     * 只为当前临时文件装配真实服务，不扫描项目配置或连接数据库。
     * @return 真实配置服务
     * @throws Exception 当临时文件创建失败时
     */
    private FileConfigService service() throws Exception {
        Files.writeString(directory.resolve("current.yml"), "受控解析输入");
        var annotation = mock(FileConfig.class);
        when(annotation.path()).thenReturn(directory.toString());
        when(annotation.value()).thenReturn("current");
        when(annotation.type()).thenReturn(FileConfigType.YAML);
        when(scanner.getClassesWithAnnotationAndInterface(FileConfig.class, IFileConfigData.class))
                .thenReturn(Map.of(Value.class, annotation));
        when(parsers.get(FileConfigType.YAML)).thenReturn(parser);
        when(parser.getSuffix()).thenReturn("yml");
        return new FileConfigService(parsers, scanner, logger);
    }

    /**
     * 验证重载失败后严格读取拒绝旧值，兼容 get 仍保留最后成功值，修复后恢复。
     * @throws Exception 当临时文件创建失败时
     */
    @Test void latestFailureRejectsStrictReadsWithoutDiscardingLastGoodValue() throws Exception {
        var service = service();
        var first = new Value("首次值"); var next = new Value("修复值");
        when(parser.fromFileToData(any(InputStream.class), eq(Value.class))).thenReturn(first)
                .thenThrow(new IllegalStateException("测试敏感值")).thenReturn(next);
        service.reload();
        assertSame(first, service.requireCurrent(Value.class));
        service.reload();
        assertSame(first, service.get(Value.class));
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
     * 受控解析器返回的不可变测试配置。
     * @param value 配置内容
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record Value(String value) implements IFileConfigData {}
}
