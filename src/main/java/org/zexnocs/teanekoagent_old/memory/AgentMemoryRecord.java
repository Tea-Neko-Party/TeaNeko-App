package org.zexnocs.teanekoagent_old.memory;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Agent 长期记忆记录。
 * <br>该对象会以 JSON 形式写入 EasyData 的 {@code o_value} 字段。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@Getter
@Setter
@NoArgsConstructor
public class AgentMemoryRecord {
    /**
     * 记忆记录 ID。
     */
    private String id = UUID.randomUUID().toString();

    /**
     * 记忆类型。
     */
    private MemoryRecordType type = MemoryRecordType.USER_FACT;

    /**
     * 记忆正文。
     * <br>该字段应保存已经清洗、适合注入 prompt 的短文本。
     */
    private String content = "";

    /**
     * 记忆来源，例如消息 ID、会话 ID 或手动写入标识。
     */
    private String source = "";

    /**
     * 记忆所属作用域 ID。
     */
    private String scopeId = "";

    /**
     * 记忆所属 agent ID。
     */
    private String agentId = "";

    /**
     * 记忆主体 ID，例如用户 ID 或群 ID。
     */
    private String subjectId = "";

    /**
     * 检索标签。
     */
    private List<String> tags = new ArrayList<>();

    /**
     * 记忆置信度，范围为 0 到 1。
     */
    private double confidence = 1.0;

    /**
     * 记忆重要度。
     * <br>高重要度和关键记忆应尽量保存更精确的事件时间与来源。
     */
    private MemoryImportance importance = MemoryImportance.NORMAL;

    /**
     * 记忆正文所描述事件的时间点或时间范围。
     * <br>该时间与记录创建时间不同；例如今天记录的“一周前发生的事情”，事件时间应指向一周前。
     */
    private MemoryTimeRange eventTime = new MemoryTimeRange();

    /**
     * 创建时间。
     */
    private Instant createdAt = Instant.now();

    /**
     * 更新时间。
     */
    private Instant updatedAt = Instant.now();

    /**
     * 过期时间。
     * <br>为空表示不过期。
     */
    @Nullable
    private Instant expiresAt = null;

    /**
     * 是否被管理员或系统锁定。
     * <br>锁定后学习流程不应自动覆盖该记忆。
     */
    private boolean locked = false;

    /**
     * 判断该记忆是否已经过期。
     *
     * @param now 当前时间。
     * @return 如果已经过期则返回 true。
     */
    public boolean isExpired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    /**
     * 判断记忆事件时间是否与查询范围相交。
     * <br>旧记录没有事件时间时使用创建时间作为兼容回退。
     *
     * @param start 查询起点；为空表示不限制。
     * @param end   查询终点；为空表示不限制。
     * @return 如果时间相交则返回 {@code true}。
     */
    public boolean occursWithin(@Nullable Instant start, @Nullable Instant end) {
        var range = eventTime == null ? new MemoryTimeRange() : eventTime;
        return range.overlaps(start, end, createdAt);
    }
}
