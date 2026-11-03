package org.zexnocs.teanekoagent.sticker;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.expression.IReplyDirectiveParser;
import org.zexnocs.teanekoagent.expression.ReplyPlans;
import org.zexnocs.teanekoagent.expression.ResourceReplySyntax;
import org.zexnocs.teanekoagent.expression.TeaNekoReplyDirective;

/**
 * 客户端原生表情指令，只产生草稿，资源证明由处理器异步验证。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.2-l
 */
@Component @TeaNekoReplyDirective("FACE")
public final class FaceReplyDirective implements IReplyDirectiveParser {
    /**
     * @param directive 原指令
     * @return 单项资源草稿
     */
    @Override public ReplyPlans.Draft parse(ReplyPlans.Directive directive){return ResourceReplySyntax.parse(directive, id->new MediaReplies.Face(id,null));}
    /** @return 本类型语法 */
    @Override public String instructions(){return "FACE|id=本轮提供的ID|mode=plain/reply|wait=非负毫秒；独立控制行，不跟正文，不附带at；仅可引用本轮已授权客户端原生表情。";}
}
