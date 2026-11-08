package org.zexnocs.teanekocore.utils;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.framework.function.IMethodCallable;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodType;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;

/**
 * 用于从 {@link org.zexnocs.teanekocore.framework.function.IMethodCallable} 中提取注解信息的工具类。
 * <br>2026/11/04：按完整方法描述符解析重载，使用原加载器且不提前初始化业务类型。
 *
 * @author zExNocs
 * @date 2026/02/18
 * @since 4.0.8
 * @version 4.7.1-l
 */
public enum MethodCallableUtils {
    /// 枚举实例，用于访问工具方法。
    INSTANCE;

    /**
     * 从 {@link IMethodCallable} 中提取方法信息。
     *
     * @param callable 包含方法引用的 {@link IMethodCallable} 实例
     * @return {@link Method } 提取到的方法信息，如果提取失败则返回 null
     */
    public @Nullable Method extractMethod(IMethodCallable<?> callable) {
        try {
            // 通过反射获取 lambda 表达式的实现方法信息。
            Method writeReplace = callable.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            SerializedLambda lambda = (SerializedLambda) writeReplace.invoke(callable);
            String className = lambda.getImplClass().replace('/', '.');
            String methodName = lambda.getImplMethodName();
            // 2026/11/04：同名重载不能借用另一方法的注解。
            var loader = callable.getClass().getClassLoader();
            Class<?> clazz = Class.forName(className, false, loader);
            var signature = MethodType.fromMethodDescriptorString(lambda.getImplMethodSignature(), loader);
            return java.util.Arrays.stream(clazz.getDeclaredMethods())
                    .filter(method -> method.getName().equals(methodName)
                            && method.getReturnType() == signature.returnType()
                            && java.util.Arrays.equals(method.getParameterTypes(), signature.parameterArray()))
                    .findFirst().orElse(null);
        }
        catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 {@link IMethodCallable} 中提取指定注解的信息。
     * 2026/11/04：标注未知来源或未声明注解时的原空值合同。
     *
     * @param callable          包含方法引用的 {@link IMethodCallable} 实例
     * @param annotationClass   要提取的注解类型
     * @param <T> 注解类型
     * @return 注解实例；来源未知或未声明时为空
     */
    public <T extends Annotation> @Nullable T getAnnotation(IMethodCallable<?> callable, Class<T> annotationClass) {
        Method method = extractMethod(callable);
        if (method != null) {
            return method.getAnnotation(annotationClass);
        }
        return null;
    }
}
