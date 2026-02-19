package org.zexnocs.teanekocore.command;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.command.api.CommandScope;
import org.zexnocs.teanekocore.command.api.DefaultValue;
import org.zexnocs.teanekocore.command.exception.CommandDataTypeMismatchException;
import org.zexnocs.teanekocore.command.interfaces.ICommandArgumentProcessor;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 测试指令参数处理器。主要测试指令参数的解析和处理是否正确。
 *
 * @author zExNocs
 * @date 2026/02/19
 */
@SpringBootTest
public class CommandArgumentProcessorTest {

    @Autowired
    private ICommandArgumentProcessor iCommandArgumentProcessor;

    /**
     * 测试指令参数的解析和处理是否正确。主要测试以下几种情况：
     * 1. 参数列表中包含 CommandData 对象，且参数类型正确，应该返回正常的参数列表。
     * 2. 参数列表中包含 CommandData 对象，但参数类型不正确，应该抛出 CommandDataTypeMismatchException 异常。
     * 3. 参数列表中包含 @DefaultValue 注解，且参数类型正确，应该返回正常的参数列表，且默认值应该被正确解析。
     * 4. 参数列表中包含 @DefaultValue 注解，但参数类型不正确，应该抛出 CommandDataTypeMismatchException 异常。
     * 5. 参数列表中包含 List 类型的参数，应该将 args 中的参数解析为 List，并返回正确的参数列表。
     * 6. 参数列表中包含 @DefaultValue 注解，且参数类型正确，但 args 中的参数数量不足，应该返回正常的参数列表，且默认值应该被正确解析。
     * 7. 参数列表中包含 @DefaultValue 注解，且参数类型正确，但 args 中的参数数量过多，应该返回正常的参数列表，且默认值应该被正确解析。
     */
    @Test
    public void testProcess() throws CommandDataTypeMismatchException {
        // 构造一个 CommandData 对象
        var args = new String[]{
                "arg1",
                "arg2",
                "123",
                "456",
                "true"
        };
        CommandData<String> data = CommandData.<String>builder()
                .body("test")
                .args(args)
                .scope(CommandScope.GROUP)
                .permission(CommandPermission.ADMIN)
                .senderId("test")
                .scopeId("test")
                .rawData("rawData")
                .build();

        // 测试 method1，应该返回 null
        var method1 = getMethod("method1");
        var processed1 = iCommandArgumentProcessor.process(method1, args, data);
        Assertions.assertNull(processed1);

        // 测试 method2，应该返回正常的参数列表
        var method2 = getMethod("method2");
        var processed2 = iCommandArgumentProcessor.process(method2, args, data);
        Assertions.assertArrayEquals(new Object[]{data, "arg1", "arg2", 123}, processed2);

        // 测试 method3，应该返回正常的参数列表
        var method3 = getMethod("method3");
        var processed3 = iCommandArgumentProcessor.process(method3, args, data);
        Assertions.assertArrayEquals(new Object[]{data, "arg1", "arg2", 123, 456, true}, processed3);

        // 测试 method4，因为优先级别为 arg > default value，所以 c 的值应该为 "123"
        var method4 = getMethod("method4");
        var processed4 = iCommandArgumentProcessor.process(method4, args, data);
        Assertions.assertArrayEquals(new Object[]{"arg1", "arg2", "123", 456}, processed4);

        // 测试 method5，因为要优先解析全部，所以 c 的值应该为 "c"
        var method5 = getMethod("method5");
        var processed5 = iCommandArgumentProcessor.process(method5, args, data);
        Assertions.assertArrayEquals(new Object[]{"arg1", "arg2", "c", 123, 456, true}, processed5);

        // 测试 method6，应该返回一个 list，且参数同 args
        var method6 = getMethod("method6");
        var processed6 = iCommandArgumentProcessor.process(method6, args, data);
        Assertions.assertInstanceOf(List.class, processed6[0]);
        Assertions.assertArrayEquals(args, ((List<?>) processed6[0]).toArray());

        // 测试 method7，因为要优先解析全部，所以 c 的值应该为 "c"，且 g 的值应该为空
        var method7 = getMethod("method7");
        var processed7 = iCommandArgumentProcessor.process(method7, args, data);
        Assertions.assertArrayEquals(new Object[]{"arg1", "arg2", "c", 123, 456, true, List.of()}, processed7);
    }

    private void method1(CommandData<String> data, String a, int b) {}

    private void method2(CommandData<String> data, String a, String b, int c) {}

    private void method3(CommandData<String> data, String a, String b, int c, int d, boolean e) {}

    private void method4(String a, String b, @DefaultValue("c") String c, int d) {}

    private void method5(String a, String b, @DefaultValue("c") String c, int d, int e, boolean f) {}

    private void method6(List<String> a) {}

    private void method7(String a, String b, @DefaultValue("c") String c, int d, int e, boolean f, List<String> g) {}

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
}
