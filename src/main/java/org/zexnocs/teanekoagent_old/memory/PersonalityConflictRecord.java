package org.zexnocs.teanekoagent_old.memory;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * 被边界检查拒绝的候选性格记忆。
 * <br>该对象用于审计和调试，不参与 prompt 注入。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@Getter
@Setter
@NoArgsConstructor
public class PersonalityConflictRecord {
    /**
     * 冲突记录 ID。
     */
    private String id = UUID.randomUUID().toString();

    /**
     * 冲突影响的字段或领域。
     */
    private String field = "";

    /**
     * 被拒绝的候选内容。
     */
    private String content = "";

    /**
     * 拒绝原因。
     */
    private String reason = "";

    /**
     * 候选内容来源。
     */
    private String source = "";

    /**
     * 触发冲突候选内容的事件时间。
     */
    private MemoryTimeRange eventTime = new MemoryTimeRange();

    /**
     * 创建时间。
     */
    private Instant createdAt = Instant.now();
}
