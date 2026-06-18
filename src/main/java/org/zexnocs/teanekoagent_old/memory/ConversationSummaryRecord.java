package org.zexnocs.teanekoagent_old.memory;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 滚动会话摘要记忆。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@Getter
@Setter
@NoArgsConstructor
public class ConversationSummaryRecord {
    /**
     * 短摘要。
     */
    private String brief = "";

    /**
     * 详细摘要。
     */
    private String detail = "";

    /**
     * 摘要覆盖的消息数量。
     */
    private int messageCount = 0;

    /**
     * 摘要覆盖的会话时间范围。
     */
    private MemoryTimeRange coveredTime = new MemoryTimeRange();

    /**
     * 摘要创建时间。
     */
    private Instant createdAt = Instant.now();

    /**
     * 更新时间。
     */
    private Instant updatedAt = Instant.now();
}
