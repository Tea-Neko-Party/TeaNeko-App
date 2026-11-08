package org.zexnocs.teanekoplugin.agent.expression.sticker;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.zexnocs.teanekoplugin.agent.expression.expression.IReplyPartHandler;
import org.zexnocs.teanekoplugin.agent.expression.expression.ReplyCompileContext;
import org.zexnocs.teanekoplugin.agent.expression.expression.ReplyPlans;
import org.zexnocs.teanekoplugin.agent.expression.expression.ReplySendContext;
import org.zexnocs.teanekoplugin.agent.expression.expression.TeaNekoReplyPartHandler;
import org.zexnocs.teanekoplugin.agent.expression.expression.NativeFaceService;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.runtime.delivery.AgentReplyPlan.Mode;
import org.zexnocs.teanekoplugin.agent.expression.expression.*;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMContent;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.utils.StableDigest;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 客户端原生表情的当轮解析、普通历史投影与独立发送，不重选资源或追加模型调用。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：原生表情解析和发送使用独立表达目录，不借用图库事务。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.9.0-l
 */
@Component @TeaNekoReplyPartHandler("teaneko:face")
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.expression")
public final class FaceReplyHandler implements IReplyPartHandler<MediaReplies.Face> {
    private final NativeFaceService service;
    /** @param service 2026/11/05：独立原生表情目录及许可服务，不经过图库 */
    public FaceReplyHandler(NativeFaceService service){this.service=service;}
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
