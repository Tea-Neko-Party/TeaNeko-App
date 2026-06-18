package org.zexnocs.teanekoagent_old.memory;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent 与某个主体之间的关系记忆。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@Getter
@Setter
@NoArgsConstructor
public class RelationshipMemoryRecord {
    /**
     * 关系主体 ID，通常是用户 ID。
     */
    private String subjectId = "";

    /**
     * 主体展示名称。
     */
    private String displayName = "";

    /**
     * 关系描述。
     */
    private String relationship = "";

    /**
     * 偏好的称呼。
     */
    private String preferredAddress = "";

    /**
     * 亲近度或好感度。
     */
    private double affinity = 0;

    /**
     * 关系备注。
     */
    private List<String> notes = new ArrayList<>();

    /**
     * 关系建立或首次被观察到的时间。
     */
    private MemoryTimeRange relationshipTime = new MemoryTimeRange();

    /**
     * 记录创建时间。
     */
    private Instant createdAt = Instant.now();

    /**
     * 更新时间。
     */
    private Instant updatedAt = Instant.now();
}
