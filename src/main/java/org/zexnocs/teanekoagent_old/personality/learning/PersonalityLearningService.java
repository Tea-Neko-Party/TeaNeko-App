package org.zexnocs.teanekoagent_old.personality.learning;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent_old.agent.prompt.AgentRequestContext;
import org.zexnocs.teanekoagent_old.memory.AgentMemoryQueryService;
import org.zexnocs.teanekoagent_old.memory.MemoryTimeRange;
import org.zexnocs.teanekoagent_old.memory.PersonalityConflictRecord;
import org.zexnocs.teanekoagent_old.memory.PersonalityDeltaRecord;
import org.zexnocs.teanekoagent_old.personality.AgentPersonalityResolver;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;

/**
 * 第一阶段确定性性格学习写入服务。
 * <br>该服务只接收已经抽取出的候选性格修正，负责边界检查、合并和写入 EasyData。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@Service
@RequiredArgsConstructor
public class PersonalityLearningService {
    /**
     * Agent 人格解析服务。
     */
    private final AgentPersonalityResolver personalityResolver;

    /**
     * Agent 记忆查询与写入服务。
     */
    private final AgentMemoryQueryService memoryQueryService;

    /**
     * 记录一条性格学习修正。
     * <br>该方法会先解析当前 active personality，再用人格边界策略判断该修正是否允许写入。
     *
     * @param context    Agent 请求上下文。
     * @param field      修正字段或领域。
     * @param content    修正内容。
     * @param source     修正来源。
     * @param confidence 置信度。
     * @return 成功写入的修正记录；被拒绝时返回空。
     */
    public Optional<PersonalityDeltaRecord> recordDelta(AgentRequestContext context,
                                                        String field,
                                                        String content,
                                                        String source,
                                                        double confidence) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }
        var resolved = personalityResolver.resolve(context);
        if (!resolved.config().enabled() || !resolved.config().personalityLearningEnabled()) {
            return Optional.empty();
        }

        var now = Instant.now();
        var record = new PersonalityDeltaRecord();
        record.setField(safe(field));
        record.setContent(content.trim());
        record.setSource(safe(source));
        record.setConfidence(clamp(confidence));
        record.setEventTime(MemoryTimeRange.exact(now));
        record.setCreatedAt(now);
        record.setUpdatedAt(now);

        if (!resolved.boundaryPolicy().accepts(record)) {
            appendConflict(context.scopeId(), resolved.agentId(), record, "Rejected by active personality boundary policy");
            return Optional.empty();
        }

        var records = new ArrayList<>(memoryQueryService.findPersonalityDeltas(context.scopeId(), resolved.agentId()));
        merge(records, record);
        memoryQueryService.savePersonalityDeltas(context.scopeId(), resolved.agentId(), records);
        return Optional.of(record);
    }

    /**
     * 追加一条被拒绝的性格学习冲突记录。
     *
     * @param scopeId  作用域 ID。
     * @param agentId  agent ID。
     * @param rejected 被拒绝的性格修正。
     * @param reason   拒绝原因。
     */
    private void appendConflict(String scopeId, String agentId, PersonalityDeltaRecord rejected, String reason) {
        var conflict = new PersonalityConflictRecord();
        conflict.setField(rejected.getField());
        conflict.setContent(rejected.getContent());
        conflict.setSource(rejected.getSource());
        conflict.setReason(reason);
        conflict.setEventTime(rejected.getEventTime());

        var conflicts = new ArrayList<>(memoryQueryService.findPersonalityConflicts(scopeId, agentId));
        conflicts.add(conflict);
        memoryQueryService.savePersonalityConflicts(scopeId, agentId, conflicts);
    }

    /**
     * 将新的性格修正合并进已有列表。
     *
     * @param records  已有性格修正列表。
     * @param incoming 新性格修正。
     */
    private void merge(ArrayList<PersonalityDeltaRecord> records, PersonalityDeltaRecord incoming) {
        for (var existing : records) {
            if (sameDelta(existing, incoming) && !existing.isLocked()) {
                existing.setConfidence(Math.max(existing.getConfidence(), incoming.getConfidence()));
                existing.setSource(incoming.getSource());
                existing.setUpdatedAt(Instant.now());
                return;
            }
        }
        records.add(incoming);
    }

    /**
     * 判断两条性格修正是否表示同一内容。
     *
     * @param a 第一条修正。
     * @param b 第二条修正。
     * @return 如果字段和内容相同则返回 true。
     */
    private boolean sameDelta(PersonalityDeltaRecord a, PersonalityDeltaRecord b) {
        return safe(a.getField()).equalsIgnoreCase(safe(b.getField()))
                && safe(a.getContent()).equalsIgnoreCase(safe(b.getContent()));
    }

    /**
     * 将置信度限制在 0 到 1 之间。
     *
     * @param value 原始置信度。
     * @return 修正后的置信度。
     */
    private static double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0;
        }
        return Math.clamp(value, 0, 1);
    }

    /**
     * 规范化字符串值。
     *
     * @param value 原始值。
     * @return 非空字符串。
     */
    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
