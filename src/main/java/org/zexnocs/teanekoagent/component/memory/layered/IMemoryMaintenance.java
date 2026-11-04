package org.zexnocs.teanekoagent.component.memory.layered;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import java.time.Instant;
import java.util.concurrent.CompletionStage;

/**
 * 持久记忆维护端口，每次有界推进，清理关闭不改变数据的逻辑有效期。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.1-l
 * @version 4.8.0-l
 */
public interface IMemoryMaintenance {
    /**
     * 维护一页数据，进度与删除或精简在同一事务提交。
     * @param now 当前时间
     * @return 本批实际删除或精简的业务记录数量
     */
    CompletionStage<Integer> maintain(Instant now);
}
