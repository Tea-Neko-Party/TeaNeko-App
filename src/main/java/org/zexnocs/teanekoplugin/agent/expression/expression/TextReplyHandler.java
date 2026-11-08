package org.zexnocs.teanekoplugin.agent.expression.expression;

import org.zexnocs.teanekoagent.runtime.delivery.AgentReplyPlan;

// 2026/11/07：表达业务归入插件，通用交付能力独立于业务组件。

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.runtime.delivery.AgentReplySender;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMContent;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.utils.StableDigest;

import java.util.concurrent.CompletionStage;

/**
 * 普通文本的内容验证、历史投影和原发送器编码。
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
 * @version 4.9.0-l
 */
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.expression")
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
