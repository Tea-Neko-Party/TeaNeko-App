package org.zexnocs.teanekocore.command;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.command.api.CommandScope;
import org.zexnocs.teanekocore.command.exception.CommandDataTypeMismatchException;
import org.zexnocs.teanekocore.command.interfaces.ICommandArgumentProcessor;
import org.zexnocs.teanekocore.logger.ILogger;

import java.lang.reflect.Method;

import static org.mockito.Mockito.mock;

/**
 * 测试数据继承。假设有数据 A 和 数据 B，其中 B 是 A 的子类。
 * 那么：
 * 1. 传入数据为 A，参数数据为 B，那么回抛出 CommandDataTypeMismatchException 异常，因为参数数据的类型不匹配。
 * 2. 传入数据为 B，参数数据为 A，那么应该正常解析参数数据，并返回正确的参数列表。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public class CommandInheritanceTest {
    private final ICommandArgumentProcessor iCommandArgumentProcessor = new CommandArgumentProcessor(mock(ILogger.class));

    /**
     * 验证派生命令数据可以按原参数绑定约定注入处理方法。
     *
     * @throws CommandDataTypeMismatchException 当该操作的校验或下游调用失败时
     */
    @Test
    public void testDataInheritance() throws CommandDataTypeMismatchException {
        var args = new String[]{};
        CommandData<A> dataA = buildCommandData(new A("test"), A.class);
        CommandData<B> dataB = buildCommandData(new B("test"), B.class);
        var methodA = getMethod("methodA");
        var methodB = getMethod("methodB");
        // 测试 methodA，传入数据为 A，参数数据为 B，应该抛出 CommandDataTypeMismatchException 异常
        Assertions.assertThrows(CommandDataTypeMismatchException.class,
                () -> iCommandArgumentProcessor.process(methodB, args, dataA));
        // 测试 methodB，传入数据为 B，参数数据为 A，应该正常解析参数数据，并返回正确的参数列表
        var result = iCommandArgumentProcessor.process(methodA, args, dataB);
        Assertions.assertArrayEquals(new Object[]{dataB}, result);
    }

    /**
     * 提供基础命令数据参数签名，验证派生实例可按父类型注入。
     *
     * @param data 本次处理的输入数据
     */
    public void methodA(CommandData<A> data) {}

    /**
     * 提供派生命令数据参数签名，验证类型匹配保留原始实例。
     *
     * @param data 本次处理的输入数据
     */
    public void methodB(CommandData<B> data) {}

    /**
     * 基础命令数据类型，用于参数继承绑定断言。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    @AllArgsConstructor
    @Getter
    public static class A {
        private String data;
    }

    /**
     * 基础命令数据的派生类型，用于验证原实例注入。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    @Getter
    public static class B extends A {
        /**
         * 通过构造参数固定当前实例的依赖与初始状态，避免执行时再从全局环境查找协作者。
         *
         * @param data 本次处理的输入数据
         */
        public B(String data) {
            super(data);
        }
    }

    /**
     * 获取 method 方法的 Method 对象
     *
     * @param name 稳定名称
     * @return 匹配的反射方法，名称不存在时为 null
     */
    private Method getMethod(String name) {
        for(var m : this.getClass().getDeclaredMethods()) {
            if(m.getName().equals(name)) {
                return m;
            }
        }
        return null;
    }

    /**
     * 构造一个 CommandData 对象
     *
     * @param rawData 用于本次测试的 {@code rawData} 数据
     * @param rawDataType 用于本次测试的 {@code rawDataType} 数据
     * @param <T> 本方法约定的泛型数据类型
     * @return 满足本方法约束的 {@code CommandData<T>} 结果
     */
    private <T> CommandData<T> buildCommandData(T rawData, Class<T> rawDataType) {
        var args = new String[]{};
        return CommandData.<T>builder()
                .body("test")
                .args(args)
                .scope(CommandScope.GROUP)
                .permission(CommandPermission.ADMIN)
                .senderId("test")
                .scopeId("test")
                .rawData(rawData)
                .build();
    }
}
