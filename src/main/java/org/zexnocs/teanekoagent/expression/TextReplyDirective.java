package org.zexnocs.teanekoagent.expression;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.adapter.platform.AgentReplyPlan.Mode;

import java.util.Map;
import java.util.Set;

/**
 * 字面文本指令，模型只能建议当前来源回复方式和相对等待。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.6.2-l
 */
@Component @TeaNekoReplyDirective("TEXT")
public final class TextReplyDirective implements IReplyDirectiveParser {
    /**
     * 解析受控字段，不从正文提取操作。
     * @param directive 完整指令
     * @return 文本草稿
     * @throws IllegalArgumentException 当字段、正文或等待无效时
     */
    @Override public ReplyPlans.Draft parse(ReplyPlans.Directive directive) {
        if (!Set.of("mode", "wait").containsAll(directive.fields().keySet()) || directive.body().isBlank() || directive.body().length() > 2000) { throw new IllegalArgumentException("文本指令字段或正文无效。"); }
        var mode = Map.of("reply",Mode.REPLY,"at",Mode.AT,"plain",Mode.PLAIN).get(directive.fields().getOrDefault("mode","plain"));
        if (mode == null) { throw new IllegalArgumentException("回复方式只允许 reply、at 或 plain。"); }
        String delay = directive.fields().get("wait"); Integer millis = null;
        if (delay != null) {
            if (!delay.matches("[0-9]{1,5}") || Integer.parseInt(delay) > 60000) { throw new IllegalArgumentException("回复等待只能是有界非负毫秒整数。"); }
            millis = Integer.parseInt(delay);
        }
        return new ReplyPlans.Draft(new ReplyPlans.Text(directive.body()), mode, millis);
    }
    /** @return 当前文本指令的准确语法 */
    @Override public String instructions() { return "TEXT|mode=reply/at/plain|wait=非负毫秒；后续正文至下一控制行，最多2000字；mode默认plain，wait可省略。"; }
}
