package org.zexnocs.teanekocore.command;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.command.api.CommandScope;
import org.zexnocs.teanekocore.command.exception.CommandDataTypeMismatchException;
import org.zexnocs.teanekocore.command.interfaces.ICommandArgumentProcessor;

import java.lang.reflect.Method;

/**
 * 测试数据继承。假设有数据 A 和 数据 B，其中 B 是 A 的子类。
 * 那么：
 * 1. 传入数据为 A，参数数据为 B，那么回抛出 CommandDataTypeMismatchException 异常，因为参数数据的类型不匹配。
 * 2. 传入数据为 B，参数数据为 A，那么应该正常解析参数数据，并返回正确的参数列表。
 */
@SpringBootTest
public class CommandInheritanceTest {
    @Autowired
    private ICommandArgumentProcessor iCommandArgumentProcessor;

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

    public void methodA(CommandData<A> data) {}

    public void methodB(CommandData<B> data) {}

    @AllArgsConstructor
    @Getter
    public static class A {
        private String data;
    }

    @Getter
    public static class B extends A {
        public B(String data) {
            super(data);
        }
    }

    /**
     * 获取 method 方法的 Method 对象
     *
     * @return {@link Method }
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
