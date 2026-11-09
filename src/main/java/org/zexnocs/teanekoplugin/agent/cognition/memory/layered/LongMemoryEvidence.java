package org.zexnocs.teanekoplugin.agent.cognition.memory.layered;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.runtime.AgentActorContext;
import org.zexnocs.teanekoplugin.agent.cognition.memory.history.HistoryMessage;
import org.zexnocs.teanekoplugin.agent.cognition.personality.StyleObservations;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.zexnocs.teanekoplugin.agent.cognition.memory.layered.MemoryEvidenceException.Code.EVIDENCE_ID_INVALID;
import static org.zexnocs.teanekoplugin.agent.cognition.memory.layered.MemoryEvidenceException.Code.EVIDENCE_ROLE_INVALID;
import static org.zexnocs.teanekoplugin.agent.cognition.memory.layered.MemoryEvidenceException.Code.QUOTE_MISMATCH;
import static org.zexnocs.teanekoplugin.agent.cognition.memory.layered.MemoryEvidenceException.Code.SEGMENT_MISMATCH;
import static org.zexnocs.teanekoplugin.agent.cognition.memory.layered.MemoryEvidenceException.Code.SEGMENT_OUTSIDE_BATCH;
import static org.zexnocs.teanekoplugin.agent.cognition.memory.layered.MemoryEvidenceException.Code.STYLE_TARGET_MISMATCH;
import static org.zexnocs.teanekoplugin.agent.cognition.memory.layered.MemoryEvidenceException.Code.SUBJECT_MISMATCH;
import static org.zexnocs.teanekoplugin.agent.cognition.memory.layered.MemoryEvidenceException.Code.TIME_HINT_MISMATCH;
import static org.zexnocs.teanekoplugin.agent.cognition.memory.layered.MemoryEvidenceException.Domain.MEMORIES;
import static org.zexnocs.teanekoplugin.agent.cognition.memory.layered.MemoryEvidenceException.Domain.STYLES;

/**
 * 生成预检与事务共用的纯证据关系校验；不查询数据库，也不修改模型候选。
 * <br>2026/11/05：统一原话、作者、片段与风格目标关系，返回待复核的权威来源定位。
 * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
 * <br>2026/11/08：风格描述中的字面举例也必须存在于本次引用的真实原话。
 *
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.3-l
 * @version 4.9.0-l
 */
public final class LongMemoryEvidence {
    private static final java.util.regex.Pattern STYLE_EXAMPLE =
            java.util.regex.Pattern.compile("[「“]([^」”]+)[」”]");
    /** 禁止实例化无状态校验器。 */
    private LongMemoryEvidence() { }

    /**
     * 校验完整批次，不把非法项过滤成空成功。
     * @param batch 宿主固定输入
     * @param analysis 完整候选
     * @param target 固定观察目标，没有观察时为空
     * @return 仍须在事务中复核的原证据使用位置
     * @throws MemoryEvidenceException 当候选与固定输入关系不一致时
     */
    public static List<Use> validate(LongMemories.Batch batch, StyleObservations.Analysis analysis,
            StyleObservations.@Nullable Target target) {
        var uses = new ArrayList<Use>();
        for (int index = 0; index < analysis.memories().size(); index++) {
            var candidate = analysis.memories().get(index);
            var location = new Use(MEMORIES, index, null);
            var segments = segments(batch, candidate.segments(), location);
            if (candidate.kind() == LongMemories.Kind.EPISODE && candidate.subjectId() != null) {
                throw location.failure(batch, SUBJECT_MISMATCH);
            }
            for (var quote : candidate.quotes()) {
                var use = quote(batch, segments, quote, location);
                if (candidate.kind() != LongMemories.Kind.EPISODE
                        && !use.source().message().source().userId().orElseThrow().equals(candidate.subjectId())) {
                    throw use.failure(batch, SUBJECT_MISMATCH);
                }
                uses.add(use);
            }
            if (!candidate.timeHint().isBlank()
                    && candidate.quotes().stream().noneMatch(value -> value.text().contains(candidate.timeHint()))) {
                throw location.failure(batch, TIME_HINT_MISMATCH);
            }
        }
        for (int index = 0; index < analysis.styles().size(); index++) {
            var candidate = analysis.styles().get(index);
            var location = new Use(STYLES, index, null);
            if (target == null || !target.fields().contains(candidate.field())) {
                throw location.failure(batch, STYLE_TARGET_MISMATCH);
            }
            var segments = segments(batch, candidate.segments(), location);
            for (var quote : candidate.quotes()) {
                var use = quote(batch, segments, quote, location);
                if (!target.evidenceIds().contains(quote.evidenceId())) {
                    throw use.failure(batch, STYLE_TARGET_MISMATCH);
                }
                uses.add(use);
            }
            // 2026/11/08：合法 quotes 不能掩护 pattern 中编造的字面示例。
            var examples = STYLE_EXAMPLE.matcher(candidate.pattern());
            while (examples.find()) {
                var example = examples.group(1);
                if (candidate.quotes().stream().noneMatch(quote -> quote.text().contains(example))) {
                    throw location.failure(batch, QUOTE_MISMATCH);
                }
            }
        }
        return List.copyOf(uses);
    }

    /**
     * 解析本批真实片段，不接受其他批次的合法 UUID。
     * @param batch 固定输入
     * @param selected 候选选择的标识
     * @param location 候选位置
     * @return 实际片段
     * @throws MemoryEvidenceException 当片段不属于本批时
     */
    private static List<MemoryLayers.Segment> segments(LongMemories.Batch batch, List<UUID> selected, Use location) {
        var values = batch.segments().stream().filter(item -> selected.contains(item.id())).toList();
        if (values.size() != new HashSet<>(selected).size()) {
            throw location.failure(batch, SEGMENT_OUTSIDE_BATCH);
        }
        return values;
    }

    /**
     * 验证逐字原话及其落入的片段，不改写引用。
     * @param batch 固定输入
     * @param segments 已确认的候选片段
     * @param quote 模型引用
     * @param location 候选位置
     * @return 原消息与安全定位
     * @throws MemoryEvidenceException 当引用或来源关系无效时
     */
    private static Use quote(LongMemories.Batch batch, List<MemoryLayers.Segment> segments,
            LongMemories.Quote quote, Use location) {
        var evidence = batch.evidence().stream().filter(item -> item.id().equals(quote.evidenceId())).findFirst()
                .orElseThrow(() -> new MemoryEvidenceException(EVIDENCE_ID_INVALID, location.domain(),
                        location.index(), batch.leaseId(), quote.evidenceId()));
        var use = new Use(location.domain(), location.index(), evidence);
        var message = evidence.message();
        if (message.role() != HistoryMessage.Role.USER || message.source().origin() != AgentActorContext.Origin.USER
                || message.source().userId().isEmpty()) {
            throw use.failure(batch, EVIDENCE_ROLE_INVALID);
        }
        if (!message.text().contains(quote.text())) {
            throw use.failure(batch, QUOTE_MISMATCH);
        }
        if (segments.stream().noneMatch(item -> evidence.sequence() >= item.from() && evidence.sequence() <= item.through())) {
            throw use.failure(batch, SEGMENT_MISMATCH);
        }
        return use;
    }

    /**
     * 已校验关系的来源使用位置，只在本次处理内传递，不直接输出到日志或管理页面。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     * @param domain 候选域
     * @param index 候选序号
     * @param evidence 原证据，候选级定位时为空
     *
     * @author zExNocs
     * @date 2026/11/05
     * @since 4.8.3-l
     * @version 4.9.0-l
     */
    public record Use(MemoryEvidenceException.Domain domain, int index, LongMemories.@Nullable Evidence evidence) {
        /** @return 已定位的来源，候选级位置不可读取原文 */
        public LongMemories.Evidence source() { return java.util.Objects.requireNonNull(evidence); }

        /**
         * 将当前位置投影为不含原文的异常。
         * @param batch 原批次
         * @param code 固定原因
         * @return 安全异常
         */
        public MemoryEvidenceException failure(LongMemories.Batch batch, MemoryEvidenceException.Code code) {
            return new MemoryEvidenceException(code, domain, index, batch.leaseId(), evidence == null ? null : evidence.id());
        }
    }
}
