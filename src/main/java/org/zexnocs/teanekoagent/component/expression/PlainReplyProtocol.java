package org.zexnocs.teanekoagent.component.expression;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.component.llm.framework.message.interfaces.ILLMAssistantMessage;

import java.util.List;
import java.util.function.Function;

/**
 * 保留旧普通图文的完整验证与分段语义，接入统一内容处理器。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/05：移除包迁移后重复或未使用的类型导入。
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.8.0-l
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
