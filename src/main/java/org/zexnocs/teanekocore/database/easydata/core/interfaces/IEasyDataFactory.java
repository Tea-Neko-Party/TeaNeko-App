package org.zexnocs.teanekocore.database.easydata.core.interfaces;

/**
 * 数据工厂接口，用于创建数据对象。
 *
 * @author zExNocs
 * @date 2026/02/15
 */
@FunctionalInterface
public interface IEasyDataFactory<T> {
    /**
     * 创建一个数据对象。
     * @param namespace 命名空间。
     * @param target 对象。
     * @param key 键。
     * @param value 值。
     * @return 数据对象。
     */
    T create(String namespace, String target, String key, String value);
}
