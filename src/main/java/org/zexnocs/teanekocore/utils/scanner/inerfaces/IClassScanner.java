package org.zexnocs.teanekocore.utils.scanner.inerfaces;

import java.lang.annotation.Annotation;
import java.util.Map;

/**
 * 类扫描器接口，扫描不用定义为 bean 的类与其注解。
 *
 * @author zExNocs
 * @date 2026/02/27
 * @since 4.0.10
 */
public interface IClassScanner {
    /**
     * 获取所有带有指定注解的 classes
     *
     * @param <A> 注解类型
     * @param annotationType 需要扫描的注解类型
     * @return 一个包含所有带有指定注解的 class 的 Map，键为 class ，值为注解实例
     */
    <A extends Annotation> Map<Class<?>, A> getClassesWithAnnotation(Class<A> annotationType);

    /**
     * 获取所有带有指定注解的 classes，并且要求带有该注解的 classes 都实现了指定接口
     *
     * @param annotationType 需要扫描的注解类型
     * @param interfaceType 需要扫描的接口类型
     * @return 一个包含所有带有指定注解的 classes 的 Map，键为 classes 的名称，值为一个 Pair 对象，包含注解实例和 classes 实例
     */
    <A extends Annotation, T> Map<Class<? extends T>, A> getClassesWithAnnotationAndInterface(
            Class<A> annotationType, Class<T> interfaceType);
}
