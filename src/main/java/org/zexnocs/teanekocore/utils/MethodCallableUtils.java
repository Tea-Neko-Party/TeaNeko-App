package org.zexnocs.teanekocore.utils;

import org.zexnocs.teanekocore.framework.function.MethodCallable;

import java.lang.annotation.Annotation;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;

/**
 * 用于从 {@link org.zexnocs.teanekocore.framework.function.MethodCallable} 中提取注解信息的工具类。
 *
 * @author zExNocs
 * @date 2026/02/18
 */
public enum MethodCallableUtils {
    /// 枚举实例，用于访问工具方法。
    INSTANCE;

    /**
     * 从 {@link MethodCallable} 中提取方法信息。
     *
     * @param callable 包含方法引用的 {@link MethodCallable} 实例
     * @return {@link Method } 提取到的方法信息，如果提取失败则返回 null
     */
    public Method extractMethod(MethodCallable<?> callable) {
        try {
            // 通过反射获取 lambda 表达式的实现方法信息。
            Method writeReplace = callable.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            SerializedLambda lambda = (SerializedLambda) writeReplace.invoke(callable);
            String className = lambda.getImplClass().replace('/', '.');
            String methodName = lambda.getImplMethodName();
            Class<?> clazz = Class.forName(className);
            for (Method method : clazz.getDeclaredMethods()) {
                if (method.getName().equals(methodName)) {
                    method.setAccessible(true);
                    return method;
                }
            }
            return null;
        }
        catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 {@link MethodCallable} 中提取指定注解的信息。
     *
     * @param callable          包含方法引用的 {@link MethodCallable} 实例
     * @param annotationClass   要提取的注解类型
     * @return {@link T }       类型的注解实例，如果方法上没有该注解则返回 null
     */
    public <T extends Annotation> T getAnnotation(MethodCallable<?> callable, Class<T> annotationClass) {
        Method method = extractMethod(callable);
        if (method != null) {
            return method.getAnnotation(annotationClass);
        }
        return null;
    }
}
