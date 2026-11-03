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
 * 普通明确图片的兼容处理，不把链接文本变成图片。
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.1-l
 */
@Component @TeaNekoReplyPartHandler("teaneko:image")
public final class ImageReplyHandler implements IReplyPartHandler<ReplyPlans.Image> {
    /** @return 图片值类型 */
    @Override public Class<ReplyPlans.Image> type() { return ReplyPlans.Image.class; }
    /**
     * 验证原图片引用。
     * @param content 内容
     * @param mode 原回复方式
     * @param waitMillis 等待
     * @return 图片投影
     * @throws IllegalArgumentException 当图片方式不被兼容合同支持时
     */
    @Override public ReplyPlans.Step prepare(ReplyPlans.Image content, AgentReplyPlan.Mode mode, int waitMillis) {
        new AgentReplyPlan.Image(content.url());
        if (mode != AgentReplyPlan.Mode.REPLY) { throw new IllegalArgumentException("普通图片沿用原引用回复方式。"); }
        return new ReplyPlans.Step(content, mode, waitMillis, "teaneko:image", 1, "[已确认发送图片]", StableDigest.sha256("image", content.url()));
    }
    /**
     * 沿用原图像历史合同。
     * @param content 图片
     * @return 原显式图片
     */
    @Override public ILLMContent history(ReplyPlans.Image content) { return new LLMContentListBuilder().addImage(content.url()).build().getFirst(); }
    /**
     * 等待原图片发送确认。
     * @param data 目标
     * @param content 图片
     * @param mode 原方式
     * @return 实际回执
     */
    @Override public CompletionStage<Void> send(ITeaNekoMessageData data, ReplyPlans.Image content, AgentReplyPlan.Mode mode) {
        return AgentReplySender.send(data, new AgentReplyPlan.Image(content.url()));
    }
}
