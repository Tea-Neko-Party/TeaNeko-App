package org.zexnocs.teanekocore.database.easydata;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import jakarta.persistence.LockModeType;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.zexnocs.teanekocore.database.easydata.core.interfaces.IEasyDataFactory;

import java.util.List;

/**
 * 一个基础的 EasyDataRepository 接口
 * 使用方法：
 * 1. 加上 @Repository 注解。
 * 2. 重写 getFactory 方法，返回一个IEasyDataFactory的实现类。
 *
 * <br>2026/11/03：为基础层维护任务增加按域和行序号的有界分页。
 * @param <T> 对应的 EasyDataObject
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.0.0
 * @version 4.6.1-l
 */
@NoRepositoryBean
public interface IBaseEasyDataRepository<T extends BaseEasyDataObject>
        extends JpaRepository<T, Long>, IEasyDataFactory<T> {
    /** 精确行查询仅供 EasyData 事务基础层使用。 */
    String ROW_QUERY = "select item from #{#entityName} item where item.namespace = :namespace and item.target = :target and item.key = :key";
    /** 固定目标的有界键扫描，不使用全表读取。 */
    String RANGE_QUERY = "select item from #{#entityName} item where item.namespace = :namespace and item.target = :target and item.key > :after and (:through is null or item.key <= :through) order by item.key asc";
    /**
     * 获取当前行并持有读锁，外层事务必须已存在。
     * @param namespace 域
     * @param target 目标
     * @param key 键
     * @return 当前行或空
     */
    @Lock(LockModeType.PESSIMISTIC_READ) @Transactional(propagation = Propagation.MANDATORY) @Query(ROW_QUERY)
    @Nullable T lockDataForRead(@Param("namespace") String namespace, @Param("target") String target, @Param("key") String key);
    /**
     * 获取当前行并持有写锁，锁持续到外层事务结束。
     * @param namespace 域
     * @param target 目标
     * @param key 键
     * @return 当前行或空
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Transactional(propagation = Propagation.MANDATORY) @Query(ROW_QUERY)
    @Nullable T lockDataForWrite(@Param("namespace") String namespace, @Param("target") String target, @Param("key") String key);
    /**
     * 按键快照分页，页面大小由事务 DTO 强制收束。
     * @param namespace 域
     * @param target 目标
     * @param after 排除下界
     * @param through 可选包含上界
     * @param page 有界页面
     * @return 键升序页面
     */
    @Transactional(propagation = Propagation.MANDATORY) @Query(RANGE_QUERY)
    List<T> scanData(@Param("namespace") String namespace, @Param("target") String target, @Param("after") String after, @Param("through") @Nullable String through, Pageable page);
    /**
     * 持读锁扫描当前页，避免既有一致性快照遮蔽较晚提交。
     * @param namespace 域
     * @param target 目标
     * @param after 排除下界
     * @param through 可选包含上界
     * @param page 有界页面
     * @return 当前读页面
     */
    @Lock(LockModeType.PESSIMISTIC_READ) @Transactional(propagation = Propagation.MANDATORY) @Query(RANGE_QUERY)
    List<T> scanDataForRead(@Param("namespace") String namespace, @Param("target") String target, @Param("after") String after, @Param("through") @Nullable String through, Pageable page);
    /**
     * 持写锁扫描待处理页，版本修改和删除仍在同一事务提交。
     * @param namespace 域
     * @param target 目标
     * @param after 排除下界
     * @param through 可选包含上界
     * @param page 有界页面
     * @return 当前读页面
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Transactional(propagation = Propagation.MANDATORY) @Query(RANGE_QUERY)
    List<T> scanDataForWrite(@Param("namespace") String namespace, @Param("target") String target, @Param("after") String after, @Param("through") @Nullable String through, Pageable page);
    /**
     * 使用目标和行序号索引获取近期页面。
     * @param namespace 域
     * @param target 目标
     * @param before 排除序号
     * @param page 有界页面
     * @return 序号倒序页面
     */
    @Transactional(propagation = Propagation.MANDATORY)
    List<T> findByNamespaceAndTargetAndIdLessThanOrderByIdDesc(String namespace, String target, long before, Pageable page);
    /**
     * 2026/11/03：维护扫描只读固定域，不对未取得业务锁的记录加锁。
     * @param namespace 固定域
     * @param before 排除序号
     * @param page 有界页面
     * @return 序号倒序页
     */
    @Transactional(propagation = Propagation.MANDATORY)
    List<T> findByNamespaceAndIdLessThanOrderByIdDesc(String namespace, long before, Pageable page);
    /**
     * 通过命名空间和对象获取数据对象。
     * @param namespace 命名空间。
     * @param target 对象。
     * @return 固定目标内的记录，仅供基础层缓存加载
     */
    List<T> findByNamespaceAndTarget(String namespace, String target);

    /**
     * 通过命名空间和对象和键获取数据对象的值。
     * @param namespace 命名空间。
     * @param target 对象。
     * @param key 键。
     * @return 原记录或空
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
