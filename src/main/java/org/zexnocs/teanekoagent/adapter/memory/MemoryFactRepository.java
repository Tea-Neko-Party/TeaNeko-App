package org.zexnocs.teanekoagent.adapter.memory;

import jakarta.persistence.LockModeType;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.zexnocs.teanekocore.database.easydata.BaseEasyDataRepository;

import java.util.List;

/**
 * 事实及操作回执的物理仓库，仅按指定域、空间代次和稳定游标查询。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Repository
public interface MemoryFactRepository extends BaseEasyDataRepository<MemoryFactDataObject> {
    /**
     * 构造新行，更新仍须使用事务读取的原始 Version 实体。
     * @param namespace 物理域
     * @param target 物理目标
     * @param key 稳定键
     * @param value 显式 JSON
     * @return 未持久化行
     */
    @Override default MemoryFactDataObject create(String namespace, String target, String key, String value) {
        return new MemoryFactDataObject(namespace, target, key, value);
    }
    /**
     * 按记录 ID 有界扫描，不读取整个表或先全量加载再分页。
     * @param namespace 事实存储域
     * @param target 固定空间和代次
     * @param key 前页最后 ID，首次为空字符串
     * @param page 当前有界页面
     * @return 稳定键升序的原始行
     */
    List<MemoryFactDataObject> findByNamespaceAndTargetAndKeyGreaterThanOrderByKeyAsc(String namespace, String target, String key, Pageable page);

    /**
     * 导入时锁定真实旧行并读取最新 Version，与仍在运行的旧列表写入互斥至事务提交。
     * @param target 明确旧目标
     * @param key 明确旧画像键
     * @return 当前原始行，缺失时为空
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Transactional(propagation = Propagation.MANDATORY)
    @Query("select profile from MemoryFactDataObject profile where profile.namespace = 'memory' and profile.target = :target and profile.key = :key")
    @Nullable MemoryFactDataObject lockLegacyProfile(@Param("target") String target, @Param("key") String key);
}
