package org.zexnocs.teanekoapp.framework.callable;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.framework.function.MethodCallable;
import org.zexnocs.teanekocore.utils.MethodCallableUtils;

@SpringBootTest
public class MethodCallableTest {
    /**
     * 测试是否可以从 MethodCallable 中提取到正确的 Method 注解。
     */
    @Test
    public void test() {
        // 测试从 MethodCallable 中提取到正确的 Method 注解。
        MethodCallable<Void> callable = this::method;
        var description = MethodCallableUtils.INSTANCE.getAnnotation(callable, Description.class);
        Assertions.assertNotNull(description);
        Assertions.assertEquals("test", description.value());

        // 测试从普通 lambda 函数中提取到 null
        var description2 = MethodCallableUtils.INSTANCE.getAnnotation(() -> null, Description.class);
        Assertions.assertNull(description2);
    }

    /// 测试方法，带有 Description 注解。
    @Description("test")
    public Void method() {
        return null;
    }
}
