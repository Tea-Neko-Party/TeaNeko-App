package org.zexnocs.teanekoapp.client;

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.client.api.TeaNekoClient;
import org.zexnocs.teanekoapp.message.ContentPartScanner;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContentPart;
import org.zexnocs.teanekoapp.message.api.TeaNekoContentPart;
import org.zexnocs.teanekocore.command.CommandScanner;
import org.zexnocs.teanekocore.command.api.Command;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IClassScanner;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 扩展冲突不能按发现顺序覆盖，失败重扫保留已发布目录。
 *
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.7-l
 * @version 4.8.7-l
 */
class ExtensionCatalogTest {
    /**
     * 客户端首次失败明确未就绪，成功目录不可修改，冲突后仍使用原快照。
     */
    @Test
    void clientsRejectDuplicatesWithoutReplacingSnapshot() {
        var beans = mock(IBeanScanner.class);
        var declaration = mock(TeaNekoClient.class);
        when(declaration.enabled()).thenReturn(true);
        when(declaration.value()).thenReturn("test");
        var first = IndependentPair.of(declaration, mock(ITeaNekoClient.class));
        var second = IndependentPair.of(declaration, mock(ITeaNekoClient.class));
        var scanner = new TeaNekoClientScanner(beans);
        assertThrows(IllegalStateException.class, scanner::getHandlerMap);
        when(beans.getBeansWithAnnotationAndInterface(TeaNekoClient.class, ITeaNekoClient.class))
            .thenReturn(Map.of("first", first));
        scanner.init();
        var snapshot = scanner.getHandlerMap();
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        when(beans.getBeansWithAnnotationAndInterface(TeaNekoClient.class, ITeaNekoClient.class))
            .thenReturn(Map.of("first", first, "second", second));
        var failure = assertThrows(IllegalArgumentException.class, scanner::reload);
        assertTrue(failure.getMessage().contains("first"));
        assertTrue(failure.getMessage().contains("second"));
        assertSame(snapshot, scanner.getHandlerMap());
        clearInvocations(beans);
        assertSame(first, scanner.getPair("test"));
        verifyNoInteractions(beans);
    }

    /**
     * 内容冲突不替换成功目录，查询不执行发现扫描。
     */
    @Test
    void contentKeepsOldDirectoryOnConflict() {
        var classes = mock(IClassScanner.class);
        var scanner = new ContentPartScanner(classes);
        assertThrows(IllegalStateException.class, () -> scanner.getContentClass("test"));
        when(classes.getClassesWithAnnotationAndInterface(TeaNekoContentPart.class, ITeaNekoContentPart.class))
            .thenReturn(Map.of(First.class, First.class.getAnnotation(TeaNekoContentPart.class)));
        scanner.init();
        when(classes.getClassesWithAnnotationAndInterface(TeaNekoContentPart.class, ITeaNekoContentPart.class))
            .thenReturn(Map.of(First.class, First.class.getAnnotation(TeaNekoContentPart.class),
                Second.class, Second.class.getAnnotation(TeaNekoContentPart.class)));
        assertThrows(IllegalArgumentException.class, scanner::reload);
        clearInvocations(classes);
        assertSame(First.class, scanner.getContentClass("test"));
        verifyNoInteractions(classes);
    }

    /**
     * 主命令冲突必须失败，不能将第二个来源悄悄跳过。
     */
    @Test
    void commandsRejectDuplicateNames() {
        var beans = mock(IBeanScanner.class);
        var first = new First();
        var second = new Second();
        doReturn(First.class).when(beans).getBeanClass(first);
        doReturn(Second.class).when(beans).getBeanClass(second);
        when(beans.getBeansWithAnnotation(Command.class)).thenReturn(Map.of(
            "first", IndependentPair.of(First.class.getAnnotation(Command.class), first)));
        var scanner = new CommandScanner(mock(ILogger.class), beans);
        scanner.init();
        var original = scanner.getPrefixCommand("/test");
        when(beans.getBeansWithAnnotation(Command.class)).thenReturn(Map.of(
            "first", IndependentPair.of(First.class.getAnnotation(Command.class), first),
            "second", IndependentPair.of(Second.class.getAnnotation(Command.class), second)));
        assertThrows(IllegalArgumentException.class, scanner::reload);
        assertSame(original, scanner.getPrefixCommand("/test"));
    }

    /**
     * 第一个冲突来源，仅提供静态目录声明。
     *
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.7-l
     * @version 4.8.7-l
     */
    @TeaNekoContentPart("test")
    @Command("/test")
    static class First implements ITeaNekoContentPart {
        /**
         * @return 无参数的测试内容
         */
        @Override
        public String[] toCommandArgs() {
            return new String[0];
        }

        /**
         * @return 空测试正文
         */
        @Override
        public String toRawString() {
            return "";
        }
    }

    /**
     * 第二个冲突来源。
     *
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.7-l
     * @version 4.8.7-l
     */
    @TeaNekoContentPart("test")
    @Command("/test")
    static class Second extends First {
    }
}
