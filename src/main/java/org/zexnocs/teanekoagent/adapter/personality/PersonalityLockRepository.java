package org.zexnocs.teanekoagent.adapter.personality;

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
 * 人格绑定与共享补正的专用行锁，按绑定后补正的顺序持有至事务提交。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface PersonalityLockRepository extends Repository<GeneralEasyDataObject, Long> {
    /**
     * 在当前事务锁定最新行；首次创建仍由唯一键及原 DatabaseTask 重试仲裁。
     * @param target 本域物理目标
     * @param key 固定物理键
     * @return 当前行，尚不存在时为空
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Transactional(propagation = Propagation.MANDATORY)
    @Query("select metadata from GeneralEasyDataObject metadata where metadata.namespace = 'agent.personality.v1' and metadata.target = :target and metadata.key = :key")
    @Nullable GeneralEasyDataObject lock(@Param("target") String target, @Param("key") String key);
}
