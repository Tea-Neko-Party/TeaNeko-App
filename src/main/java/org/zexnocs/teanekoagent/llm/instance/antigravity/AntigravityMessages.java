package org.zexnocs.teanekoagent.llm.instance.antigravity;

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.ImageLLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMImageDetail;
import org.zexnocs.teanekoagent.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMToolMessage;
import org.zexnocs.teanekoagent.llm.framework.model.LLMInputValidator;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolCall;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMToolCall;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 原生消息和工具往返映射；签名保留在透明状态中，不替换统一正文或绕过工具配对。
 * <br>2026/11/10：按原部件顺序回传签名，回放前复核正文与工具参数未被修改。
 * <br>2026/11/10：按原生协议将省略的可选 args 解释为空参数，已提供的非法值仍拒绝。
 * <br>2026/11/10：可见文本增量直接连接，原始部件及签名继续独立保留供续轮回放。
 *
 * @author zExNocs
 * @date 2026/11/10
 * @since 4.9.1-l
 * @version 4.9.1-l
 */
final class AntigravityMessages {
    private static final String PARTS = "antigravity.parts";
    private static final String CALL_IDS = "antigravity.callIds";
    private static final Set<String> PART_FIELDS = Set.of("text", "thought", "thoughtSignature", "functionCall");

    /** 纯协议转换不持有运行资源。 */
    private AntigravityMessages() { }

    /**
     * @param messages 统一消息，包含已匹配的工具结果
     * @param mapper JSON 转换器
     * @return 分离系统指令后的原生对话
     * @throws IllegalArgumentException 当回放状态、消息或工具对应关系不合法时
     */
    static Encoded encode(List<ILLMMessage> messages, ObjectMapper mapper) {
        var system = new ArrayList<>();
        var turns = new ArrayList<Map<String, Object>>();
        var pending = new LinkedHashMap<String, Binding>();
        var userParts = new ArrayList<>();
        for (var message : messages) {
            switch (message.getRole()) {
                case SYSTEM -> system.addAll(content(message));
                case USER -> userParts.addAll(content(message));
                case TOOL -> {
                    var tool = (ILLMToolMessage) message;
                    var binding = pending.remove(tool.getToolCallId());
                    if (binding == null) {
                        throw new IllegalArgumentException("原生工具结果缺少对应的模型调用，拒绝错误配对。");
                    }
                    var response = new LinkedHashMap<String, Object>();
                    response.put("name", binding.name());
                    response.put("response", Map.of("result", visibleText(message)));
                    if (binding.nativeId() != null) {
                        response.put("id", binding.nativeId());
                    }
                    userParts.add(Map.of("functionResponse", response));
                }
                case ASSISTANT -> {
                    if (!pending.isEmpty()) {
                        throw new IllegalArgumentException("原生工具结果尚未完整回填，不能开始下一条模型消息。");
                    }
                    flushUser(turns, userParts);
                    var assistant = (ILLMAssistantMessage) message;
                    var parts = replay(assistant, mapper, pending);
                    if (parts.isEmpty()) {
                        throw new IllegalArgumentException("原生模型消息没有可回放内容。");
                    }
                    turns.add(Map.of("role", "model", "parts", parts));
                }
            }
        }
        if (!pending.isEmpty()) {
            throw new IllegalArgumentException("原生请求存在尚未回填的工具调用。");
        }
        flushUser(turns, userParts);
        if (turns.isEmpty()) {
            throw new IllegalArgumentException("原生请求至少需要一条非系统消息。");
        }
        return new Encoded(List.copyOf(turns), system.isEmpty() ? Map.of() : Map.of("parts", List.copyOf(system)));
    }

    /**
     * @param turns 已生成的完整对话
     * @param parts 连续用户消息或并行工具结果
     */
    private static void flushUser(List<Map<String, Object>> turns, List<Object> parts) {
        if (!parts.isEmpty()) {
            turns.add(Map.of("role", "user", "parts", List.copyOf(parts)));
            parts.clear();
        }
    }

    /**
     * @param message 只含统一文本或图片的消息
     * @return 原生文本、内联图片或远端图片引用，不下载远端文件
     */
    private static List<Object> content(ILLMMessage message) {
        var parts = new ArrayList<>();
        if (message.getContents() == null) {
            return parts;
        }
        for (var content : message.getContents()) {
            var value = LLMInputValidator.requirePart(content);
            if (value instanceof TextLLMContentPart text) {
                parts.add(Map.of("text", text.getText()));
            } else if (value instanceof ImageLLMContentPart image) {
                var part = new LinkedHashMap<String, Object>();
                String url = image.getUrl();
                if (url.startsWith("data:")) {
                    int separator = url.indexOf(";base64,");
                    part.put("inlineData", Map.of("mimeType", url.substring(5, separator),
                            "data", url.substring(separator + 8)));
                } else {
                    part.put("fileData", Map.of("fileUri", url));
                }
                if (image.getDetail() != LLMImageDetail.AUTO) {
                    part.put("mediaResolution", Map.of("level", image.getDetail() == LLMImageDetail.LOW
                            ? "MEDIA_RESOLUTION_LOW" : "MEDIA_RESOLUTION_HIGH"));
                }
                parts.add(part);
            }
        }
        return parts;
    }

    /**
     * @param message 需要回填的文本消息
     * @return 保持原顺序的全文
     * @throws IllegalArgumentException 当工具输出或回放正文包含非文本时
     */
    private static String visibleText(ILLMMessage message) {
        var text = new StringBuilder();
        if (message.getContents() != null) {
            for (var content : message.getContents()) {
                if (!(LLMInputValidator.requirePart(content) instanceof TextLLMContentPart part)) {
                    throw new IllegalArgumentException("原生工具结果或签名消息只允许明确的文本正文。");
                }
                text.append(part.getText());
            }
        }
        return text.toString();
    }

    /**
     * @param message 统一模型消息
     * @param mapper JSON 转换器
     * @param pending 登记本轮工具 ID 与上游函数名的关系
     * @return 原序签名部件，或没有透明状态的普通历史
     * @throws IllegalArgumentException 当透明状态与统一消息矛盾或工具重复时
     */
    private static List<Object> replay(ILLMAssistantMessage message, ObjectMapper mapper, Map<String, Binding> pending) {
        var metadata = message.getProviderMetadata();
        var stored = metadata.get(PARTS);
        if (stored == null) {
            var result = new ArrayList<>(content(message));
            for (var call : message.getToolCalls()) {
                var args = mapper.readTree(call.getArguments());
                if (!args.isObject()) {
                    throw new IllegalArgumentException("原生工具参数要求 JSON 对象。");
                }
                result.add(Map.of("functionCall", Map.of("name", call.getName(), "args", args, "id", call.getId())));
                register(pending, call.getId(), new Binding(call.getName(), call.getId()));
            }
            return result;
        }
        if (!(stored instanceof String json)) {
            throw new IllegalArgumentException("原生签名回放状态格式无效。");
        }
        var parts = mapper.readTree(json);
        var ids = mapper.valueToTree(metadata.get(CALL_IDS));
        var decoded = decode(parts, ids, mapper);
        if (!visibleText(message).equals(visibleText(decoded.message()))
                || message.getToolCalls().size() != decoded.message().getToolCalls().size()) {
            throw new IllegalArgumentException("原生回放状态与当前正文或工具目录不一致。");
        }
        for (int i = 0; i < message.getToolCalls().size(); i++) {
            var actual = message.getToolCalls().get(i);
            var original = decoded.message().getToolCalls().get(i);
            if (!actual.getId().equals(original.getId()) || !actual.getName().equals(original.getName())
                    || !mapper.readTree(actual.getArguments()).equals(mapper.readTree(original.getArguments()))) {
                throw new IllegalArgumentException("原生签名对应的工具参数已变化，拒绝回放。");
            }
            register(pending, actual.getId(), decoded.bindings().get(i));
        }
        var result = new ArrayList<>();
        parts.forEach(result::add);
        return result;
    }

    /**
     * @param pending 在途工具映射
     * @param id 本地统一调用 ID
     * @param binding 可信响应中的上游关系
     * @throws IllegalArgumentException 当同轮出现重复 ID 时
     */
    private static void register(Map<String, Binding> pending, String id, Binding binding) {
        if (pending.putIfAbsent(id, binding) != null) {
            throw new IllegalArgumentException("原生响应包含重复工具调用 ID。");
        }
    }

    /**
     * @param parts 原生候选的部件数组
     * @param mapper JSON 转换器
     * @return 可交互的助手消息，隐藏思考正文并保留签名
     */
    static LLMAssistantMessage decode(JsonNode parts, ObjectMapper mapper) {
        return decode(parts, null, mapper).message();
    }

    /**
     * @param parts 原生部件
     * @param ids 回放时的本地工具 ID，首次响应为空
     * @param mapper JSON 转换器
     * @return 严格转换结果及原生工具关系
     * @throws IllegalArgumentException 当候选部件或回放 ID 无法安全映射时
     */
    private static Decoded decode(JsonNode parts, @Nullable JsonNode ids, ObjectMapper mapper) {
        if (!parts.isArray() || ids != null && !ids.isArray()) {
            throw new IllegalArgumentException("原生候选 parts 和回放工具 ID 要求数组。");
        }
        // 2026/11/10：原生文本增量拼成一个可见部件，避免下游把网络分片当段落或多条消息。
        var contents = new LLMContentListBuilder();
        var visible = new StringBuilder();
        boolean hasVisibleText = false;
        var calls = new ArrayList<ILLMToolCall>();
        var bindings = new ArrayList<Binding>();
        for (var part : parts) {
            if (!part.isObject() || !PART_FIELDS.containsAll(part.propertyNames())
                    || part.has("thought") && !part.path("thought").isBoolean()
                    || part.has("thoughtSignature") && !part.path("thoughtSignature").isString()
                    || part.has("text") && part.has("functionCall")) {
                throw new IllegalArgumentException("原生响应包含无法映射的部件，不会静默丢弃。");
            }
            if (part.has("text")) {
                if (!part.path("text").isString()) {
                    throw new IllegalArgumentException("原生 text 字段要求字符串。");
                }
                if (!part.path("thought").asBoolean(false)) {
                    visible.append(part.path("text").asString());
                    hasVisibleText = true;
                }
            } else if (part.has("functionCall")) {
                var function = part.path("functionCall");
                if (!function.isObject() || !function.path("name").isString()
                        || function.path("name").asString().isBlank()
                        || function.has("args") && !function.path("args").isObject()
                        || function.has("id") && (!function.path("id").isString() || function.path("id").asString().isBlank())
                        || !Set.of("name", "args", "id").containsAll(function.propertyNames())) {
                    throw new IllegalArgumentException("原生 functionCall 要求名称、可选对象参数和可选非空 ID。");
                }
                String nativeId = function.has("id") ? function.path("id").asString() : null;
                String id;
                if (ids == null) {
                    id = nativeId == null ? "native-" + UUID.randomUUID() : nativeId;
                } else {
                    var value = ids.path(calls.size());
                    if (!value.isString() || value.asString().isBlank()) {
                        throw new IllegalArgumentException("原生回放工具 ID 缺失。");
                    }
                    id = value.asString();
                }
                // 2026/11/10：协议允许无参调用省略 args；参数必填性仍由统一工具 Schema 复核。
                String arguments = function.has("args") ? mapper.writeValueAsString(function.path("args")) : "{}";
                calls.add(LLMToolCall.builder().id(id).name(function.path("name").asString())
                        .arguments(arguments).build());
                bindings.add(new Binding(function.path("name").asString(), nativeId));
            } else if (!part.has("thoughtSignature")) {
                throw new IllegalArgumentException("原生响应部件缺少文本、工具或思考签名。");
            }
        }
        if (ids != null && ids.size() != calls.size()
                || calls.stream().map(ILLMToolCall::getId).distinct().count() != calls.size()) {
            throw new IllegalArgumentException("原生工具 ID 数量或唯一性不符合要求。");
        }
        var metadata = Map.of(PARTS, mapper.writeValueAsString(parts),
                CALL_IDS, calls.stream().map(ILLMToolCall::getId).toList());
        if (hasVisibleText) {
            contents.addText(visible.toString());
        }
        return new Decoded(LLMAssistantMessage.builder().contents(contents.build()).toolCalls(List.copyOf(calls))
                .providerMetadata(metadata).build(), List.copyOf(bindings));
    }

    /**
     * 系统指令与对话的固定转换结果。
     * @param contents 原生对话
     * @param systemInstruction 系统指令
     * @author zExNocs
     * @date 2026/11/10
     * @since 4.9.1-l
     * @version 4.9.1-l
     */
    record Encoded(List<Map<String, Object>> contents, Map<String, Object> systemInstruction) { }

    /**
     * 本地工具与上游可选 ID 的独立关系。
     * @param name 原生函数名
     * @param nativeId 上游显式 ID，不用本地补号冒充
     * @author zExNocs
     * @date 2026/11/10
     * @since 4.9.1-l
     * @version 4.9.1-l
     */
    private record Binding(String name, @Nullable String nativeId) { }

    /**
     * 严格解析的助手消息及工具关联。
     * @param message 可见正文和透明状态
     * @param bindings 按工具顺序排列的关联
     * @author zExNocs
     * @date 2026/11/10
     * @since 4.9.1-l
     * @version 4.9.1-l
     */
    private record Decoded(LLMAssistantMessage message, List<Binding> bindings) { }
}
