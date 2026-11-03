package org.zexnocs.teanekoagent.expression;
/**
 * 单条指令的纯字段解析，不负责授权和发送。
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.1-l
 */
public interface IReplyDirectiveParser {
    /**
     * 解析完整指令。
     * @param directive 字段和字面正文
     * @return 有类型草稿
     */
    ReplyPlans.Draft parse(ReplyPlans.Directive directive);
    /** @return 向模型展示的有界语法说明 */
    String instructions();
}
