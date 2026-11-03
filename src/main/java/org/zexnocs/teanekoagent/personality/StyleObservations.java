package org.zexnocs.teanekoagent.personality;

import org.zexnocs.teanekoagent.agent.personality.AgentPersonalitySnapshot;
import org.zexnocs.teanekoagent.memory.layered.LongMemories;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 人类表达观察的数据契约，目标由宿主固定，模型只能提出带原话的风格候选。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.1-l
 * @version 4.6.1-l
 */
public final class StyleObservations {
    /** 禁止实例化契约容器。 */
    private StyleObservations() { }
    /**
     * 表达适用场景，不把针对某用户的请求推广到整个群。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.1-l
     * @version 4.6.1-l
     */
    public enum Context { GENERAL, TECHNICAL, CASUAL }
    /**
     * 待验证的表达模式，不包含基础身份、权限和采用指令。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param field 允许微调的表达字段
     * @param pattern 简洁表达特征
     * @param context 适用场景
     * @param quotes 真实用户原话
     * @param segments 对应中期片段
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.1-l
     * @version 4.6.1-l
     */
    public record Candidate(String field, String pattern, Context context, List<LongMemories.Quote> quotes, List<UUID> segments) {
        /**
         * 固定有界候选，关系、经历和个人偏好不能作为群体表达字段。
         * @param field 表达字段
         * @param pattern 模式正文
         * @param context 适用场景
         * @param quotes 原话引用
         * @param segments 片段来源
         * @throws IllegalArgumentException 当字段、来源或范围无效时
         */
        public Candidate {
            Objects.requireNonNull(context); quotes = List.copyOf(quotes); segments = List.copyOf(segments);
            if (!Set.of("speakingStyle", "speaking detail").contains(field) || pattern == null || pattern.isBlank() || pattern.length() > 256
                    || quotes.isEmpty() || quotes.size() > 4 || segments.isEmpty() || segments.size() > 4) {
                throw new IllegalArgumentException("表达观察字段、来源或容量无效。");
            }
            pattern = pattern.strip();
        }
    }
    /**
     * 宿主固定的学习目标，已有模式帮助增量观察保持命名一致。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param basis 当前人格身份及管理代次
     * @param fields 代码与配置共同允许的字段
     * @param evidenceIds 仍属于该人格的真实消息标识
     * @param known 已有模式摘要，不包含原话
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.1-l
     * @version 4.6.1-l
     */
    public record Target(AgentPersonalitySnapshot basis, Set<String> fields, Set<String> evidenceIds, List<String> known) {
        /**
         * 冻结已复核目标，模型不能修改绑定或扩大白名单。
         * @param basis 人格快照
         * @param fields 合法字段
         * @param evidenceIds 合格原话标识
         * @param known 既有表达模式
         */
        public Target { Objects.requireNonNull(basis); fields = Set.copyOf(fields); evidenceIds = Set.copyOf(evidenceIds); known = List.copyOf(known); }
    }
    /**
     * 一次模型同时返回事实与表达观察，两类候选分别验证和采用。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param memories 长期事实候选
     * @param styles 表达观察候选
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.1-l
     * @version 4.6.1-l
     */
    public record Analysis(List<LongMemories.Candidate> memories, List<Candidate> styles) {
        /**
         * 限制单批变化量，不允许生成器返回无界观察。
         * @param memories 长期候选
         * @param styles 表达候选
         * @throws IllegalArgumentException 当候选数量超过预算时
         */
        public Analysis {
            memories = List.copyOf(memories); styles = List.copyOf(styles);
            if (memories.size() > 8 || styles.size() > 2) { throw new IllegalArgumentException("联合整理候选超过预算。"); }
        }
    }
    /**
     * 当前 scope 的观察摘要，不导出原始发言或其他区域样本。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param field 表达字段
     * @param pattern 已观察模式
     * @param context 适用场景
     * @param messages 独立消息数
     * @param segments 独立片段数
     * @param authors 样本作者 UUID
     * @param adopted 是否已经采用
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.6.1-l
     * @version 4.6.1-l
     */
    public record View(String field, String pattern, Context context, int messages, int segments, Set<UUID> authors, boolean adopted) { }
}
