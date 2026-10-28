package org.zexnocs.teanekoagent.llm.framework.message;

import org.zexnocs.teanekoagent.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.*;
import org.zexnocs.teanekoagent.llm.framework.model.LLMInputValidator;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.LLMOptionBoundary;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMChoice;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMUsage;
import org.zexnocs.teanekoagent.llm.framework.tool.interfaces.ILLMToolCall;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.*;

/**
 * 冻结交互边界的协议数据；透明状态只接受有限深度的 JSON 值，不保留任意运行句柄。
 * 不通过序列化猜测消息角色，因而保留工具配对及供应商回放所需的接口类型。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class LLMProtocolSnapshots {
    /** 禁止实例化无状态快照工具。 */
    private LLMProtocolSnapshots() {}

    /**
     * 复制列表及每个消息，调用方须避免在复制期间并发修改原对象。
     *
     * @param source 原始消息列表
     * @return 与原始可变消息独立的不可变列表
     * @throws IllegalArgumentException 当角色、内容或透明状态不受支持时
     */
    public static List<ILLMMessage> messages(List<ILLMMessage> source) {
        return Objects.requireNonNull(source, "消息列表不能为空。").stream()
                .map(LLMProtocolSnapshots::message).toList();
    }

    /**
     * 按角色保留统一协议接口，并冻结正文、工具调用和嵌套状态。
     *
     * @param source 原始消息
     * @return 不可变的同角色消息
     * @throws IllegalArgumentException 当角色与专用接口不一致时
     */
    public static ILLMMessage message(ILLMMessage source) {
        Objects.requireNonNull(source, "消息不能为空。");
        var contents = new ArrayList<ILLMContent>();
        if (source.getContents() != null) {
            for (var content : source.getContents()) {
                var part = LLMInputValidator.requirePart(content);
                contents.add(new Content(content.getType(), part instanceof TextLLMContentPart text
                        ? new FrozenText(text.getText()) : part));
            }
        }
        var frozen = List.copyOf(contents);
        var metadata = metadata(source.getProviderMetadata());
        var name = source.getName();
        return switch (Objects.requireNonNull(source.getRole(), "消息角色不能为空。")) {
            case SYSTEM -> new SystemMessage(name, frozen, metadata);
            case USER -> new UserMessage(name, frozen, metadata);
            case ASSISTANT -> {
                if (!(source instanceof ILLMAssistantMessage assistant)) {
                    throw new IllegalArgumentException("assistant 消息缺少工具协议接口。");
                }
                var calls = assistant.getToolCalls() == null ? List.<ILLMToolCall>of()
                        : assistant.getToolCalls().stream().map(LLMProtocolSnapshots::call).toList();
                yield new AssistantMessage(name, frozen, metadata, calls);
            }
            case TOOL -> {
                if (!(source instanceof ILLMToolMessage tool)) {
                    throw new IllegalArgumentException("tool 消息缺少调用标识接口。");
                }
                yield new ToolMessage(name, frozen, metadata, tool.getToolCallId());
            }
        };
    }

    /**
     * 冻结模型可修改的选项数据；工具由可信绑定覆盖，禁止从 Prompt 携带执行器。
     * 返回容器仅由本次编排私有持有，每次模型调用继续复制容器。
     *
     * @param source 调用方的可选覆盖项
     * @return 私有选项副本及不可变的嵌套数据
     */
    public static LLMModelOptions options(ILLMModelOptions source) {
        var result = LLMModelOptions.copyOf(source);
        result.setMetadata(metadata(result.getMetadata()));
        result.findStopWords().ifPresent(words -> result.setStopWords(List.copyOf(words)));
        result.setTools(List.of());
        return result;
    }

    /**
     * 冻结所有候选、usage 和透明状态；缺失 usage 保持缺失，不制造供应商 token 数据。
     *
     * @param source 单次模型响应
     * @return 与供应商可变 DTO 独立的响应快照
     * @throws IllegalArgumentException 当 token 数据为负或响应包含非法协议数据时
     */
    public static ILLMResult result(ILLMResult source) {
        Objects.requireNonNull(source, "模型结果不能为空。");
        var choices = new ArrayList<ILLMChoice>();
        if (source.getChoices() != null) {
            for (var choice : source.getChoices()) {
                Objects.requireNonNull(choice, "模型候选不能为空。");
                choices.add(new Choice(choice.getFinishReason(), choice.getIndex(),
                        choice.getMessage() == null ? null : (ILLMAssistantMessage) message(choice.getMessage()),
                        metadata(choice.getLogprobs())));
            }
        }
        var raw = source.getUsage();
        ILLMUsage usage = raw == null ? null : new Usage(raw.getCompletionTokens(), raw.getPromptTokens(),
                raw.getTotalTokens(), raw.getPromptCacheHitTokens(), raw.getPromptCacheMissTokens(), raw.getReasoningTokens());
        if (usage != null && (usage.getCompletionTokens() < 0 || usage.getPromptTokens() < 0
                || usage.getTotalTokens() < 0 || usage.getPromptCacheHitTokens() < 0
                || usage.getPromptCacheMissTokens() < 0 || usage.getReasoningTokens() < 0)) {
            throw new IllegalArgumentException("供应商 token 用量不能为负数。");
        }
        return new Result(source.getId(), List.copyOf(choices), source.getObject(), source.getCreated(), source.getModel(), usage);
    }

    /**
     * 固定模型业务参数，不保留原始工具 DTO。
     *
     * @param source 模型工具调用
     * @return 只含协议字段的不可变调用
     */
    public static ILLMToolCall call(ILLMToolCall source) {
        Objects.requireNonNull(source, "工具调用不能为空。");
        return new Call(source.getId(), source.getType(), source.getName(), source.getArguments());
    }

    /**
     * 在深拷贝前检查凭据边界，保留 JSON null、列表顺序和透明字段。
     *
     * @param source 可选的 JSON 对象
     * @return 不可变对象，空输入规范化为空对象
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> metadata(Map<String, Object> source) {
        if (source == null) {
            return Map.of();
        }
        var frozen = (Map<String, Object>) json(source, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
        LLMOptionBoundary.validateMetadata(frozen);
        return frozen;
    }

    /**
     * 递归冻结 JSON 值，拒绝循环、非字符串键、非有限数和任意业务对象。
     *
     * @param source 当前 JSON 值
     * @param visiting 当前递归路径，允许不同分支共享原始容器
     * @param depth 当前嵌套深度
     * @return 不含可变引用的 JSON 值
     * @throws IllegalArgumentException 当数据不能安全表示为有限 JSON 树时
     */
    private static Object json(Object source, Set<Object> visiting, int depth) {
        if (depth > 64) {
            throw new IllegalArgumentException("交互透明状态嵌套过深。");
        }
        if (source == null || source instanceof String || source instanceof Boolean
                || source instanceof Byte || source instanceof Short || source instanceof Integer
                || source instanceof Long || source.getClass() == BigInteger.class || source.getClass() == BigDecimal.class
                || source instanceof Double value && Double.isFinite(value)
                || source instanceof Float floatValue && Float.isFinite(floatValue)) {
            return source;
        }
        if (!visiting.add(source)) {
            throw new IllegalArgumentException("交互透明状态不能包含循环引用。");
        }
        try {
            if (source instanceof Map<?, ?> map) {
                var result = new LinkedHashMap<String, Object>();
                for (var entry : map.entrySet()) {
                    if (!(entry.getKey() instanceof String key)) {
                        throw new IllegalArgumentException("交互透明状态的对象键必须是字符串。");
                    }
                    result.put(key, json(entry.getValue(), visiting, depth + 1));
                }
                return Collections.unmodifiableMap(result);
            }
            var result = new ArrayList<>();
            if (source instanceof List<?> list) {
                for (var item : list) {
                    result.add(json(item, visiting, depth + 1));
                }
            } else if (source.getClass().isArray()) {
                for (int i = 0; i < Array.getLength(source); i++) {
                    result.add(json(Array.get(source, i), visiting, depth + 1));
                }
            } else {
                throw new IllegalArgumentException("交互透明状态只允许 JSON 数据，不能携带运行对象。");
            }
            return Collections.unmodifiableList(result);
        } finally {
            visiting.remove(source);
        }
    }

    /**
     * 兼容既有文本 mapper 的只读文本，禁止通过 Lombok 生成的父类 setter 修改快照。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private static final class FrozenText extends TextLLMContentPart {
        /**
         * 固定正文；字符串本身不可变。
         * @param text 已通过内容校验的文本
         */
        private FrozenText(String text) { super(text); }

        /**
         * 拒绝改变已经交付或即将回放的正文。
         * @param text 调用方尝试写入的文本
         * @throws UnsupportedOperationException 任何调用都会被拒绝
         */
        @Override public void setText(String text) { throw new UnsupportedOperationException("交互正文快照不可修改。"); }
    }

    /**
     * 不可变正文项。
     * @param getType 内容类型
     * @param getContentPart 只读内容数据
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record Content(String getType, ILLMContentPart getContentPart) implements ILLMContent {}

    /**
     * 不可变系统消息。
     * @param getName 消息名称
     * @param getContents 正文
     * @param getProviderMetadata 透明状态
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record SystemMessage(String getName, List<ILLMContent> getContents,
                                 Map<String, Object> getProviderMetadata) implements ILLMSystemMessage {}

    /**
     * 不可变用户消息。
     * @param getName 消息名称
     * @param getContents 正文
     * @param getProviderMetadata 透明状态
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record UserMessage(String getName, List<ILLMContent> getContents,
                               Map<String, Object> getProviderMetadata) implements ILLMUserMessage {}

    /**
     * 不可变模型消息，保留原始工具顺序和供应商推理回放状态。
     * @param getName 消息名称
     * @param getContents 正文
     * @param getProviderMetadata 透明状态
     * @param getToolCalls 模型请求的工具调用
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record AssistantMessage(String getName, List<ILLMContent> getContents,
                                    Map<String, Object> getProviderMetadata,
                                    List<ILLMToolCall> getToolCalls) implements ILLMAssistantMessage {}

    /**
     * 不可变工具回填。
     * @param getName 工具名称
     * @param getContents 工具输出
     * @param getProviderMetadata 透明状态
     * @param getToolCallId 对应的模型调用 ID
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record ToolMessage(String getName, List<ILLMContent> getContents,
                               Map<String, Object> getProviderMetadata, String getToolCallId) implements ILLMToolMessage {}

    /**
     * 不可变工具调用。
     * @param getId 调用 ID
     * @param getType 调用类型
     * @param getName 工具名称
     * @param getArguments 业务参数 JSON
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record Call(String getId, String getType, String getName, String getArguments) implements ILLMToolCall {}

    /**
     * 不可变供应商用量；零值不推断为缺失，也不推断为精确计费信息。
     * @param getCompletionTokens 输出 token
     * @param getPromptTokens 输入 token
     * @param getTotalTokens 总 token
     * @param getPromptCacheHitTokens 缓存命中 token
     * @param getPromptCacheMissTokens 缓存未命中 token
     * @param getReasoningTokens 推理 token
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record Usage(int getCompletionTokens, int getPromptTokens, int getTotalTokens,
                         int getPromptCacheHitTokens, int getPromptCacheMissTokens, int getReasoningTokens) implements ILLMUsage {}

    /**
     * 不可变候选，不把 logprobs 或推理状态拼入可见正文。
     * @param getFinishReason 统一终止标识
     * @param getIndex 候选序号
     * @param getMessage 模型消息，缺失时由编排拒绝
     * @param getLogprobs 供应商概率数据
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record Choice(String getFinishReason, int getIndex, ILLMAssistantMessage getMessage,
                          Map<String, Object> getLogprobs) implements ILLMChoice {}

    /**
     * 不可变单次响应，保留完整候选与供应商 usage。
     * @param getId 响应 ID
     * @param getChoices 候选列表
     * @param getObject 响应对象类型
     * @param getCreated 供应商时间
     * @param getModel 实际模型名称
     * @param getUsage 供应商用量，可为空
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    private record Result(String getId, List<ILLMChoice> getChoices, String getObject, Instant getCreated,
                          String getModel, ILLMUsage getUsage) implements ILLMResult {}
}
