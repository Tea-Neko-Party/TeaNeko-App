package org.zexnocs.teanekoplugin.agent.cognition.memory.layered;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoplugin.agent.cognition.personality.StyleObservations;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 组织长期学习的紧凑输入，文本去重与真实证据身份分别维护。
 * <br>2026/11/08：重复原话仅保存一次，独立发言的作者、时间和片段不合并。
 *
 * @author zExNocs
 * @date 2026/11/08
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
final class LongMemoryLearningInput {
    /** 禁止实例化纯输入投影。 */
    private LongMemoryLearningInput() {
    }

    /**
     * 背景只携带片段简述，原话通过字典逐字还原，不省略来源或放大授权。
     * @param batch 领取时冻结的真实证据
     * @param target 可选的已授权人格观察目标
     * @return 模型输入纯数据，文本字典与证据引用一一对应
     */
    static Map<String, Object> document(LongMemories.Batch batch, StyleObservations.@Nullable Target target) {
        var input = new LinkedHashMap<String, Object>();
        var references = new LinkedHashMap<String, String>();
        var texts = new LinkedHashMap<String, String>();
        input.put("scope", batch.stream().reference().scope());
        input.put("segments", batch.segments().stream().map(segment -> Map.of(
                "id", segment.id(), "brief", segment.digest().brief())).toList());
        input.put("evidenceMap", batch.evidence().stream().map(value -> {
            var text = value.message().text();
            var key = references.computeIfAbsent(text, ignored -> {
                var id = "t" + (texts.size() + 1);
                texts.put(id, text);
                return id;
            });
            return Map.of("evidenceId", value.id(), "authorId", value.message().source().userId().orElseThrow(),
                    "textRef", key, "occurredAt", value.message().occurredAt(),
                    "segments", batch.segments().stream().filter(segment -> value.sequence() >= segment.from()
                            && value.sequence() <= segment.through()).map(MemoryLayers.Segment::id).toList());
        }).toList());
        input.put("texts", texts);
        if (target != null) {
            var instructions = target.basis().instructions();
            input.put("styleTarget", Map.of("fields", target.fields(), "evidenceIds", target.evidenceIds(),
                    "known", target.known(), "base", instructions.substring(0, Math.min(4000, instructions.length()))));
        }
        return input;
    }
}
