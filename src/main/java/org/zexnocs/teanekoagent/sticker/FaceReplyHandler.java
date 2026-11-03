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
 * 客户端原生表情的当轮解析、普通历史投影与独立发送，不重选资源或追加模型调用。
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.1-l
 */
@Component @TeaNekoReplyPartHandler("teaneko:face")
public final class FaceReplyHandler implements IReplyPartHandler<MediaReplies.Face> {
    private final StickerReplyService service;
    /** @param service 原资源宿主 */ public FaceReplyHandler(StickerReplyService service){this.service=service;}
    /** @return 准确内容类型 */ @Override public Class<MediaReplies.Face> type(){return MediaReplies.Face.class;}
    /**
     * @param context 原编译上下文
     * @param content 原选择
     * @return 原请求证明固定的资源
     */
    @Override public CompletionStage<MediaReplies.Face> resolve(ReplyCompileContext context, MediaReplies.Face content){
        return CompletableFuture.completedStage(service.face(context,content.id())).thenApply(p->new MediaReplies.Face(content.id(),p));
    }
    /**
     * @param content 已解析内容
     * @param mode 方式
     * @param waitMillis 等待
     * @return 固定独立步骤
     */
    @Override public ReplyPlans.Step prepare(MediaReplies.Face content, Mode mode, int waitMillis){
        if(mode==Mode.AT){throw new IllegalArgumentException("资源消息不允许 at。");}var p=Objects.requireNonNull(content.permit(),"资源尚未解析。");
        return new ReplyPlans.Step(content,mode,waitMillis,"teaneko:face",1,projection(p),StableDigest.sha256("face",mode.name(),p.item().id(),Long.toString(p.item().revision()),p.item().hash()));
    }
    /**
     * @param content 已解析资源
     * @return 不含控制符或文件路径的普通文本
     */
    @Override public ILLMContent history(MediaReplies.Face content){return new LLMContentListBuilder().addText(projection(Objects.requireNonNull(content.permit()))).build().getFirst();}
    /**
     * @param data 原目标
     * @param content 原内容
     * @param mode 方式
     * @return 缺少上下文时明确拒绝
     */
    @Override public CompletionStage<Void> send(ITeaNekoMessageData data,MediaReplies.Face content,Mode mode){return CompletableFuture.failedStage(new IllegalStateException("资源发送需要原请求、控制与守卫。"));}
    /**
     * @param context 原发送上下文
     * @param content 冻结资源
     * @param mode 方式
     * @return 实际平台确认
     */
    @Override public CompletionStage<Void> send(ReplySendContext context, MediaReplies.Face content, Mode mode){return service.sendFace(context,Objects.requireNonNull(content.permit()),mode);}
    /**
     * @param permit 原许可
     * @return 有界语义说明
     */
    private String projection(MediaReplies.Permit permit){return "[客户端原生表情："+permit.item().description()+"]";}
}
