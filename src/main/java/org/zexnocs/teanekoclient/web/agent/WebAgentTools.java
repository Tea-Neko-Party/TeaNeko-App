package org.zexnocs.teanekoclient.web.agent;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.component.llm.framework.message.content.ImageLLMContentPart;
import org.zexnocs.teanekoagent.component.llm.framework.message.interfaces.ILLMContent;
import org.zexnocs.teanekoagent.component.llm.framework.tool.LLMToolOutput;
import org.zexnocs.teanekoagent.component.llm.framework.tool.LLMToolSet;
import org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolContext;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentTool;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentToolClass;
import org.zexnocs.teanekoagent.integration.command.NativeCommandBridge;
import org.zexnocs.teanekoagent.integration.message.NativeMessageInput;
import org.zexnocs.teanekoclient.web.WebMessageData;
import org.zexnocs.teanekoclient.web.WebTeaNekoClient;
import org.zexnocs.teanekocore.framework.description.Description;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 注解式网页消息查询，严格绑定原轮次聊天，引用图片按真实视觉结果回填模型。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/05：通过明确的 getter 借用执行资源，保持异步完成后的原关闭责任。
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.8.0-l
 */
@Component
@TeaNekoAgentToolClass(value = "web", toolPackage = "agent.web", client = WebTeaNekoClient.ID)
public final class WebAgentTools {
    private final NativeCommandBridge sources;
    private final ObjectMapper mapper;

    /**
     * @param sources 原轮次来源租约
     * @param mapper 原有界结果转换器
     */
    public WebAgentTools(NativeCommandBridge sources, ObjectMapper mapper) {
        this.sources = sources;
        this.mapper = mapper;
    }

    /**
     * @param context 宿主工具上下文
     * @return 当前持有真实网页来源时可见
     */
    public boolean available(TeaNekoAgentToolContext context) {
        return sources.source(context.request()).filter(WebMessageData.class::isInstance).isPresent();
    }

    /**
     * 2026/11/05：通过明确的 getter 借用执行资源，保持异步完成后的原关闭责任。
     * @param messageId 当前网页聊天内的原消息号
     * @param context 宿主注入轮次上下文
     * @return 有界文字和真实图片；查询失败不暴露原异常
     */
    @TeaNekoAgentTool(value = "web_get_message", condition = "available")
    @Description("查询当前网页聊天中原消息的正文、发送者、时间和图片；用于读取引用图片，不能跨聊天查询。")
    public CompletionStage<LLMToolOutput> message(@Description("当前网页聊天的 web-msg 消息编号。") String messageId,
                                                 TeaNekoAgentToolContext context) {
        try {
            var source = sources.source(context.request()).filter(WebMessageData.class::isInstance)
                    .map(WebMessageData.class::cast).orElseThrow(() -> new SecurityException("网页原生来源已失效。"));
            source.checkCommandSource();
            return context.requireInvocation().getControl().guard(source.getClient().getTeaNekoToolbox().getGetMsgSender()
                    .getMsg(messageId).asStage()).thenApply(result -> {
                source.checkCommandSource();
                var data = result.isSuccess() ? result.getResult() : null;
                if (!(data instanceof WebMessageData quoted) || !quoted.roomId().equals(source.roomId())
                        || !data.getMessageId().equals(messageId) || !data.getScopeId().equals(source.getScopeId())
                        || !data.getClient().getClientId().equals(WebTeaNekoClient.ID)) {
                    throw new SecurityException("原消息不属于当前网页聊天。");
                }
                var input = NativeMessageInput.from(data);
                var images = input.message().getContents().stream().map(ILLMContent::getContentPart)
                        .filter(ImageLLMContentPart.class::isInstance).map(ImageLLMContentPart.class::cast).toList();
                String text = mapper.writeValueAsString(Map.of("context", NativeMessageInput.contextText(data), "text", input.text()));
                if (text.length() > LLMToolSet.DEFAULT_RESULT_CHARACTER_LIMIT) {
                    text = "消息文字超出工具预算；保留图片供回答本轮问题。";
                }
                return new LLMToolOutput(text, images);
            }).exceptionally(ignored -> error());
        } catch (RuntimeException unavailable) {
            return CompletableFuture.completedStage(error());
        }
    }

    /** @return 固定脱敏失败结果 */
    private LLMToolOutput error() {
        return new LLMToolOutput("网页消息不可用、已离开保留窗口或不属于当前聊天。");
    }
}
