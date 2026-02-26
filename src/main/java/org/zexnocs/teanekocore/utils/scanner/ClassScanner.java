package org.zexnocs.teanekocore.utils.scanner;

import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.stereotype.Service;
import org.springframework.util.ClassUtils;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IClassScanner;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 类扫描器，扫描不用定义为 bean 的类与其注解。
 *
 * @author zExNocs
 * @date 2026/02/27
 * @since 4.0.10
 */
@Service
public class ClassScanner implements IClassScanner {
    /**
     * 基础包列表，从 Spring Boot 自动配置包中获取。
     */
    private final List<String> basePackages;
    private final ILogger logger;

    public ClassScanner(ApplicationContext context, ILogger logger) {
        this.basePackages = AutoConfigurationPackages.get(context);
        this.logger = logger;
    }


    /**
     * 获取所有带有指定注解的 classes
     *
     * @param annotationType 需要扫描的注解类型
     * @return 一个包含所有带有指定注解的 class 的 Map，键为 class ，值为注解实例
     */
    @Override
    public <A extends Annotation> Map<Class<?>, A> getClassesWithAnnotation(Class<A> annotationType) {
        var result = new HashMap<Class<?>, A>();
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(annotationType));
        for (var basePackage : basePackages) {
            for (var beanDefinition : scanner.findCandidateComponents(basePackage)) {
                try {
                    var clazz = ClassUtils.forName(Objects.requireNonNull(beanDefinition.getBeanClassName()), null);
                    var annotation = clazz.getAnnotation(annotationType);
                    if (annotation != null) {
                        result.put(clazz, annotation);
                    } else {
                        logger.warn(this.getClass().getSimpleName(),
                                "扫描类时发现类 %s 没有注解 %s，但它被扫描到了。"
                                        .formatted(clazz.getName(), annotationType.getName()));
                    }
                } catch (ClassNotFoundException | NoClassDefFoundError e) {
                    logger.error(this.getClass().getSimpleName(),
                            "扫描类失败，类名：%s".formatted(beanDefinition.getBeanClassName()),
                            e);
                }
            }
        }
        return result;
    }

    /**
     * 获取所有带有指定注解的 classes，并且要求带有该注解的 classes 都实现了指定接口
     *
     * @param annotationType 需要扫描的注解类型
     * @param interfaceType  需要扫描的接口类型
     * @return 一个包含所有带有指定注解的 classes 的 Map，键为 classes 的名称，值为一个 Pair 对象，包含注解实例和 classes 实例
     */
    @Override
    public <A extends Annotation, T>
    Map<Class<? extends T>, A> getClassesWithAnnotationAndInterface(Class<A> annotationType,
                                                                    Class<T> interfaceType) {

        var result = new HashMap<Class<? extends T>, A>();
        var scanner = new ClassPathScanningCandidateComponentProvider(false);

        // 添加两个 OR 的过滤器，满足任意一个即可被扫描到
        scanner.addIncludeFilter(new AnnotationTypeFilter(annotationType));
        scanner.addIncludeFilter(new AssignableTypeFilter(interfaceType));

        for (var basePackage : basePackages) {
            for (var beanDefinition: scanner.findCandidateComponents(basePackage)) {
                try {
                    var clazz = ClassUtils.forName(Objects.requireNonNull(beanDefinition.getBeanClassName()), null);
                    var annotation = clazz.getAnnotation(annotationType);
                    // 如果是接口、抽象类、没有实现接口或者没有注解，则跳过
                    if (clazz.isInterface()
                            || Modifier.isAbstract(clazz.getModifiers())
                            || !interfaceType.isAssignableFrom(clazz)
                            ||  annotation == null) {
                        continue;
                    }
                    result.put(clazz.asSubclass(interfaceType), annotation);
                }
                catch (Throwable e) {
                    logger.error("ClassScanner", "扫描失败: %s".formatted(beanDefinition.getBeanClassName()), e);
                }
            }
        }
        return result;
    }
}
