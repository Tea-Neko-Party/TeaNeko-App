package org.zexnocs.teanekoplugin.agent.expression.expression;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.zexnocs.teanekoplugin.agent.expression.expression.IReplyProtocolParser;
import org.zexnocs.teanekoplugin.agent.expression.expression.ReplyPlans;
import org.zexnocs.teanekoplugin.agent.expression.expression.ReplyProtocolException;
import org.zexnocs.teanekoplugin.agent.expression.expression.TeaNekoReplyProtocol;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

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
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：明确逐行协议示例，允许控制行边缘空白并保留正文、围栏和当轮标识校验。
 * <br>2026/11/05：格式失败使用安全原因类型，允许日志与用户回执准确解释。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.9.0-l
 */
@Component
@TeaNekoReplyProtocol("teaneko:expression")
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.expression")
public final class ExpressionReplyProtocol implements IReplyProtocolParser {
    /**
     * 展示当前完整信封与转义规则。
     * 2026/11/05：使用实际多行示例，避免模型把正文接在控制行末尾。
     * @param nonce 固定当轮标识
     * @return 明确版本及边界的语法
     */
    @Override
    public String instructions(String nonce) {
        return """
                普通回复直接输出正文即可，不需要信封。只有需要分段或专用指令时才按以下逐行结构输出：
                ⟦TNK:%1$s|BEGIN|protocol=teaneko:expression|v=1⟧
                ⟦TNK:%1$s|TEXT|mode=plain|wait=0⟧
                第一段正文
                ⟦TNK:%1$s|TEXT|mode=plain|wait=800⟧
                第二段正文
                ⟦TNK:%1$s|END⟧
                每个控制行必须独占一行，⟧之后不能跟正文、标点或其他控制符；正文必须从下一行开始。
                所有控制行都使用本轮标识 %1$s，不得复制历史标识。可只有一段，也可按需重复 TEXT 与正文。
                wait 是前一段确认后的毫秒等待，首段通常为 0。
                正文代码围栏中的控制行保持字面；正文如需展示控制前缀，在其前加反斜杠转义。
                不得嵌套信封、用代码围栏包裹信封或在信封外夹正文；最后一行必须完整输出 END，不可省略。
                """.formatted(nonce);
    }
    /**
     * 完整校验后返回草稿，不交付半截控制文本。
     * 2026/11/05：仅控制行忽略边缘空白，正文与代码保真，错误标识及行内正文分别拒绝。
     * @param answer 正式答案
     * @param nonce 固定当轮标识
     * @param directives 当前协议指令入口
     * @return 有序草稿
     * @throws IllegalArgumentException 当信封不完整、控制行或正文无效时
     */
    @Override
    public List<ReplyPlans.Draft> parse(ILLMAssistantMessage answer, String nonce,
            Function<ReplyPlans.Directive, ReplyPlans.Draft> directives) {
        var lines = text(answer).strip().lines().toList();
        String prefix = "⟦TNK:" + nonce + "|";
        // 2026/11/05：分开解释头部与结束标记错误，日志无需包含模型原文即可定位。
        if (lines.size() < 2 || !lines.getFirst().strip().equals(prefix + "BEGIN|protocol=teaneko:expression|v=1⟧")) {
            throw new ReplyProtocolException("拟人回复开头缺少独立的有效 BEGIN 控制行。");
        }
        if (!lines.getLast().strip().equals(prefix + "END⟧")) {
            throw new ReplyProtocolException("拟人回复末尾缺少独立且使用本轮标识的 END 控制行；正文不能跟在结束标记之后。");
        }
        var result = new ArrayList<ReplyPlans.Draft>();
        String name = null;
        Map<String, String> fields = Map.of();
        var body = new ArrayList<String>();
        char fence = 0;
        int fenceSize = 0;
        for (int i = 1; i < lines.size() - 1; i++) {
            String line = lines.get(i);
            var stripped = line.stripLeading();
            if (fence != 0) {
                body.add(line);
                if (stripped.matches(java.util.regex.Pattern.quote(String.valueOf(fence)) + "{" + fenceSize + ",}\\s*")) {
                    fence = 0;
                }
                continue;
            }
            if (stripped.startsWith("```") || stripped.startsWith("~~~")) {
                if (name == null) {
                    throw new ReplyProtocolException("回复正文缺少所属指令。");
                }
                fence = stripped.charAt(0);
                fenceSize = 0;
                while (fenceSize < stripped.length() && stripped.charAt(fenceSize) == fence) {
                    fenceSize++;
                }
                body.add(line);
                continue;
            }
            if (stripped.startsWith("\\⟦TNK:")) {
                if (name == null) {
                    throw new ReplyProtocolException("回复转义内容缺少所属指令。");
                }
                body.add(line.substring(0, line.length() - stripped.length()) + stripped.substring(1));
                continue;
            }
            if (stripped.startsWith("⟦TNK:")) {
                String controlLine = stripped.stripTrailing();
                if (!controlLine.startsWith(prefix)) {
                    throw new ReplyProtocolException("回复控制行与当轮标识不一致。");
                }
                if (!controlLine.endsWith("⟧")) {
                    throw new ReplyProtocolException("回复控制行必须完整结束并独占一行，正文须另起一行。");
                }
                if (name != null) {
                    result.add(directives.apply(new ReplyPlans.Directive(name, fields, String.join("\n", body))));
                }
                if (result.size() >= 32) {
                    throw new ReplyProtocolException("回复指令数量超过限制。");
                }
                var tokens = controlLine.substring(prefix.length(), controlLine.length() - 1).split("\\|", -1);
                name = tokens[0];
                if (!name.matches("[A-Z][A-Z0-9_]{0,47}")) {
                    throw new ReplyProtocolException("回复指令名称无效。");
                }
                var values = new LinkedHashMap<String, String>();
                for (int n = 1; n < tokens.length; n++) {
                    var field = tokens[n].split("=", 2);
                    if (field.length != 2 || values.putIfAbsent(field[0], field[1]) != null) {
                        throw new ReplyProtocolException("回复指令字段缺失或重复。");
                    }
                }
                fields = Map.copyOf(values);
                body.clear();
            } else {
                if (name == null) {
                    throw new ReplyProtocolException("回复信封有未归属正文。");
                }
                body.add(line);
            }
        }
        if (fence != 0 || name == null) {
            throw new ReplyProtocolException("回复代码围栏或指令不完整。");
        }
        result.add(directives.apply(new ReplyPlans.Directive(name, fields, String.join("\n", body))));
        return List.copyOf(result);
    }
    /**
     * 合并正式文本，拒绝信封与附带图片混用。
     * 2026/11/05：错误仅携带解析器固定说明，供安全失败回执使用。
     * @param answer 正式答案
     * @return 有界完整文本
     * @throws IllegalArgumentException 当含非文本内容或容量超限时
     */
    public static String text(ILLMAssistantMessage answer) {
        var out = new StringBuilder();
        for (var item : answer.getContents()) {
            if (!(item.getContentPart() instanceof TextLLMContentPart words)) {
                throw new ReplyProtocolException("拟人信封不能混入额外媒体内容。");
            }
            if (!out.isEmpty()) {
                out.append('\n');
            }
            out.append(words.getText());
            if (out.length() > 64000) {
                throw new ReplyProtocolException("回复信封超出容量。");
            }
        }
        return out.toString();
    }
}
