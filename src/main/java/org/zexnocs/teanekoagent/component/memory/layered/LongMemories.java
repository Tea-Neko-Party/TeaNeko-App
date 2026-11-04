package org.zexnocs.teanekoagent.component.memory.layered;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.component.memory.history.HistoryMessage;
import org.zexnocs.teanekocore.utils.StableDigest;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.*;

/**
 * 长期整理、证据和公开召回的数据契约，内部原话不随普通召回导出。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.1-l
 * @version 4.8.0-l
 */
public final class LongMemories {
    /** 禁止实例化领域契约容器。 */
    private LongMemories() { }
    /**
     * 区分用户事实、偏好和事件摘要。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.6.1-l
     * @version 4.8.0-l
     */
    public enum Kind { FACT, PREFERENCE, EPISODE }
    /**
     * 模型只提出合并建议，实际主体、版本和覆盖由事务校验。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.6.1-l
     * @version 4.8.0-l
     */
    public enum Mode { ADD, REPLACE, CONFLICT }
    /**
     * 失效和遗忘记录保留墓碑，普通召回只选择有效或明确冲突项。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.6.1-l
     * @version 4.8.0-l
     */
    public enum Status { ACTIVE, SUPERSEDED, CONFLICT, FORGOTTEN }
    /**
     * 提供给抽取器的真实人类发言，身份由宿主产生。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param id 批次内稳定证据标识
     * @param sequence 原短期序号
     * @param message 真实用户消息
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.6.1-l
     * @version 4.8.0-l
     */
    public record Evidence(String id, long sequence, HistoryMessage message) { }
    /**
     * 模型提出的原话引用，必须在事务中与对应原消息逐字核对。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param evidenceId 宿主提供的证据标识
     * @param text 原始消息中的连续片段
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.6.1-l
     * @version 4.8.0-l
     */
    public record Quote(String evidenceId, String text) {
        /**
         * 限制引用容量，空引用不能证明事实。
         * @param evidenceId 证据标识
         * @param text 逐字引用
         * @throws IllegalArgumentException 当标识或引用非法时
         */
        public Quote {
            if (evidenceId == null || evidenceId.isBlank() || text == null || text.isBlank() || text.length() > 512) {
                throw new IllegalArgumentException("长期记忆证据引用无效。");
            }
        }
    }
    /**
     * 结构化长期候选；重要时间保留原话表达，不由模型补造精确时间点。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param kind 事实、偏好或事件
     * @param subjectId 事实主体，事件可为空
     * @param attribute 语义属性
     * @param text 简洁正文
     * @param mode 合并建议
     * @param quotes 真实发言引用
     * @param segments 中期来源标识
     * @param timeHint 可选的重要时间原表达，空字符串表示普通概略时间
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.6.1-l
     * @version 4.8.0-l
     */
    public record Candidate(Kind kind, @Nullable UUID subjectId, String attribute, String text, Mode mode,
                            List<Quote> quotes, List<UUID> segments, String timeHint) {
        /**
         * 固定有界候选，不接受模型自报权限或未定位的事实主体。
         * @param kind 记忆类型
         * @param subjectId 事实主体
         * @param attribute 语义属性
         * @param text 正文
         * @param mode 合并建议
         * @param quotes 原话引用
         * @param segments 来源片段
         * @param timeHint 重要时间原表达
         * @throws IllegalArgumentException 当候选类型、来源或容量无效时
         */
        public Candidate {
            Objects.requireNonNull(kind); Objects.requireNonNull(mode); quotes = List.copyOf(quotes); segments = List.copyOf(segments);
            if (attribute == null || attribute.isBlank() || attribute.length() > 64 || text == null || text.isBlank() || text.length() > 512
                    || timeHint == null || timeHint.length() > 128 || segments.isEmpty() || segments.size() > 4 || quotes.size() > 4
                    || kind != Kind.EPISODE && (subjectId == null || quotes.isEmpty()) || kind == Kind.EPISODE && mode != Mode.ADD) {
                throw new IllegalArgumentException("长期记忆候选结构或容量无效。");
            }
            attribute = attribute.strip().toLowerCase(Locale.ROOT); text = text.strip();
        }
    }
    /**
     * 已领取的中期整理输入，原范围和证据由宿主固定。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param stream 原分区
     * @param leaseId 原领取令牌
     * @param segments 连续中期片段
     * @param evidence 可验证的用户原话
     * @param fingerprint 整理输入指纹
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.6.1-l
     * @version 4.8.0-l
     */
    public record Batch(MemoryLayers.Stream stream, UUID leaseId, List<MemoryLayers.Segment> segments,
                        List<Evidence> evidence, String fingerprint) {
        /**
         * 拒绝空输入、过大工作量和跨分区片段。
         * @param stream 固定分区
         * @param leaseId 租约标识
         * @param segments 固定中期片段
         * @param evidence 固定真实证据
         * @param fingerprint 输入指纹
         * @throws IllegalArgumentException 当来源或容量无效时
         */
        public Batch {
            Objects.requireNonNull(stream); Objects.requireNonNull(leaseId); Objects.requireNonNull(fingerprint);
            segments = List.copyOf(segments); evidence = List.copyOf(evidence);
            if (segments.isEmpty() || segments.size() > 4 || evidence.size() > 64
                    || segments.stream().anyMatch(item -> !item.stream().equals(stream))) { throw new IllegalArgumentException("长期整理批次来源或容量无效。"); }
        }
    }
    /**
     * 权威长期记录，包含内部证据及人工锁定，不直接作为工具结果输出。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param id 稳定记录标识
     * @param kind 记忆类型
     * @param subjectId 用户主体或空
     * @param attribute 语义属性
     * @param text 当前正文
     * @param status 当前状态
     * @param revision 内容版本
     * @param locked 是否保护人工纠正
     * @param quotes 内部原话证据
     * @param sourceKeys 不含正文的真实来源摘要，原聊天到期后仍可执行遗忘抑制
     * @param segments 来源片段标识
     * @param observedAt 来源观察时刻
     * @param updatedAt 最近提交时刻
     * @param timeHint 重要时间原表达
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.6.1-l
     * @version 4.8.0-l
     */
    public record Entry(UUID id, Kind kind, @Nullable UUID subjectId, String attribute, String text, Status status, long revision,
                        boolean locked, List<Quote> quotes, List<String> sourceKeys, List<UUID> segments, Instant observedAt, Instant updatedAt, String timeHint) {
        /**
         * 冻结内部证据容器。
         * @param id 记录标识
         * @param kind 记录类型
         * @param subjectId 用户主体
         * @param attribute 语义属性
         * @param text 正文
         * @param status 状态
         * @param revision 内容版本
         * @param locked 人工锁定
         * @param quotes 内部引用
         * @param sourceKeys 原来源摘要
         * @param segments 来源标识
         * @param observedAt 来源观察时间
         * @param updatedAt 提交时间
         * @param timeHint 重要时间原表达
         */
        public Entry { quotes = List.copyOf(quotes); sourceKeys = List.copyOf(sourceKeys); segments = List.copyOf(segments); }
        /** @return 不含原始聊天引用的普通召回投影 */
        public Item item() {
            var time = timeHint.isBlank() ? "记录于大约 " + YearMonth.from(observedAt.atZone(ZoneOffset.UTC)) : timeHint;
            return new Item(id, kind, subjectId, attribute, text, status, revision, time);
        }
    }
    /**
     * 普通召回投影，明确分歧和版本，不跨 scope 导出原聊天证据。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param id 记录标识
     * @param kind 记录类型
     * @param subjectId 用户主体
     * @param attribute 语义属性
     * @param text 正文
     * @param status 当前状态
     * @param revision 当前版本
     * @param time 时间概念，不冒充事情发生时刻
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.6.1-l
     * @version 4.8.0-l
     */
    public record Item(UUID id, Kind kind, @Nullable UUID subjectId, String attribute, String text, Status status, long revision, String time) { }
    /**
     * 受检索候选和结果预算限制的读取视图。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param items 公开结果
     * @param scanned 实际检查条数
     * @param limited 是否受候选或结果预算限制
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.6.1-l
     * @version 4.8.0-l
     */
    public record Result(List<Item> items, int scanned, boolean limited) {
        /**
         * 冻结结果，部分未命中不代表历史从未存在。
         * @param items 结果列表
         * @param scanned 检查数量
         * @param limited 是否部分覆盖
         */
        public Result { items = List.copyOf(items); }
    }
    /**
     * 用于候选索引的有限文字片段，中文采用二字片段，不依赖外部分词器。
     * @param text 原查询或正文
     * @param limit 最多片段数
     * @return 去重的小写索引片段
     */
    public static List<String> terms(String text, int limit) {
        var result = new LinkedHashSet<String>();
        var matcher = java.util.regex.Pattern.compile("[\\p{L}\\p{N}]+").matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find() && result.size() < limit) {
            var value = matcher.group();
            if (value.codePoints().anyMatch(point -> Character.UnicodeScript.of(point) == Character.UnicodeScript.HAN)) {
                var points = value.codePoints().toArray();
                for (int i = 0; i + 1 < points.length && result.size() < limit; i++) { result.add(new String(points, i, 2)); }
            } else if (value.length() > 1) { result.add(value.substring(0, Math.min(64, value.length()))); }
        }
        return List.copyOf(result);
    }
    /**
     * 基于已固定输入生成指纹，不按内存对象或随机实例定位。
     * @param segments 中期片段 JSON
     * @param evidence 原证据 JSON
     * @return 稳定输入指纹
     */
    public static String fingerprint(String segments, String evidence) { return StableDigest.sha256("long-memory-v1", segments, evidence); }
}
