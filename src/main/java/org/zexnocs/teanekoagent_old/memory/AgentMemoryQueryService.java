package org.zexnocs.teanekoagent_old.memory;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent_old.agent.prompt.AgentRequestContext;
import org.zexnocs.teanekoagent_old.database.LLMRelatedEasyData;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Agent 第一阶段确定性记忆读写服务。
 * <br>该服务只使用 EasyData 的确定性 key 读写记忆，不引入向量检索或图谱检索，便于后续扩展替换。
 *
 * @author zExNocs
 * @date 2026/06/10
 * @since 4.4.1
 */
@Service
public class AgentMemoryQueryService {
    /**
     * 默认注入 prompt 的长期记忆数量上限。
     */
    private static final int DEFAULT_MEMORY_LIMIT = 12;

    /**
     * 查询指定作用域和 agent 的性格学习修正。
     *
     * @param scopeId 作用域 ID。
     * @param agentId agent ID。
     * @return 未过期的性格修正列表，按置信度和更新时间排序。
     */
    public List<PersonalityDeltaRecord> findPersonalityDeltas(String scopeId, String agentId) {
        var target = AgentMemoryKeys.scopeTarget(scopeId, agentId);
        var dto = LLMRelatedEasyData.of(AgentMemoryKeys.NAMESPACE_MEMORY).get(target);
        var now = Instant.now();
        return dto.getList(AgentMemoryKeys.KEY_PERSONALITY_DELTA, PersonalityDeltaRecord.class)
                .stream()
                .filter(record -> !record.isExpired(now))
                .sorted(Comparator.comparing(PersonalityDeltaRecord::getConfidence).reversed()
                        .thenComparing(PersonalityDeltaRecord::getUpdatedAt).reversed())
                .toList();
    }

    /**
     * 查询当前请求相关的长期记忆。
     *
     * @param context agent 请求上下文。
     * @return 相关长期记忆列表。
     */
    public List<AgentMemoryRecord> findRelevant(AgentRequestContext context) {
        return findRelevant(context, DEFAULT_MEMORY_LIMIT);
    }

    /**
     * 查询当前请求相关的长期记忆。
     *
     * @param context agent 请求上下文。
     * @param limit   返回数量上限。
     * @return 相关长期记忆列表。
     */
    public List<AgentMemoryRecord> findRelevant(AgentRequestContext context, int limit) {
        return findRelevant(context, null, null, limit);
    }

    /**
     * 查询与指定事件时间范围相交的长期记忆。
     *
     * @param context Agent 请求上下文。
     * @param start   查询起点；为空表示不限制起点。
     * @param end     查询终点；为空表示不限制终点。
     * @param limit   返回数量上限。
     * @return 与查询时间范围相交的长期记忆列表。
     */
    public List<AgentMemoryRecord> findRelevant(AgentRequestContext context,
                                                Instant start,
                                                Instant end,
                                                int limit) {
        var records = new ArrayList<AgentMemoryRecord>();
        var target = AgentMemoryKeys.scopeTarget(context.scopeId(), context.agentId());
        var dto = LLMRelatedEasyData.of(AgentMemoryKeys.NAMESPACE_MEMORY).get(target);
        var now = Instant.now();

        if (!context.userId().isBlank()) {
            records.addAll(dto.getList(AgentMemoryKeys.profileKey(context.userId()), AgentMemoryRecord.class));
        }

        return records.stream()
                .filter(record -> !record.isExpired(now))
                .filter(record -> record.occursWithin(start, end))
                .sorted(Comparator.comparing(AgentMemoryRecord::getImportance).reversed()
                        .thenComparing(Comparator.comparingDouble(AgentMemoryRecord::getConfidence).reversed())
                        .thenComparing(Comparator.comparing(AgentMemoryRecord::getUpdatedAt).reversed()))
                .limit(Math.max(0, limit))
                .toList();
    }

    /**
     * 查询发生在指定时间点的长期记忆。
     *
     * @param context   Agent 请求上下文。
     * @param timePoint 查询时间点。
     * @param limit     返回数量上限。
     * @return 覆盖该时间点的记忆列表。
     */
    public List<AgentMemoryRecord> findRelevantAt(AgentRequestContext context,
                                                  Instant timePoint,
                                                  int limit) {
        return findRelevant(context, timePoint, timePoint, limit);
    }

    /**
     * 查询与指定时间范围相交的长期记忆。
     *
     * @param context Agent 请求上下文。
     * @param start   查询起点。
     * @param end     查询终点。
     * @param limit   返回数量上限。
     * @return 与时间范围相交的记忆列表。
     */
    public List<AgentMemoryRecord> findRelevantBetween(AgentRequestContext context,
                                                       Instant start,
                                                       Instant end,
                                                       int limit) {
        return findRelevant(context, start, end, limit);
    }

    /**
     * 保存指定作用域和 agent 的性格学习修正列表。
     *
     * @param scopeId 作用域 ID。
     * @param agentId agent ID。
     * @param records 性格修正列表。
     */
    public void savePersonalityDeltas(String scopeId, String agentId, List<PersonalityDeltaRecord> records) {
        var target = AgentMemoryKeys.scopeTarget(scopeId, agentId);
        LLMRelatedEasyData.of(AgentMemoryKeys.NAMESPACE_MEMORY)
                .get(target)
                .getTaskConfig("update agent personality deltas")
                .set(AgentMemoryKeys.KEY_PERSONALITY_DELTA, List.copyOf(records))
                .push();
    }

    /**
     * 查询指定作用域和 agent 的性格冲突记录。
     *
     * @param scopeId 作用域 ID。
     * @param agentId agent ID。
     * @return 性格冲突记录列表。
     */
    public List<PersonalityConflictRecord> findPersonalityConflicts(String scopeId, String agentId) {
        var target = AgentMemoryKeys.scopeTarget(scopeId, agentId);
        var dto = LLMRelatedEasyData.of(AgentMemoryKeys.NAMESPACE_MEMORY).get(target);
        return dto.getList(AgentMemoryKeys.KEY_PERSONALITY_CONFLICT, PersonalityConflictRecord.class);
    }

    /**
     * 保存指定作用域和 agent 的性格冲突记录列表。
     *
     * @param scopeId 作用域 ID。
     * @param agentId agent ID。
     * @param records 性格冲突记录列表。
     */
    public void savePersonalityConflicts(String scopeId, String agentId, List<PersonalityConflictRecord> records) {
        var target = AgentMemoryKeys.scopeTarget(scopeId, agentId);
        LLMRelatedEasyData.of(AgentMemoryKeys.NAMESPACE_MEMORY)
                .get(target)
                .getTaskConfig("update rejected agent personality deltas")
                .set(AgentMemoryKeys.KEY_PERSONALITY_CONFLICT, List.copyOf(records))
                .push();
    }

    /**
     * 追加一条用户画像记忆。
     *
     * @param scopeId 作用域 ID。
     * @param agentId agent ID。
     * @param userId  用户 ID。
     * @param record  要追加的记忆记录。
     */
    public void appendUserMemory(String scopeId, String agentId, String userId, AgentMemoryRecord record) {
        var target = AgentMemoryKeys.scopeTarget(scopeId, agentId);
        var key = AgentMemoryKeys.profileKey(userId);
        var dto = LLMRelatedEasyData.of(AgentMemoryKeys.NAMESPACE_MEMORY).get(target);
        var records = new ArrayList<>(dto.getList(key, AgentMemoryRecord.class));
        records.add(record);
        dto.getTaskConfig("append agent user memory")
                .set(key, records)
                .push();
    }
}
