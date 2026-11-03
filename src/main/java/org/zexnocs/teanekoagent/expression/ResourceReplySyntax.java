package org.zexnocs.teanekoagent.expression;

import org.zexnocs.teanekoagent.adapter.platform.AgentReplyPlan.Mode;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 独立资源指令共用的字段边界，不承担按内容类型分派。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.6.2-l
 */
public final class ResourceReplySyntax{
    /** 禁止实例化无状态语法工具。 */ private ResourceReplySyntax(){}
    /**
     * @param directive 完整指令
     * @param content 类型工厂
     * @return 只含单一资源的草稿
     */
    public static ReplyPlans.Draft parse(ReplyPlans.Directive directive,Function<String,ReplyPlans.Content> content){
        if(!directive.body().isBlank()||!Set.of("id","mode","wait").containsAll(directive.fields().keySet())){throw new IllegalArgumentException("资源指令只能包含 ID、方式与等待，不能附带正文。");}
        String id=directive.fields().getOrDefault("id","");if(!id.matches("[a-zA-Z0-9_.:-]{1,80}")){throw new IllegalArgumentException("资源 ID 无效。");}
        var mode=Map.of("plain",Mode.PLAIN,"reply",Mode.REPLY).get(directive.fields().getOrDefault("mode","plain"));if(mode==null){throw new IllegalArgumentException("资源只能 plain 或 reply，不能附带 at。");}
        Integer wait=null;var raw=directive.fields().get("wait");if(raw!=null){if(!raw.matches("[0-9]{1,5}")||Integer.parseInt(raw)>60000){throw new IllegalArgumentException("资源等待必须为有界非负毫秒。");}wait=Integer.valueOf(raw);}
        return new ReplyPlans.Draft(content.apply(id),mode,wait);
    }
}
