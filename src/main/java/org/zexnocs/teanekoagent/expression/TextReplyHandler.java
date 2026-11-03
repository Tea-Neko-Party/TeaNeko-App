package org.zexnocs.teanekoagent.expression;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.adapter.platform.AgentReplyPlan;
import org.zexnocs.teanekoagent.adapter.platform.AgentReplySender;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMContent;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.utils.StableDigest;

import java.util.concurrent.CompletionStage;

/**
 * 普通文本的内容验证、历史投影和原发送器编码。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.2-l
 */
@Component @TeaNekoReplyPartHandler("teaneko:text")
public final class TextReplyHandler implements IReplyPartHandler<ReplyPlans.Text> {
    /** @return 文本值类型 */
    @Override public Class<ReplyPlans.Text> type() { return ReplyPlans.Text.class; }
    /**
     * 冻结文本和原模式。
     * @param content 文本
     * @param mode 方式
     * @param waitMillis 等待
     * @return 冻结步骤
     * @throws IllegalArgumentException 当文本或回复方式无效时
     */
    @Override public ReplyPlans.Step prepare(ReplyPlans.Text content, AgentReplyPlan.Mode mode, int waitMillis) {
        new AgentReplyPlan.Text(content.value(), mode);
        return new ReplyPlans.Step(content, mode, waitMillis, "teaneko:text", 1, content.value(), StableDigest.sha256("text", mode.name(), content.value()));
    }
    /**
     * 保留字面正文。
     * @param content 文本
     * @return 普通内容
     */
    @Override public ILLMContent history(ReplyPlans.Text content) { return new LLMContentListBuilder().addText(content.value()).build().getFirst(); }
    /**
     * 交给原发送链等待平台确认。
     * @param data 目标
     * @param content 内容
     * @param mode 方式
     * @return 实际回执
     */
    @Override public CompletionStage<Void> send(ITeaNekoMessageData data, ReplyPlans.Text content, AgentReplyPlan.Mode mode) {
        return AgentReplySender.send(data, new AgentReplyPlan.Text(content.value(), mode));
    }
}
