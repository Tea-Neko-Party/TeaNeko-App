package org.zexnocs.teanekoplugin.agent.expression.sticker;

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoplugin.agent.expression.expression.IReplyDirectiveParser;
import org.zexnocs.teanekoplugin.agent.expression.expression.ReplyPlans;
import org.zexnocs.teanekoplugin.agent.expression.expression.ResourceReplySyntax;
import org.zexnocs.teanekoplugin.agent.expression.expression.TeaNekoReplyDirective;

/**
 * 客户端原生表情指令，只产生草稿，资源证明由处理器异步验证。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.9.0-l
 */
@Component @TeaNekoReplyDirective("FACE")
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.expression")
public final class FaceReplyDirective implements IReplyDirectiveParser {
    /**
     * @param directive 原指令
     * @return 单项资源草稿
     */
    @Override public ReplyPlans.Draft parse(ReplyPlans.Directive directive){return ResourceReplySyntax.parse(directive, id->new MediaReplies.Face(id,null));}
    /** @return 本类型语法 */
    @Override public String instructions(){return "FACE|id=本轮提供的ID|mode=plain/reply|wait=非负毫秒；独立控制行，不跟正文，不附带at；仅可引用本轮已授权客户端原生表情。";}
}
