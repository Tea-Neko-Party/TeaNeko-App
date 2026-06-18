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
 * 学习得到的性格修正记录。
 * <br>该记录只能作为 active base personality 的补充，不能替代主性格或自定义性格。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@Getter
@Setter
@NoArgsConstructor
public class PersonalityDeltaRecord {
    /**
     * 修正记录 ID。
     */
    private String id = UUID.randomUUID().toString();

    /**
     * 修正影响的性格字段或领域。
     */
    private String field = "";

    /**
     * 修正内容。
     */
    private String content = "";

    /**
     * 修正来源，例如消息 ID、会话 ID 或手动写入标识。
     */
    private String source = "";

    /**
     * 检索标签。
     */
    private List<String> tags = new ArrayList<>();

    /**
     * 置信度，范围为 0 到 1。
     */
    private double confidence = 1.0;

    /**
     * 触发该人格修正的事件时间。
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
     * 是否被锁定。
     * <br>锁定后自动学习流程不应合并或覆盖该项。
     */
    private boolean locked = false;

    /**
     * 判断该性格修正是否已经过期。
     *
     * @param now 当前时间。
     * @return 如果已经过期则返回 true。
     */
    public boolean isExpired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }
}
