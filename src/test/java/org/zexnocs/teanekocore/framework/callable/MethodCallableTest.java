package org.zexnocs.teanekocore.framework.callable;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.zexnocs.teanekocore.framework.description.Description;
import org.zexnocs.teanekocore.framework.function.IMethodCallable;
import org.zexnocs.teanekocore.utils.MethodCallableUtils;

public class MethodCallableTest {
    /**
     * 测试是否可以从 IMethodCallable 中提取到正确的 Method 注解。
     */
    @Test
    public void test() {
        // 测试从 IMethodCallable 中提取到正确的 Method 注解。
        IMethodCallable<Void> callable = this::method;
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
