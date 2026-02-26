package org.zexnocs.teanekocore.utils.scanner;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekocore.framework.pair.IndependentPair;
import org.zexnocs.teanekocore.framework.pair.Pair;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.scanner.exception.InterfaceAndAnnotationInconsistencyException;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;

/**
 * 提供一个可以扫描所有 bean 的服务。
 *
 * @author zExNocs
 * @date 2026/02/11
 */
@Component
public class BeanScanner implements IBeanScanner {
    /// Spring 的 ApplicationContext 对象，用于获取所有的 Bean。
    private final ApplicationContext applicationContext;
    private final ILogger logger;

    @Autowired
    public BeanScanner(ApplicationContext applicationContext, ILogger logger) {
        this.applicationContext = applicationContext;
        this.logger = logger;
    }

    /**
     * 获取 Bean 的准确 Class 对象，解决 Spring AOP 代理导致的 Class 对象不准确问题
     * @param bean Bean 实例
     * @return Bean 的准确 Class 对象
     */
    @Override
    public Class<?> getBeanClass(Object bean) {
        return AopUtils.getTargetClass(bean);
    }

    /**
     * 获取 Bean 的准确 Class 对象，解决 Spring AOP 代理导致的 Class 对象不准确问题
     *
     * @param bean          Bean 实例
     * @param beanInterface bean class 的父类或接口类型
     * @return Bean 的准确 Class 对象
     * @throws ClassCastException 当 Bean 的准确 Class 对象不实现指定接口时抛出此异常
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> Class<? extends T> getBeanClass(Object bean, Class<T> beanInterface) throws ClassCastException {
        var clazz = getBeanClass(bean);
        if (!beanInterface.isAssignableFrom(clazz)) {
            throw new ClassCastException("Bean " + clazz.getName() + " 的准确 Class 对象未实现或未继承 " + beanInterface.getName());
        }
        return (Class<? extends T>) clazz;
    }

    /**
     * 获取所有实现了指定接口的 Bean
     * @param interfaceType 需要扫描的接口类型
     * @return 一个包含所有实现了指定接口的 Bean 的 Map，键为 Bean 的名称，值为 Bean 的实例
     */
    @Override
    public <T> Map<String, T> getBeansOfType(Class<T> interfaceType) {
        return applicationContext.getBeansOfType(interfaceType);
    }

    /**
     * 获取所有带有指定注解的 Bean
     * @param annotationType 需要扫描的注解类型
     * @return 一个包含所有带有指定注解的 Bean 的 Map，键为 Bean 的名称，值为 Bean 的实例
     */
    @Override
    public <A extends Annotation> Map<String, Pair<A, Object>> getBeansWithAnnotation(Class<A> annotationType) {
        var map = applicationContext.getBeansWithAnnotation(annotationType);
        var result = new HashMap<String, Pair<A, Object>>();
        for(var entry: map.entrySet()) {
            var bean = entry.getValue();
            var clazz = getBeanClass(bean);
            var annotation = clazz.getAnnotation(annotationType);
            if (annotation == null) {
                // 无法获得注解实例，报告错误并跳过
                logger.errorWithReport(this.getClass().getSimpleName(), """
                        Bean %s 带有注解 %s 但因为未知原因无法获取注解实例，可能是由于 Spring AOP 代理导致的 Class 对象不准确问题。"""
                        .formatted(clazz.getName(), annotationType.getName()));
                continue;
            }
            result.put(entry.getKey(), IndependentPair.of(annotation, bean));
        }
        return result;
    }

    /**
     * 获取所有带有指定注解的 Bean，并且要求带有该注解的 Bean 都实现了指定接口
     * @param annotationType 需要扫描的注解类型
     * @param interfaceType 需要扫描的接口类型
     * @return 注解 → Bean 实例 的 Map
     * @throws InterfaceAndAnnotationInconsistencyException 当带有指定注解的 Bean 中存在不实现指定接口的 Bean 时抛出此异常
     */
    @Override
    public <A extends Annotation, T> Map<String, Pair<A, T>> getBeansWithAnnotationAndInterface(
            Class<A> annotationType, Class<T> interfaceType)
            throws InterfaceAndAnnotationInconsistencyException {
        var beans = applicationContext.getBeansWithAnnotation(annotationType);
        var result = new HashMap<String, Pair<A, T>>();
        for(var entry: beans.entrySet()) {
            var bean = entry.getValue();
            // 检查是否实现了指定接口
            var clazz = getBeanClass(bean);
            if (!interfaceType.isAssignableFrom(clazz)) {
                throw new InterfaceAndAnnotationInconsistencyException(
                    "Bean " + clazz.getName() + " 带有注解 " + annotationType.getName() + " 但未实现接口 " + interfaceType.getName()
                );
            }
            var castedBean = interfaceType.cast(bean);
            // 鲁棒性
            var annotation = clazz.getAnnotation(annotationType);
            if (annotation == null) {
                throw new InterfaceAndAnnotationInconsistencyException(
                    "Bean " + clazz.getName() + " 带有注解 " + annotationType.getName() + " 但无法获取注解实例"
                );
            }

            // 将注解实例和 Bean 实例加入结果中
            result.put(entry.getKey(), IndependentPair.of(annotation, castedBean));
        }
        return result;
    }
}
