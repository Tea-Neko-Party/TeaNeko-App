package org.zexnocs.teanekocore.utils.scanner.inerfaces;

import org.zexnocs.teanekocore.framework.pair.Pair;
import org.zexnocs.teanekocore.utils.scanner.exception.InterfaceAndAnnotationInconsistencyException;

import java.lang.annotation.Annotation;
import java.util.Map;

/**
 * Bean 扫描器接口，定义了扫描 Bean 的基本方法。
 *
 * @author zExNocs
 * @date 2026/02/11
 */
public interface IBeanScanner {
    /**
     * 获取 Bean 的准确 Class 对象，解决 Spring AOP 代理导致的 Class 对象不准确问题
     * @param bean Bean 实例
     * @return Bean 的准确 Class 对象
     */
    Class<?> getBeanClass(Object bean);

    /**
     * 获取 Bean 的准确 Class 对象，解决 Spring AOP 代理导致的 Class 对象不准确问题
     * @param bean Bean 实例
     * @param beanInterface bean class 的父类或接口类型
     * @param <T> bean class 的父类或接口类型
     * @return Bean 的准确 Class 对象
     * @throws ClassCastException 当 Bean 的准确 Class 对象不实现指定接口时抛出此异常
     */
    <T> Class<? extends T> getBeanClass(Object bean, Class<T> beanInterface) throws ClassCastException;

    /**
     * 获取所有实现了指定接口的Bean
     * @param interfaceType 需要扫描的接口类型
     * @return 一个包含所有实现了指定接口的 Bean 的 Map，键为 Bean 的名称，值为 Bean 的实例
     */
    <T> Map<String, T> getBeansOfType(Class<T> interfaceType);

    /**
     * 获取所有带有指定注解的 Bean
     * @param annotationType 需要扫描的注解类型
     * @return 一个包含所有带有指定注解的 Bean 的 Map，键为 Bean 的名称，值为一个 Pair 对象，包含注解实例和 Bean 实例
     */
    <A extends Annotation> Map<String, Pair<A, Object>> getBeansWithAnnotation(Class<A> annotationType);

    /**
     * 获取所有带有指定注解的 Bean，并且要求带有该注解的 Bean 都实现了指定接口
     * @param annotationType 需要扫描的注解类型
     * @param interfaceType 需要扫描的接口类型
     * @return 一个包含所有带有指定注解的 Bean 的 Map，键为 Bean 的名称，值为一个 Pair 对象，包含注解实例和 Bean 实例
     * @throws InterfaceAndAnnotationInconsistencyException 当带有指定注解的 Bean 中存在不实现指定接口的 Bean 时抛出此异常
     */
    <A extends Annotation, T> Map<String, Pair<A, T>> getBeansWithAnnotationAndInterface(
            Class<A> annotationType, Class<T> interfaceType)
        throws InterfaceAndAnnotationInconsistencyException;
}
