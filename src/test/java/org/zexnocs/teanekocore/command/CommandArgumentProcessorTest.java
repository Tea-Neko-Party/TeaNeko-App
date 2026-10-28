package org.zexnocs.teanekocore.command;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.command.api.CommandScope;
import org.zexnocs.teanekocore.command.api.DefaultValue;
import org.zexnocs.teanekocore.command.exception.CommandDataTypeMismatchException;
import org.zexnocs.teanekocore.command.interfaces.ICommandArgumentProcessor;
import org.zexnocs.teanekocore.logger.ILogger;

import java.lang.reflect.Method;
import java.util.List;

import static org.mockito.Mockito.mock;

/**
 * 测试指令参数处理器。主要测试指令参数的解析和处理是否正确。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public class CommandArgumentProcessorTest {

    private final ICommandArgumentProcessor iCommandArgumentProcessor = new CommandArgumentProcessor(mock(ILogger.class));

    /**
     * 测试指令参数的解析和处理是否正确。主要测试以下几种情况：
     * 1. 参数列表中包含 CommandData 对象，且参数类型正确，应该返回正常的参数列表。
     * 2. 参数列表中包含 CommandData 对象，但参数类型不正确，应该抛出 CommandDataTypeMismatchException 异常。
     * 3. 参数列表中包含 @DefaultValue 注解，且参数类型正确，应该返回正常的参数列表，且默认值应该被正确解析。
     * 4. 参数列表中包含 @DefaultValue 注解，但参数类型不正确，应该抛出 CommandDataTypeMismatchException 异常。
     * 5. 参数列表中包含 List 类型的参数，应该将 args 中的参数解析为 List，并返回正确的参数列表。
     * 6. 参数列表中包含 @DefaultValue 注解，且参数类型正确，但 args 中的参数数量不足，应该返回正常的参数列表，且默认值应该被正确解析。
     * 7. 参数列表中包含 @DefaultValue 注解，且参数类型正确，但 args 中的参数数量过多，应该返回正常的参数列表，且默认值应该被正确解析。
     *
     * @throws CommandDataTypeMismatchException 当该操作的校验或下游调用失败时
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

    /**
     * 提供数值错型的反射签名，验证参数绑定失败时返回拒绝结果。
     *
     * @param data 本次处理的输入数据
     * @param a 第一个位置的测试绑定值
     * @param b 第二个位置的测试绑定值
     */
    private void method1(CommandData<String> data, String a, int b) {}

    /**
     * 提供带原数据和整数转换的签名，验证基本类型按位置绑定。
     *
     * @param data 本次处理的输入数据
     * @param a 第一个位置的测试绑定值
     * @param b 第二个位置的测试绑定值
     * @param c 第三个位置的测试绑定值
     */
    private void method2(CommandData<String> data, String a, String b, int c) {}

    /**
     * 提供多种基本类型签名，验证字符串、整数和布尔值的连续绑定。
     *
     * @param data 本次处理的输入数据
     * @param a 第一个位置的测试绑定值
     * @param b 第二个位置的测试绑定值
     * @param c 第三个位置的测试绑定值
     * @param d 第四个位置的测试绑定值
     * @param e 第五个位置的测试绑定值
     */
    private void method3(CommandData<String> data, String a, String b, int c, int d, boolean e) {}

    /**
     * 提供可选默认值签名，验证显式参数优先于默认值。
     *
     * @param a 第一个位置的测试绑定值
     * @param b 第二个位置的测试绑定值
     * @param c 第三个位置的测试绑定值
     * @param d 第四个位置的测试绑定值
     */
    private void method4(String a, String b, @DefaultValue("c") String c, int d) {}

    /**
     * 提供参数不足时的默认值签名，验证后续必需参数仍优先绑定。
     *
     * @param a 第一个位置的测试绑定值
     * @param b 第二个位置的测试绑定值
     * @param c 第三个位置的测试绑定值
     * @param d 第四个位置的测试绑定值
     * @param e 第五个位置的测试绑定值
     * @param f 第六个位置的测试绑定值
     */
    private void method5(String a, String b, @DefaultValue("c") String c, int d, int e, boolean f) {}

    /**
     * 提供尾部列表签名，验证其捕获剩余全部命令参数。
     *
     * @param a 第一个位置的测试绑定值
     */
    private void method6(List<String> a) {}

    /**
     * 提供默认值和尾部列表组合签名，验证列表没有剩余参数时为空集合。
     *
     * @param a 第一个位置的测试绑定值
     * @param b 第二个位置的测试绑定值
     * @param c 第三个位置的测试绑定值
     * @param d 第四个位置的测试绑定值
     * @param e 第五个位置的测试绑定值
     * @param f 第六个位置的测试绑定值
     * @param g 尾部剩余参数列表
     */
    private void method7(String a, String b, @DefaultValue("c") String c, int d, int e, boolean f, List<String> g) {}

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
}
