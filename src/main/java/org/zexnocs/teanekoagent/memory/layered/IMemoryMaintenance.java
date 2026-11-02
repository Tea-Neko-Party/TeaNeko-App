package org.zexnocs.teanekoagent.memory.layered;

import java.time.Instant;
import java.util.concurrent.CompletionStage;

/**
 * 持久记忆维护端口，每次有界推进，清理关闭不改变数据的逻辑有效期。
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.1-l
 */
public interface IMemoryMaintenance {
    /**
     * 维护一页数据，进度与删除或精简在同一事务提交。
     * @param now 当前时间
     * @return 本批实际删除或精简的业务记录数量
     */
    CompletionStage<Integer> maintain(Instant now);
}
