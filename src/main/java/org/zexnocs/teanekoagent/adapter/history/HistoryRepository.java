package org.zexnocs.teanekoagent.adapter.history;

import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

/**
 * 历史仓库只按指定分区分页或按到期索引清理，不提供业务授权。
 * @author zExNocs
 * @date 2026/10/30
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface HistoryRepository extends JpaRepository<HistoryDataObject, Long> {
    /**
     * 读取同分区稳定消息键以处理幂等，首次并发由唯一键及原事务重试仲裁。
     * @param partition 当前分区
     * @param messageKey 消息键
     * @return 已有行或空
     */
    @Nullable HistoryDataObject findByPartitionAndMessageKey(String partition, String messageKey);
    /**
     * 读取固定数量的近期行，未过期过滤由当前时间强制执行。
     * @param partition 当前分区
     * @param before 上一页序号
     * @param now 当前时刻
     * @param page 有界页
     * @return 倒序行
     */
    List<HistoryDataObject> findByPartitionAndIdLessThanAndExpiresAtGreaterThanOrderByIdDesc(String partition, long before, Instant now, Pageable page);
    /**
     * 按到期索引取得一批待清理记录。
     * @param now 到期上界，包含等于
     * @param page 固定批大小
     * @return 待清理行
     */
    List<HistoryDataObject> findByExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(Instant now, Pageable page);
}
