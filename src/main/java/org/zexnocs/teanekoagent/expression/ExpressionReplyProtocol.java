package org.zexnocs.teanekoagent.expression;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMAssistantMessage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 按独立控制行解析表达信封，代码围栏和转义内容保持字面语义。
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.1-l
 */
@Component @TeaNekoReplyProtocol("teaneko:expression")
public final class ExpressionReplyProtocol implements IReplyProtocolParser {
    /**
     * 展示当前完整信封与转义规则。
     * @param nonce 固定当轮标识
     * @return 明确版本及边界的语法
     */
    @Override public String instructions(String nonce) {
        return "按独立行输出：⟦TNK:" + nonce + "|BEGIN|protocol=teaneko:expression|v=1⟧，之后各段以⟦TNK:" + nonce
                + "|TEXT|mode=plain|wait=800⟧起始并跟正文，最后独立行⟦TNK:" + nonce + "|END⟧。wait是前一段确认后的毫秒等待，首段通常0。"
                + "代码围栏中控制行保持字面；正文如需展示控制前缀，在其前加反斜杠转义。不得嵌套信封或在信封外夹正文。";
    }
    /**
     * 完整校验后返回草稿，不交付半截控制文本。
     * @param answer 正式答案
     * @param nonce 固定当轮标识
     * @param directives 当前协议指令入口
     * @return 有序草稿
     * @throws IllegalArgumentException 当信封不完整、控制行或正文无效时
     */
    @Override public List<ReplyPlans.Draft> parse(ILLMAssistantMessage answer, String nonce, Function<ReplyPlans.Directive,ReplyPlans.Draft> directives) {
        String text = text(answer); var lines = text.strip().lines().toList(); String prefix = "⟦TNK:" + nonce + "|";
        if (lines.size() < 2 || !lines.getFirst().equals(prefix + "BEGIN|protocol=teaneko:expression|v=1⟧") || !lines.getLast().equals(prefix + "END⟧")) {
            throw new IllegalArgumentException("拟人回复信封版本、标识或结束位置无效。");
        }
        var result = new ArrayList<ReplyPlans.Draft>(); String name = null; Map<String,String> fields = Map.of(); var body = new ArrayList<String>();
        char fence = 0; int fenceSize = 0;
        for (int i = 1; i < lines.size() - 1; i++) {
            String line = lines.get(i); var stripped = line.stripLeading();
            if (fence != 0) {
                body.add(line);
                if (stripped.matches(java.util.regex.Pattern.quote(String.valueOf(fence)) + "{" + fenceSize + ",}\\s*")) { fence = 0; }
                continue;
            }
            if (stripped.startsWith("```") || stripped.startsWith("~~~")) {
                if (name == null) { throw new IllegalArgumentException("回复正文缺少所属指令。"); }
                fence = stripped.charAt(0); fenceSize = 0; while (fenceSize < stripped.length() && stripped.charAt(fenceSize) == fence) { fenceSize++; }
                body.add(line); continue;
            }
            if (line.startsWith("\\⟦TNK:")) { if (name == null) { throw new IllegalArgumentException("回复转义内容缺少所属指令。"); } body.add(line.substring(1)); continue; }
            if (line.startsWith("⟦TNK:")) {
                if (!line.startsWith(prefix) || !line.endsWith("⟧")) { throw new IllegalArgumentException("回复控制行标识或结尾无效。"); }
                if (name != null) { result.add(directives.apply(new ReplyPlans.Directive(name, fields, String.join("\n", body)))); }
                if (result.size() >= 32) { throw new IllegalArgumentException("回复指令数量超过限制。"); }
                var tokens = line.substring(prefix.length(), line.length() - 1).split("\\|", -1); name = tokens[0];
                if (!name.matches("[A-Z][A-Z0-9_]{0,47}")) { throw new IllegalArgumentException("回复指令名称无效。"); }
                var values = new LinkedHashMap<String,String>();
                for (int n = 1; n < tokens.length; n++) {
                    var field = tokens[n].split("=", 2);
                    if (field.length != 2 || values.putIfAbsent(field[0], field[1]) != null) { throw new IllegalArgumentException("回复指令字段缺失或重复。"); }
                }
                fields = Map.copyOf(values); body.clear();
            } else { if (name == null) { throw new IllegalArgumentException("回复信封有未归属正文。"); } body.add(line); }
        }
        if (fence != 0 || name == null) { throw new IllegalArgumentException("回复代码围栏或指令不完整。"); }
        result.add(directives.apply(new ReplyPlans.Directive(name, fields, String.join("\n", body))));
        return List.copyOf(result);
    }
    /**
     * 合并正式文本，拒绝信封与附带图片混用。
     * @param answer 正式答案
     * @return 有界完整文本
     * @throws IllegalArgumentException 当含非文本内容或容量超限时
     */
    public static String text(ILLMAssistantMessage answer) {
        var out = new StringBuilder();
        for (var item : answer.getContents()) {
            if (!(item.getContentPart() instanceof TextLLMContentPart words)) { throw new IllegalArgumentException("拟人信封不能混入额外媒体内容。"); }
            if (!out.isEmpty()) { out.append('\n'); } out.append(words.getText());
            if (out.length() > 64000) { throw new IllegalArgumentException("回复信封超出容量。"); }
        }
        return out.toString();
    }
}
