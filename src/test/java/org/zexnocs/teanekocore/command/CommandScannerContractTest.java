package org.zexnocs.teanekocore.command;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.command.api.DefaultCommand;
import org.zexnocs.teanekocore.command.api.SubCommand;
import org.zexnocs.teanekocore.framework.pair.HashPair;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * 验证扫描与执行使用同一异步方法契约，并核对实际生产命令。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public class CommandScannerContractTest {
    /**
     * 故意返回 void 的非法命令夹具。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    @Command("/invalid-void") public class VoidHandler {
        /**
         * 提供故意违反异步返回契约的 void 签名，扫描与执行均应拒绝。
         */
        @DefaultCommand public void execute() {}
    }
    /**
     * 故意直接返回业务字符串的非法命令夹具。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    @Command("/invalid-string") public class StringHandler {
        /**
         * 提供故意直接返回字符串的非法命令签名，不应进入实际业务。
         *
         * @return 按本方法契约生成的文本或标识
         */
        @SubCommand("sub") public String execute() { return "premature result"; }
    }
    /**
     * 故意声明为私有方法的非法命令夹具。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    @Command("/invalid-private") public class PrivateHandler {
        /**
         * 提供非公开的异步命令签名，验证扫描和执行使用同一公开性约束。
         *
         * @return 本用例受控的完成阶段，可由测试推进成功、拒绝或异常
         */
        @DefaultCommand private CompletionStage<Void> execute() { return null; }
    }

    /**
     * 验证扫描器和执行器共同拒绝非法命令方法契约。
     *
     * @param type 运行时类型
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     */
    @ParameterizedTest
    @ValueSource(strings = {"void", "string", "private"})
    void scannerAndExecutorRejectInvalidHandlerContracts(String type) throws Exception {
        Object bean = switch (type) {
            case "void" -> new VoidHandler();
            case "string" -> new StringHandler();
            default -> new PrivateHandler();
        };
        var beans = mock(IBeanScanner.class);
        doReturn(Map.of("invalid", HashPair.of(bean.getClass().getAnnotation(Command.class), bean)))
                .when(beans).getBeansWithAnnotation(Command.class);
        doReturn(bean.getClass()).when(beans).getBeanClass(bean);
        var logger = mock(ILogger.class);
        var scanner = new CommandScanner(logger, beans);
        assertThrows(IllegalArgumentException.class, scanner::_scan);

        var tasks = new CommandAsyncTest.TaskRunner(logger);
        var executor = new CommandExecutor(tasks.service);
        var result = executor.execute(bean, bean.getClass().getDeclaredMethod("execute"), new Object[0], "");
        assertInstanceOf(IllegalArgumentException.class, CommandAsyncTest.failure(result));
        assertTrue(tasks.queued.isEmpty());
    }

    /**
     * 验证所有实际编译的生产命令方法均满足异步返回契约。
     *
     * @throws Exception 当底层业务、反射调用或结果转换失败时
     */
    @Test
    void everyCompiledProductionCommandUsesTheAsyncContract() throws Exception {
        var root = Path.of(CommandDispatcher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var metadata = new SimpleMetadataReaderFactory();
        int commands = 0;
        int methods = 0;
        try (var paths = Files.walk(root.resolve("org/zexnocs"))) {
            for (var path : paths.filter(file -> file.toString().endsWith(".class")).toList()) {
                var info = metadata.getMetadataReader(new FileSystemResource(path)).getAnnotationMetadata();
                if (!info.hasAnnotation(Command.class.getName())) continue;
                commands++;
                var type = Class.forName(info.getClassName(), false, CommandDispatcher.class.getClassLoader());
                for (var method : type.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(DefaultCommand.class) || method.isAnnotationPresent(SubCommand.class)) {
                        methods++;
                        assertDoesNotThrow(() -> CommandMethodContract.requireAsync(method), method.toString());
                    }
                }
            }
        }
        assertTrue(commands >= 20, "production command discovery failed");
        assertTrue(methods >= 50, "production handler discovery failed");
    }
}
