package org.zexnocs.teanekoagent_old.response;

import org.zexnocs.teanekoagent_old.memory.AgentMemoryRecord;

/**
 * Agent 手动记忆写入响应。
 *
 * @param record 已写入的记忆记录
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
public record AgentMemoryWriteResponse(AgentMemoryRecord record) {
}
