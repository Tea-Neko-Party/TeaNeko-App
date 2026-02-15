package org.zexnocs.teanekocore.database.easydata;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.transaction.annotation.Transactional;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataFactory;

import java.util.List;

/**
 * 一个基础的 EasyDataRepository 接口
 * 使用方法：
 * 1. 加上 @Repository 注解。
 * 2. 重写 getFactory 方法，返回一个IEasyDataFactory的实现类。
 *
 * @param <T> 对应的 EasyDataObject
 * @author zExNocs
 * @date 2026/02/15
 */
@NoRepositoryBean
public interface BaseEasyDataRepository<T extends BaseEasyDataObject>
        extends JpaRepository<T, Long>, IEasyDataFactory<T> {
    /**
     * 通过命名空间和对象获取数据对象。
     * @param namespace 命名空间。
     * @param target 对象。
     * @return 数据对象。
     */
    List<T> findByNamespaceAndTarget(String namespace, String target);

    /**
     * 通过命名空间和对象和键获取数据对象的值。
     * @param namespace 命名空间。
     * @param target 对象。
     * @param key 键。
     */
    T findByNamespaceAndTargetAndKey(String namespace, String target, String key);

    /**
     * 通过命名空间和对象和键来删除数据对象的值。
     * @param namespace 命名空间。
     * @param target 对象。
     * @param key 键。
     */
    @Transactional
    void deleteByNamespaceAndTargetAndKey(String namespace, String target, String key);

    /**
     * 通过命名空间和对象和键来修改数据对象的值。
     * @param namespace 命名空间。
     * @param target 对象。
     * @param key 键。
     * @param value 值。
     */
    @Transactional
    default void updateDataObjectData (String namespace,
                                       String target,
                                       String key,
                                       String value) {
        // 如果值为空则删除数据对象。
        if (value == null) {
            deleteByNamespaceAndTargetAndKey(namespace, target, key);
            return;
        }
        T existing = findByNamespaceAndTargetAndKey(namespace, target, key);
        if (existing == null) {
            // 如果数据对象不存在则创建一个新的数据对象。
            existing = create(namespace, target, key, value);
        } else {
            // 如果数据对象存在则修改数据对象的值。
            existing.setValue(value);
        }
        // 可以会抛出唯一冲突和乐观锁冲突，不过会在重试层处理。
        save(existing);
    }
}
