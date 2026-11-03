package org.zexnocs.teanekocore.framework.extension;

import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.lang.annotation.Annotation;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * 按声明键注册已有 Spring Bean，保留代理并拒绝重复声明。
 * @param <K> 稳定路由键
 * @param <T> 扩展接口
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.1-l
 */
public final class AnnotatedBeanRegistry<K,T> {
    private final Map<K,Entry<T>> entries;
    /**
     * 扫描一次注解和接口，不手动实例化实现。
     * @param scanner 原扫描器
     * @param annotation 声明注解
     * @param contract 实际接口
     * @param key 业务键映射
     * @param <A> 注解类型
     * @throws IllegalArgumentException 当业务键重复时
     */
    public <A extends Annotation> AnnotatedBeanRegistry(IBeanScanner scanner, Class<A> annotation, Class<T> contract, Function<A,K> key) {
        var found = new LinkedHashMap<K,Entry<T>>();
        scanner.getBeansWithAnnotationAndInterface(annotation, contract).entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(value -> {
            var pair = value.getValue(); var route = Objects.requireNonNull(key.apply(pair.first()), "扩展路由键不能为空。");
            var entry = new Entry<>(value.getKey(), scanner.getBeanClass(pair.second()), pair.second());
            if (found.putIfAbsent(route, entry) != null) { throw new IllegalArgumentException("扩展声明键重复：" + route + "，Bean：" + value.getKey()); }
        });
        entries = Map.copyOf(found);
    }
    /** @return 已冻结目录，不重新扫描 */
    public Map<K,Entry<T>> entries() { return entries; }
    /**
     * 按精确键取得原容器实现。
     * @param key 业务键
     * @return 已登记实现
     * @throws IllegalArgumentException 当实现未注册时
     */
    public Entry<T> require(K key) {
        var entry = entries.get(key); if (entry == null) { throw new IllegalArgumentException("未注册的扩展：" + key); } return entry;
    }
    /**
 * 声明来源及原容器代理。
 * @param name Bean 名称
 * @param type 目标类型
 * @param bean 原 Bean
 * @param <T> 接口类型
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.1-l
 */
    public record Entry<T>(String name, Class<?> type, T bean) {}
}
