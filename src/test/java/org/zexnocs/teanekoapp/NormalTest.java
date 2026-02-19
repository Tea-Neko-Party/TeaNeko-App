package org.zexnocs.teanekoapp;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.zexnocs.teanekocore.logger.ILogger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * 常规测试类，用于测试任何想要测试的功能。
 */
@SpringBootTest
public class NormalTest {
    @Autowired
    private ILogger iLogger;

    @Test
    void run() throws InvocationTargetException, IllegalAccessException {
        // 获取 method 方法
        Method method = null;
        for (Method m : this.getClass().getDeclaredMethods()) {
            if (m.getName().equals("method")) {
                method = m;
                break;
            }
        }
        Assertions.assertNotNull(method);

        // 获取方法的泛型参数类型
        Type[] parameterTypes = method.getGenericParameterTypes();
        Assertions.assertEquals(1, parameterTypes.length);
        Type parameterType = parameterTypes[0];

        // 判断是否是 ParameterizedType
        Assertions.assertInstanceOf(ParameterizedType.class, parameterType);
        ParameterizedType pType = (ParameterizedType) parameterType;

        // 获取实际类型参数（TestClass<T> 中的 T）
        Type[] actualTypeArguments = pType.getActualTypeArguments();
        Assertions.assertEquals(1, actualTypeArguments.length);
        Type typeArgument = actualTypeArguments[0];

        // 判断 A 是否可以赋值给 typeArgument
        boolean canUseA = canAssignAto(typeArgument);

        if (canUseA) {
            // 构造 TestClass<A> 对象（由于类型擦除，可以直接传入）
            TestClass<A> testClassA = new TestClass<>(new A());
            // 反射调用 method
            method.invoke(this, testClassA);
            System.out.println("method 被调用，传入 A 实例");
        } else {
            System.out.println("method 不会被调用，因为泛型参数是 A 的子类");
        }
    }

    /**
     * 判断 A.class 能否赋值给给定的 Type 类型
     */
    private boolean canAssignAto(Type type) {
        // 如果 type 是 Class
        if (type instanceof Class<?> clazz) {
            // A 能否赋值给 clazz? 即 clazz 是否是 A 的父类或相同
            return clazz.isAssignableFrom(A.class);
        }
        // 如果 type 是 ParameterizedType（例如 List<A>），取其原始类型继续判断
        else if (type instanceof ParameterizedType) {
            Type rawType = ((ParameterizedType) type).getRawType();
            if (rawType instanceof Class<?> rawClass) {
                // 只比较原始类型，忽略泛型参数（简化处理）
                return rawClass.isAssignableFrom(A.class);
            }
        }
        // 其他类型（TypeVariable, WildcardType, GenericArrayType）按需扩展
        // 这里简单返回 false
        return false;
    }

    public void method(TestClass<C> testClass) {
        System.out.println("yes");
        System.out.println(testClass.getData());
    }

    public static class C {}

    public static class A extends C {}

    @AllArgsConstructor
    public static class B extends A {
        private final String test;
    }

    @AllArgsConstructor
    public static class TestClass<T> {
        @Getter
        private T data;
    }
}
