package org.zexnocs.teanekoagent.expression;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.adapter.platform.AgentReplyPlan;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMAssistantMessage;

import java.util.List;
import java.util.function.Function;

/**
 * 保留旧普通图文的完整验证与分段语义，接入统一内容处理器。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.2-l
 */
@Component @TeaNekoReplyProtocol("teaneko:plain")
public class PlainReplyProtocol implements IReplyProtocolParser {
    /**
     * 适配原计划，不增加旧消息等待。
     * @param answer 正式答案
     * @param nonce 当轮标识
     * @param directives 指令入口，旧协议不使用
     * @return 兼容草稿
     * @throws IllegalArgumentException 当原图文或 JSON 合同无效时
     */
    @Override public List<ReplyPlans.Draft> parse(ILLMAssistantMessage answer, String nonce, Function<ReplyPlans.Directive,ReplyPlans.Draft> directives) {
        return AgentReplyPlan.fromAnswer(answer).stream().flatMap(plan -> plan.parts().stream()).map(part -> new ReplyPlans.Draft(
                part instanceof AgentReplyPlan.Text text ? new ReplyPlans.Text(text.value()) : new ReplyPlans.Image(((AgentReplyPlan.Image)part).url()), part.mode(), 0)).toList();
    }
}
