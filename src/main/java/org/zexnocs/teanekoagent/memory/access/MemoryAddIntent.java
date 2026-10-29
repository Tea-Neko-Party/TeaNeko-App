package org.zexnocs.teanekoagent.memory.access;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import com.fasterxml.jackson.annotation.JsonValue;
import org.zexnocs.teanekoagent.agent.AgentActorContext;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 由可信入口从单条原始用户消息识别的保守新增意图，不从模型结论、历史或工具文字授予写权限。
 * 仅支持明确冒号前缀，正文保持原文；其他自然语言需上层澄清或未来独立可信策略处理。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@JsonIgnoreType
public final class MemoryAddIntent {
    private static final List<String> PREFIXES = List.of("请记住：", "请记住:", "记住：", "记住:");
    private final MemoryAccessContext context;
    private final String text;

    /**
     * 保存已经识别的原始意图，禁止通过公开构造或模型布尔值创建授权。
     * @param context 原始可信请求与空间
     * @param text 明确要求记住的原文
     */
    private MemoryAddIntent(MemoryAccessContext context, String text) { this.context = context; this.text = text; }

    /**
     * 从当前发起者的原始单条消息识别明确前缀，不扫描引用、历史或拼接的 Prompt。
     * 宿主必须保证消息来自 context.actor 的 sourceId；此方法不承担平台验真。
     * @param context 宿主从可信绑定取得的上下文
     * @param originalUserText 当前原始消息文本，不能传入模型生成的文字
     * @return 已绑定原请求与原文的意图；系统任务、空白、超限或其他格式为空
     * @throws NullPointerException 当上下文或原消息为空时
     */
    public static Optional<MemoryAddIntent> fromUserText(MemoryAccessContext context, String originalUserText) {
        Objects.requireNonNull(context, "明确新增上下文不能为空。"); Objects.requireNonNull(originalUserText, "明确新增原消息不能为空。");
        if (context.actor().origin() != AgentActorContext.Origin.USER) { return Optional.empty(); }
        var input = originalUserText.strip();
        for (var prefix : PREFIXES) {
            if (input.startsWith(prefix)) {
                var body = input.substring(prefix.length()).strip();
                return body.isBlank() || body.length() > 4096 ? Optional.empty() : Optional.of(new MemoryAddIntent(context, body));
            }
        }
        return Optional.empty();
    }

    /**
     * 检查完整原上下文与待写原文，模型不能改写主体、请求、空间或记住的内容。
     * @param expected 实际操作的可信上下文
     * @param candidate 待追加正文
     * @return 原始绑定及正文完全一致时为 true
     */
    boolean permits(MemoryAccessContext expected, String candidate) { return context.equals(expected) && text.equals(candidate); }

    /**
     * 只向可信宿主提供已识别原文，便于构建有界事实，不自动解释事件时间或个人关系。
     * @return 用户明确提供的正文
     */
    public String text() { return text; }

    /**
     * 避免通用日志与 JSON 序列化把授权对象或原文输出给模型。
     * @return 固定脱敏说明
     */
    @Override @JsonValue public String toString() { return "明确新增意图[已隐藏原文与可信绑定]"; }
}
