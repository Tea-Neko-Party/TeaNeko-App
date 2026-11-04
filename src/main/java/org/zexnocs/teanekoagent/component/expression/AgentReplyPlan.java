package org.zexnocs.teanekoagent.component.expression;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.zexnocs.teanekoagent.component.llm.framework.message.LLMSystemMessage;
import org.zexnocs.teanekoagent.component.llm.framework.message.content.ImageLLMContentPart;
import org.zexnocs.teanekoagent.component.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.component.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent.component.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent.runtime.AgentRunResult;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 只投影成功正式答案的有序图文计划，不把正文中的路径或 URL 自动解释成图片。
 * <br>2026/11/03：为注解式兼容解析器提供独立正式答案入口。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @param parts 完整有序文本段和图片，每段文本不超过两千字符
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.8.0-l
 */
public record AgentReplyPlan(List<Part> parts) {
    private static final JsonMapper JSON = JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();

    /**
     * 提供可选的模型回复形式合同，目标身份始终由宿主绑定。
     * 2026/11/03：只有实际采用表达合同后才允许新信封，其他路径继续旧规则。
     * @return 不携带平台身份或操作权限的系统说明
     */
    public static ILLMMessage instructions() {
        return LLMSystemMessage.builder().contents(new LLMContentListBuilder().addText("可直接输出普通图文答案；若宿主提供本轮表达合同，可按合同一次输出完整特殊信封；没有合同或使用旧回复形式时，输出严格 JSON：{\"teaneko_reply\":1,\"messages\":[{\"mode\":\"reply\",\"text\":\"正文\"}]}。mode 可为 reply（引用当前消息）、at（仅提及当前发起者）或 plain（普通发送）；messages 保持你选择的分段顺序，空数组表示不发言。不得指定用户、群、消息 ID 或平台操作。每段最多2000字符，最多32段，不加 Markdown。此格式只描述正式答案，不是工具或命令。").build()).build();
    }
    /**
     * 计划在任何发送开始前完整验证，拒绝无界或空段。
     * @param parts 完整文本段
     */
    public AgentReplyPlan {
        parts = List.copyOf(parts);
        if (parts.isEmpty() || parts.size() > 32 || parts.stream().filter(Image.class::isInstance).count() > 4
                || parts.stream().mapToLong(part -> part instanceof Text text ? text.value().length() : 0).sum() > 64000
                || parts.stream().mapToLong(part -> part instanceof Image(String url) ? url.length() : 0).sum() > 2_097_152) {
            throw new IllegalArgumentException("Agent 回复计划超出图文容量限制。");
        }
    }

    /**
     * 保留全文并在 Unicode 代理对边界分段，无正式文本时放弃自动发言。
     * @param result 已授权复核的原结果
     * @return 完整发送计划或明确无发言
     */
    public static Optional<AgentReplyPlan> from(AgentRunResult result) {
        if (result.status() != AgentRunResult.Status.SUCCESS) { return Optional.empty(); }
        var answer = result.answer().orElse(null); if (answer == null) { return Optional.empty(); }
        // 2026/11/03：解析器复用相同兼容校验，不构造虚假成功结果。
        return fromAnswer(answer);
    }
    /**
     * 2026/11/03：只解析调用者已经确认的正式答案，不执行发送。
     * @param answer 正式消息
     * @return 完整旧计划或无发言
     * @throws IllegalArgumentException 当正式内容或控制信封不合法时
     */
    public static Optional<AgentReplyPlan> fromAnswer(org.zexnocs.teanekoagent.component.llm.framework.message.interfaces.ILLMAssistantMessage answer) {
        var parts = new ArrayList<Part>(); var text = new StringBuilder(); var original = new StringBuilder();
        for (var content : answer.getContents()) {
            if (content.getContentPart() instanceof TextLLMContentPart words) {
                if (!text.isEmpty()) { text.append('\n'); } text.append(words.getText());
                if (!original.isEmpty()) { original.append('\n'); } original.append(words.getText());
                if (original.length() > 64000) { throw new IllegalArgumentException("Agent 回复正文过长。"); }
            } else if (content.getContentPart() instanceof ImageLLMContentPart image) {
                flush(text, parts); parts.add(new Image(image.getUrl()));
            } else { throw new IllegalArgumentException("Agent 正式答案含当前平台输出尚不支持的内容。"); }
        }
        if (original.toString().stripLeading().startsWith("{") && original.indexOf("\"teaneko_reply\"") >= 0) {
            if (parts.stream().anyMatch(Image.class::isInstance)) { throw new IllegalArgumentException("回复形式信封不能与额外图片内容混用。"); }
            return directed(original.toString());
        }
        flush(text, parts); return parts.isEmpty() ? Optional.empty() : Optional.of(new AgentReplyPlan(parts));
    }

    /**
     * 完整验证模型显式选择的段落，任何无效段都在发送前拒绝。
     * @param text 完整正式控制信封
     * @return 有界计划或明确弃言
     * @throws IllegalArgumentException 当版本、字段、形式或正文不合法时
     */
    private static Optional<AgentReplyPlan> directed(String text) {
        var root = JSON.readTree(text);
        if (!root.isObject() || root.size() != 2 || !root.path("teaneko_reply").isIntegralNumber()
                || !root.path("teaneko_reply").toString().equals("1") || !root.path("messages").isArray() || root.path("messages").size() > 32) {
            throw new IllegalArgumentException("Agent 回复形式信封无效。");
        }
        var parts = new ArrayList<Part>();
        for (var item : root.path("messages")) {
            if (!item.isObject() || item.size() != 2 || !item.path("mode").isString() || !item.path("text").isString()) {
                throw new IllegalArgumentException("Agent 回复段落字段无效。");
            }
            var mode = switch (item.path("mode").stringValue()) {
                case "reply" -> Mode.REPLY; case "at" -> Mode.AT; case "plain" -> Mode.PLAIN;
                default -> throw new IllegalArgumentException("Agent 回复形式不被支持。");
            };
            parts.add(new Text(item.path("text").stringValue(), mode));
        }
        return parts.isEmpty() ? Optional.empty() : Optional.of(new AgentReplyPlan(parts));
    }

    /**
     * 在图片边界前完整输出前置文本，不切开代理对或静默截断全文。
     * @param source 待输出文本
     * @param parts 原有序计划
     */
    private static void flush(StringBuilder source, List<Part> parts) {
        var text = source.toString(); source.setLength(0); if (text.isBlank()) { return; }
        for (int start = 0; start < text.length();) {
            int end = Math.min(text.length(), start + 2000);
            if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) { end--; }
            parts.add(new Text(text.substring(start, end))); start = end;
        }
    }

    /**
     * 可支持的明确输出类型，不接收任意平台操作或命令。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.5.0-l
     * @version 4.8.0-l
     */
    public sealed interface Part {
        /**
         * 普通图文默认引用原消息，不接受模型提供任意平台 ID。
         * @return 本段发送形式
         */
        default Mode mode() { return Mode.REPLY; }
    }

    /**
     * 仅针对当前原生来源的回复形式。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.5.0-l
     * @version 4.8.0-l
     */
    public enum Mode { REPLY, AT, PLAIN }

    /**
     * 字面文本段，正文 URL 不获得图片发送能力。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param value 文本
     * @param mode 宿主验证的当前来源回复形式
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.5.0-l
     * @version 4.8.0-l
     */
    public record Text(String value, Mode mode) implements Part {
        /**
         * 保留普通文本默认引用的构造方式。
         * @param value 字面文本
         */
        public Text(String value) { this(value, Mode.REPLY); }
        /**
         * 保存有界非空文本。
         * @param value 文本
         * @param mode 当前来源回复形式
         */
        public Text { if (mode == null || value == null || value.isEmpty() || value.length() > 2000) { throw new IllegalArgumentException("发送文本段不合法。"); } }
    }

    /**
     * 已显式声明的图片引用，不读取文件或下载内容。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     *
     * @param url 标准 HTTP(S)/data 图片引用
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.5.0-l
     * @version 4.8.0-l
     */
    public record Image(String url) implements Part {
        /**
         * 复用 LLM 图片校验并限制整体发送负载。
         * @param url 图片引用
         */
        public Image {
            if (url == null || url.length() > 2_097_152) { throw new IllegalArgumentException("发送图片引用超出限制。"); }
            url = new ImageLLMContentPart(url).getUrl();
        }

        /**
         * 将标准内联图片转换为现有平台发送器的 base64 引用，不改变远程 URL。
         * @return 平台支持的引用
         */
        public String platformReference() { return url.startsWith("data:") ? "base64://" + url.substring(url.indexOf(',') + 1) : url; }
    }
}
