package org.zexnocs.teanekoagent.sticker;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.adapter.platform.AgentReplyPlan.Mode;
import org.zexnocs.teanekoagent.expression.*;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMContent;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.utils.StableDigest;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 表情包的当轮解析、普通历史投影与独立发送，不重选资源或追加模型调用。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.2-l
 */
@Component @TeaNekoReplyPartHandler("teaneko:sticker")
public final class StickerReplyHandler implements IReplyPartHandler<MediaReplies.Sticker> {
    private final StickerReplyService service;
    /** @param service 原资源宿主 */ public StickerReplyHandler(StickerReplyService service){this.service=service;}
    /** @return 准确内容类型 */ @Override public Class<MediaReplies.Sticker> type(){return MediaReplies.Sticker.class;}
    /**
     * @param context 原编译上下文
     * @param content 原选择
     * @return 原请求证明固定的资源
     */
    @Override public CompletionStage<MediaReplies.Sticker> resolve(ReplyCompileContext context, MediaReplies.Sticker content){
        return service.sticker(context,content.id()).thenApply(p->new MediaReplies.Sticker(content.id(),p));
    }
    /**
     * @param content 已解析内容
     * @param mode 方式
     * @param waitMillis 等待
     * @return 固定独立步骤
     */
    @Override public ReplyPlans.Step prepare(MediaReplies.Sticker content, Mode mode, int waitMillis){
        if(mode==Mode.AT){throw new IllegalArgumentException("资源消息不允许 at。");}var p=Objects.requireNonNull(content.permit(),"资源尚未解析。");
        return new ReplyPlans.Step(content,mode,waitMillis,"teaneko:sticker",1,projection(p),StableDigest.sha256("sticker",mode.name(),p.item().id(),Long.toString(p.item().revision()),p.item().hash()));
    }
    /**
     * @param content 已解析资源
     * @return 不含控制符或文件路径的普通文本
     */
    @Override public ILLMContent history(MediaReplies.Sticker content){return new LLMContentListBuilder().addText(projection(Objects.requireNonNull(content.permit()))).build().getFirst();}
    /**
     * @param data 原目标
     * @param content 原内容
     * @param mode 方式
     * @return 缺少上下文时明确拒绝
     */
    @Override public CompletionStage<Void> send(ITeaNekoMessageData data,MediaReplies.Sticker content,Mode mode){return CompletableFuture.failedStage(new IllegalStateException("资源发送需要原请求、控制与守卫。"));}
    /**
     * @param context 原发送上下文
     * @param content 冻结资源
     * @param mode 方式
     * @return 实际平台确认
     */
    @Override public CompletionStage<Void> send(ReplySendContext context, MediaReplies.Sticker content, Mode mode){return service.sendSticker(context,Objects.requireNonNull(content.permit()),mode);}
    /**
     * @param permit 原许可
     * @return 有界语义说明
     */
    private String projection(MediaReplies.Permit permit){return "[表情包："+permit.item().description()+"]";}
}
