package org.zexnocs.teanekoagent.runtime.delivery;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

// 2026/11/07：表达业务归入插件，通用交付能力独立于业务组件。

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.llm.framework.message.LLMSystemMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.ImageLLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent.llm.framework.response.LLMResponseProtocolException;
import org.zexnocs.teanekoagent.llm.framework.response.LLMVisibleTextValidator;
import org.zexnocs.teanekoagent.runtime.AgentRunResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 只投影成功正式答案的有序图文计划，不把正文中的路径或 URL 自动解释成图片。
 * <br>2026/11/03：为注解式兼容解析器提供独立正式答案入口。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：在 JSON 解码与分段完成后统一检查工具标记，保留跨分段的代码示例。
 * <br>2026/11/06：可选旧信封使用共用类型描述和严格 JSON 解码；诊断不生成虚假请求号。
 * @param parts 完整有序文本段和图片，每段文本不超过两千字符
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
public record AgentReplyPlan(List<IPart> parts) {
    /**
     * 2026/11/07：基础聊天只约定普通正式图文，业务协议由对应组件提供。
     * @return 不包含业务控制信封的输出约束
     */
    public static ILLMMessage instructions() {
        return LLMSystemMessage.builder().contents(new LLMContentListBuilder()
                .addText("请直接输出正式图文答案，不输出内部工具控制标记；额外回复格式仅在本轮提供相应合同后使用。")
                .build()).build();
    }

    /**
     * 计划在任何发送开始前完整验证，拒绝无界或空段。
     * 2026/11/05：解码后的完整正文复用工具协议检查，不逐段丢失代码围栏状态。
     * @param parts 完整文本段
     * @throws IllegalArgumentException 当容量非法时
     * @throws LLMResponseProtocolException 当正文泄漏内部工具控制标记时
     */
    public AgentReplyPlan {
        parts = List.copyOf(parts);
        if (parts.isEmpty() || parts.size() > 32 || parts.stream().filter(Image.class::isInstance).count() > 4
                || parts.stream().mapToLong(part -> part instanceof Text text ? text.value().length() : 0).sum() > 64000
                || parts.stream().mapToLong(part -> part instanceof Image(String url) ? url.length() : 0).sum() > 2_097_152) {
            throw new IllegalArgumentException("Agent 回复计划超出图文容量限制。");
        }
        LLMVisibleTextValidator.validate(parts.stream().filter(Text.class::isInstance)
                .map(part -> ((Text) part).value()).collect(Collectors.joining("\n")));
    }

    /**
     * 保留全文并在 Unicode 代理对边界分段，无正式文本时放弃自动发言。
     * @param result 已授权复核的原结果
     * @return 完整发送计划或明确无发言
     */
    public static Optional<AgentReplyPlan> from(AgentRunResult result) {
        if (result.status() != AgentRunResult.Status.SUCCESS) { return Optional.empty(); }
        var answer = result.answer().orElse(null); if (answer == null) { return Optional.empty(); }
        // 2026/11/06：解析器复用相同兼容校验，并保留宿主的真实请求号。
        return fromAnswer(answer, result.requestId());
    }
    /**
     * 2026/11/03：只解析调用者已经确认的正式答案，不执行发送。
     * @param answer 正式消息
     * @return 完整旧计划或无发言
     * @throws IllegalArgumentException 当正式内容或控制信封不合法时
     */
    public static Optional<AgentReplyPlan> fromAnswer(ILLMAssistantMessage answer) {
        return fromAnswer(answer, null);
    }

    /**
     * 2026/11/06：解析正式答案时携带可用的宿主请求号，旧入口缺少上下文时明确报告未取得。
     * @param answer 已确认的正式消息
     * @param request 原请求号；未取得时为空
     * @return 完整旧计划或无发言
     * @throws IllegalArgumentException 当正式内容或控制信封不合法时
     */
    public static Optional<AgentReplyPlan> fromAnswer(ILLMAssistantMessage answer, @Nullable UUID request) {
        var parts = new ArrayList<IPart>();
        var text = new StringBuilder();
        var original = new StringBuilder();
        for (var content : answer.getContents()) {
            if (content.getContentPart() instanceof TextLLMContentPart words) {
                if (!text.isEmpty()) { text.append('\n'); } text.append(words.getText());
                if (!original.isEmpty()) { original.append('\n'); } original.append(words.getText());
                if (original.length() > 64000) { throw new IllegalArgumentException("Agent 回复正文过长。"); }
            } else if (content.getContentPart() instanceof ImageLLMContentPart image) {
                flush(text, parts); parts.add(new Image(image.getUrl()));
            } else { throw new IllegalArgumentException("Agent 正式答案含当前平台输出尚不支持的内容。"); }
        }
        flush(text, parts); return parts.isEmpty() ? Optional.empty() : Optional.of(new AgentReplyPlan(parts));
    }

    /**
     * 在图片边界前完整输出前置文本，不切开代理对或静默截断全文。
     * @param source 待输出文本
     * @param parts 原有序计划
     */
    private static void flush(StringBuilder source, List<IPart> parts) {
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
    public sealed interface IPart {
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
    public record Text(String value, Mode mode) implements IPart {
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
    public record Image(String url) implements IPart {
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
