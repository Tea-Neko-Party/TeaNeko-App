package org.zexnocs.teanekoagent_old.response;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent_old.memory.PersonalityDeltaRecord;

/**
 * Agent 手动人格修正响应。
 *
 * @param accepted 修正是否被人格边界策略接受
 * @param record 已写入的修正记录；拒绝时为空
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
public record AgentPersonalityCorrectionResponse(
        boolean accepted,
        @Nullable PersonalityDeltaRecord record
) {
}
