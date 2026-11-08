package org.zexnocs.teanekoagent.llm.framework.response;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

import org.zexnocs.teanekoagent.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMAssistantMessage;

import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 校验模型可见正文中的内部工具标记，不解析参数或代替结构化工具调用。
 * <br>2026/11/05：覆盖单/双竖线、空白及行内标记，保留代码围栏中的协议示例。
 * <br>2026/11/07：迁入独立 LLM 子模块，保留公开协议与原配置语义。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.3-l
 * @version 4.9.0-l
 */
public final class LLMVisibleTextValidator {
    private static final Pattern TOOL_MARKUP = Pattern.compile(
            "<\\h*/?\\h*(?:[｜|]\\h*)+DSML\\h*(?:[｜|]\\h*)+(?:calls|function_calls|invoke|parameter)\\b",
            Pattern.CASE_INSENSITIVE);

    /** 禁止实例化无状态校验器。 */
    private LLMVisibleTextValidator() { }

    /**
     * 合并真实文本块后校验，图片及供应商透明状态不属于可见正文。
     * @param message 已冻结的 assistant 消息
     * @throws LLMResponseProtocolException 当正文包含未隔离的内部工具标记时
     */
    public static void validate(ILLMAssistantMessage message) {
        validate(message.getContents().stream()
                .filter(content -> content.getContentPart() instanceof TextLLMContentPart)
                .map(content -> ((TextLLMContentPart) content.getContentPart()).getText())
                .collect(Collectors.joining("\n")));
    }

    /**
     * 检查原答案或协议解码后的字面正文，不让 JSON 转义绕过交付前检查。
     * @param text 即将作为普通正文使用的文本
     * @throws LLMResponseProtocolException 当代码围栏之外存在内部工具控制标记时
     */
    public static void validate(String text) {
        char fence = 0;
        int fenceSize = 0;
        for (String line : text.lines().toList()) {
            String stripped = line.stripLeading();
            if (fence != 0) {
                int end = 0;
                while (end < stripped.length() && stripped.charAt(end) == fence) {
                    end++;
                }
                if (end >= fenceSize && stripped.substring(end).isBlank()) {
                    fence = 0;
                }
            } else if (stripped.startsWith("```") || stripped.startsWith("~~~")) {
                fence = stripped.charAt(0);
                fenceSize = 0;
                while (fenceSize < stripped.length() && stripped.charAt(fenceSize) == fence) {
                    fenceSize++;
                }
            } else if (TOOL_MARKUP.matcher(stripped).find()) {
                throw new LLMResponseProtocolException();
            }
        }
    }
}
