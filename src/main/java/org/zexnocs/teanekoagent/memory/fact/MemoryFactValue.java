package org.zexnocs.teanekoagent.memory.fact;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/**
 * 可独立演进的事实/偏好内容，来源、空间与主语由追加命令单独固定。
 * @param kind 内容类别
 * @param text 短文本正文，保留原文以避免隐式语义改写
 * @param tags 有界检索标签
 * @param confidence 来源置信度
 * @param importance 重要度
 * @param eventTime 事件时间
 * @param expiresAt 可选有效期
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record MemoryFactValue(Kind kind, String text, List<String> tags, double confidence,
        Importance importance, MemoryEventTime eventTime, @Nullable Instant expiresAt) {
    /**
     * 校验有界数据并按稳定顺序冻结标签；不推断用户没有明确提供的事实。
     * @param kind 内容类别
     * @param text 正文
     * @param tags 标签
     * @param confidence 有限置信度
     * @param importance 重要度
     * @param eventTime 事件时间
     * @param expiresAt 可选有效期
     * @throws NullPointerException 当必需字段为空时
     * @throws IllegalArgumentException 当正文、标签或置信度越界时
     */
    public MemoryFactValue {
        Objects.requireNonNull(kind, "记忆类别不能为空。"); Objects.requireNonNull(text, "记忆正文不能为空。");
        Objects.requireNonNull(importance, "记忆重要度不能为空。"); Objects.requireNonNull(eventTime, "记忆事件时间不能为空。");
        var ordered = new TreeSet<>(List.copyOf(tags));
        if (text.isBlank() || text.length() > 4096 || tags.size() > 16 || ordered.stream().anyMatch(tag -> tag.isBlank() || tag.length() > 64)
                || !Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("记忆正文、标签或置信度超出允许范围。");
        }
        tags = List.copyOf(ordered);
        // 浮点负零与正零表示相同置信度，避免完全重复判断产生两种内容摘要。
        if (confidence == 0) { confidence = 0; }
    }

    /**
     * 当前可追加的明确事实类别，人格补正和自动摘要由独立能力管理。
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public enum Kind { FACT, PREFERENCE }

    /**
     * 独立于置信度的内容重要度，检索排序可另行选择策略。
     * @author zExNocs
     * @date 2026/10/29
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public enum Importance { LOW, NORMAL, HIGH, CRITICAL }
}
