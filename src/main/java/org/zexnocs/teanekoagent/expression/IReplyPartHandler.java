package org.zexnocs.teanekoagent.expression;

import org.zexnocs.teanekoagent.adapter.platform.AgentReplyPlan.Mode;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMContent;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;

import java.util.concurrent.CompletionStage;

/**
 * 有类型内容验证及编码，宿主在实际调用前统一守卫。
 * <br>2026/11/03：资源内容可借用原发送上下文，在异步解析后再次守卫。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @param <P> 不可变内容类型
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.6.2-l
 */
public interface IReplyPartHandler<P extends ReplyPlans.Content> {
    /** @return 准确内容类型 */
    Class<P> type();
    /**
     * 编译期间解析资源和客户端约束，允许后端异步访问原 DTO；默认字面内容无需 I/O。
     * 结果必须保持本类型不可变，并响应原控制；不能在此发送消息或关闭借用控制。
     * @param context 原可信上下文
     * @param content 原有类型内容
     * @return 完成所有资源校验的固定内容
     */
    default CompletionStage<P> resolve(ReplyCompileContext context, P content) {
        context.control().checkActive(); return java.util.concurrent.CompletableFuture.completedStage(content);
    }
    /**
     * 验证并生成交付描述，不发送。
     * @param content 内容
     * @param mode 回复方式
     * @param waitMillis 归一化等待
     * @return 固定正文和摘要的步骤
     */
    ReplyPlans.Step prepare(P content, Mode mode, int waitMillis);
    /**
     * 生成不带控制符的历史内容。
     * @param content 已验证内容
     * @return 模型消息内容
     */
    ILLMContent history(P content);
    /**
     * 等待真实平台回执。
     * @param data 原目标
     * @param content 内容
     * @param mode 回复方式
     * @return 实际发送阶段
     */
    CompletionStage<Void> send(ITeaNekoMessageData data, P content, Mode mode);
    /**
     * 2026/11/03：字面处理器兼容原入口；资源处理器可覆写并持有真实在途阶段。
     * @param context 原请求、控制与守卫
     * @param content 已冻结内容
     * @param mode 回复方式
     * @return 实际传输确认
     */
    default CompletionStage<Void> send(ReplySendContext context,P content,Mode mode){
        // 2026/11/03：注册入口已等待原守卫，字面发送器不重复执行同一轮检查。
        context.control().checkActive();return send(context.data(),content,mode);
    }
}
