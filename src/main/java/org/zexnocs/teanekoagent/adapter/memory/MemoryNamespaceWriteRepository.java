package org.zexnocs.teanekoagent.adapter.memory;

import jakarta.persistence.LockModeType;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.zexnocs.teanekocore.database.easydata.general.GeneralEasyDataObject;

/**
 * 事实写入与检索的 namespace 行锁入口，与换代更新同一元数据行，锁持续到外层 DatabaseTask 事务结束。
 * 与通用仓库分开声明，不扩大 Core 的业务 API，也不在应用锁内访问数据库。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface MemoryNamespaceWriteRepository extends Repository<GeneralEasyDataObject, Long> {
    /**
     * 获取当前已提交元数据并持有写锁，阻止校验后、事实提交前的 clear 换代穿透。
     * @param id 业务空间 ID
     * @return 当前管理行，空间尚未登记时为空
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Transactional(propagation = Propagation.MANDATORY)
    @Query("select metadata from GeneralEasyDataObject metadata where metadata.namespace = 'agent.namespace.v1' and metadata.target = 'namespace' and metadata.key = :id")
    @Nullable GeneralEasyDataObject lockNamespace(@Param("id") String id);

    /**
     * 为一次有界多页检索固定空间代次与记录集合，阻止追加或 clear 穿透扫描边界。
     * @param id 业务空间 ID
     * @return 当前共享锁元数据，缺失时为空；锁持有至外层事务结束
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Transactional(propagation = Propagation.MANDATORY)
    @Query("select metadata from GeneralEasyDataObject metadata where metadata.namespace = 'agent.namespace.v1' and metadata.target = 'namespace' and metadata.key = :id")
    @Nullable GeneralEasyDataObject lockSnapshot(@Param("id") String id);
}
