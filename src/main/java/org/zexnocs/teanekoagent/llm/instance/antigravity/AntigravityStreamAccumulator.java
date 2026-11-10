package org.zexnocs.teanekoagent.llm.instance.antigravity;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.api_response.interfaces.IAPIResponseAccumulator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.zexnocs.teanekoagent.llm.instance.antigravity.AntigravityStreamException.Reason.CAPACITY;
import static org.zexnocs.teanekoagent.llm.instance.antigravity.AntigravityStreamException.Reason.INCOMPLETE;
import static org.zexnocs.teanekoagent.llm.instance.antigravity.AntigravityStreamException.Reason.INVALID;

/**
 * 有界聚合原生生成事件，保留部件和签名顺序，等待 EOF 后一次性交付。
 * <br>2026/11/10：累计用量取最后快照；工具必须等整条响应正常结束，不消费半个调用。
 *
 * @author zExNocs
 * @date 2026/11/10
 * @since 4.9.1-l
 * @version 4.9.1-l
 */
public final class AntigravityStreamAccumulator
        implements IAPIResponseAccumulator<AntigravityResponseData, AntigravityResponseData> {
    private final ObjectMapper mapper;
    private final Map<Integer, ObjectNode> candidates = new LinkedHashMap<>();
    private @Nullable String responseId;
    private @Nullable String modelVersion;
    private @Nullable JsonNode feedback;
    private @Nullable JsonNode usage;
    private long characters;
    private int events;
    private int parts;

    /** @param mapper 借用的 JSON 转换器，不创建网络资源 */
    public AntigravityStreamAccumulator(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * @param data 一个已解码事件
     * @throws AntigravityStreamException 当容量、身份或协议顺序无效时
     */
    @Override
    public void accept(AntigravityResponseData data) {
        characters += mapper.writeValueAsString(data).length();
        if (++events > 8192 || characters > 8L * 1024 * 1024) {
            throw new AntigravityStreamException(CAPACITY);
        }
        responseId = identity(responseId, data.responseId());
        modelVersion = identity(modelVersion, data.modelVersion());
        if (data.promptFeedback() != null) {
            feedback = data.promptFeedback().deepCopy();
        }
        if (data.usageMetadata() != null) {
            // 2026/11/10：每片是累计快照，相加会重复计算输入和生成用量。
            usage = data.usageMetadata().deepCopy();
        }
        if (data.candidates() != null) {
            for (var candidate : data.candidates()) {
                append(candidate);
            }
        }
    }

    /**
     * @param current 之前确定的可选身份
     * @param next 当前事件的可选身份
     * @return 连续事件的一致身份
     * @throws AntigravityStreamException 当响应中途变成另一身份时
     */
    private static @Nullable String identity(@Nullable String current, @Nullable String next) {
        if (current != null && next != null && !current.equals(next)) {
            throw new AntigravityStreamException(INVALID);
        }
        return next == null ? current : next;
    }

    /**
     * 按候选编号保存原生部件，不合并带签名的部件，也不在终态后接受新正文。
     * @param delta 当前候选增量
     * @throws AntigravityStreamException 当候选结构、终态或容量无效时
     */
    private void append(JsonNode delta) {
        var indexNode = delta.path("index");
        if (!delta.isObject() || !indexNode.isMissingNode()
                && (!indexNode.isIntegralNumber() || !indexNode.canConvertToInt() || indexNode.intValue() < 0)) {
            throw new AntigravityStreamException(INVALID);
        }
        int index = indexNode.asInt(0);
        if (!candidates.containsKey(index) && candidates.size() >= 8) {
            throw new AntigravityStreamException(CAPACITY);
        }
        var target = candidates.computeIfAbsent(index, ignored -> mapper.createObjectNode().put("index", index));
        var content = delta.path("content");
        var items = content.path("parts");
        if (!content.isMissingNode() && !content.isObject()
                || !items.isMissingNode() && !items.isArray()
                || content.has("role") && !"model".equals(content.path("role").asString(""))
                || target.has("finishReason") && (delta.has("finishReason") || !items.isEmpty())) {
            throw new AntigravityStreamException(INVALID);
        }
        if (items.isArray()) {
            parts += items.size();
            if (parts > 16384) {
                throw new AntigravityStreamException(CAPACITY);
            }
            if (!target.has("content")) {
                target.putObject("content").put("role", "model").putArray("parts");
            }
            var combined = target.withObject("content").withArray("parts");
            for (var part : items) {
                if (!part.isObject() || part.isEmpty()) {
                    throw new AntigravityStreamException(INVALID);
                }
                combined.add(part.deepCopy());
            }
        }
        if (delta.has("finishReason")) {
            var finish = delta.path("finishReason");
            if (!finish.isString() || finish.asString().isBlank()
                    || "FINISH_REASON_UNSPECIFIED".equals(finish.asString())) {
                throw new AntigravityStreamException(INVALID);
            }
            target.set("finishReason", finish);
        }
    }

    /**
     * @return 仅在正常 EOF 后返回的完整原生响应
     * @throws AntigravityStreamException 当任一候选未结束或响应为空时
     */
    @Override
    public AntigravityResponseData finish() {
        boolean blocked = feedback != null && feedback.path("blockReason").isString()
                && !feedback.path("blockReason").asString().isBlank()
                && !"BLOCK_REASON_UNSPECIFIED".equals(feedback.path("blockReason").asString());
        if (candidates.isEmpty() && !blocked
                || candidates.values().stream().anyMatch(candidate -> !candidate.has("finishReason"))) {
            throw new AntigravityStreamException(INCOMPLETE);
        }
        return new AntigravityResponseData(responseId, modelVersion,
                List.copyOf(candidates.values()), feedback, usage);
    }
}
