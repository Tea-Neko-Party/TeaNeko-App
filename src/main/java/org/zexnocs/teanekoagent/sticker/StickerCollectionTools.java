package org.zexnocs.teanekoagent.sticker;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.adapter.command.NativeCommandBridge;
import org.zexnocs.teanekoagent.tool.TeaNekoAgentToolContext;
import org.zexnocs.teanekoagent.tool.api.TeaNekoAgentTool;
import org.zexnocs.teanekoagent.tool.api.TeaNekoAgentToolClass;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContent;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.message.api.content.IReplyTeaNekoContentPart;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * 注解式主动收藏，只读取当前消息及其真实引用，不接受任意 URL 或宿主路径。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.2-l
 */
@Component @TeaNekoAgentToolClass(value="sticker-collection",toolPackage="agent.sticker")
public final class StickerCollectionTools {
    private final NativeCommandBridge bridge;
    private final StickerCollectionService service;
    private final StickerLibrary library;
    /**
     * @param bridge 原来源租约
     * @param service 持久作业
     * @param library 当前采集策略
     */
    public StickerCollectionTools(NativeCommandBridge bridge,StickerCollectionService service,StickerLibrary library){this.bridge=bridge;this.service=service;this.library=library;}
    /**
     * @param context 宿主上下文
     * @return 当前真实原生用户可用时为真
     */
    public boolean available(TeaNekoAgentToolContext context){
        var data=bridge.source(context.request()).orElse(null);if(data==null){return false;}var self=data.getClient().getTeaNekoToolbox().getSelfPlatformId().orElse("");
        return library.allows(new Stickers.Source(context.request().actor().scope(),data.getUserData().getUuid(),data.getUserData().getUserIdInPlatform(),self,data.getMessageId(),0,data.getTime(),true,data.getUserData().getUuid(),""));
    }
    /**
     * 请求收藏不代表已入库，后台仍区分画作并执行限流与容量规则。
     * @param context 实际宿主调用
     * @param messageId 当前消息或其引用的真实 ID
     * @param imageIndex 图片序号
     * @return 持久入队回执
     */
    @TeaNekoAgentTool(value="sticker_collect",condition="available")
    @Description("收藏当前消息或当前消息直接引用中的图片为全实例共享表情包；只提交后台作业，不保证入库。不会强制收藏插画。随后用 sticker_collection_status 查看结果。")
    public CompletionStage<Stickers.Receipt> collect(TeaNekoAgentToolContext context,@Description("当前消息或直接引用消息的原始 ID") String messageId,
            @Description("图片序号，从0开始，最多15") int imageIndex){
        var invocation=context.requireInvocation();var source=bridge.source(context.request()).orElseThrow();
        boolean current=source.getMessageId().equals(messageId);
        boolean reply=source.getMessages().stream().map(ITeaNekoContent::getContentPart).filter(IReplyTeaNekoContentPart.class::isInstance).map(IReplyTeaNekoContentPart.class::cast).anyMatch(v->v.getId().equals(messageId));
        if(messageId.isBlank()||messageId.length()>128||(!current&&!reply)){throw new IllegalArgumentException("只能收藏当前消息或其直接引用的图片。");}
        CompletionStage<ITeaNekoMessageData> reading=current?CompletableFuture.completedStage(source):source.getClient().getTeaNekoToolbox().getGetMsgSender().getMsg(messageId).asStage()
                .toCompletableFuture().orTimeout(8,TimeUnit.SECONDS).thenApply(r->{var value=r!=null&&r.isSuccess()?r.getResult():null;if(value==null){throw new IllegalArgumentException("引用消息不可读取。");}return value;});
        return invocation.control().guard(reading).thenCompose(data->{
            invocation.control().checkActive();if(bridge.source(context.request()).filter(v->v==source).isEmpty()||!data.getMessageId().equals(messageId)
                    ||!data.getScopeId().equals(source.getScopeId())||!data.getClient().getClientId().equals(source.getClient().getClientId())){throw new IllegalStateException("原消息来源或引用归属已失效。");}
            return service.collect(data,true,context.request().actor().userId().orElseThrow(),imageIndex);
        });
    }
    /**
     * @param context 实际宿主调用
     * @param jobId 原回执中的作业 ID
     * @return 仅当前用户同区域可见的状态
     */
    @TeaNekoAgentTool(value="sticker_collection_status",condition="available")
    @Description("读取本人在当前区域提交的表情包采集作业状态；SAVED 才表示入库，不返回源图片链接或其他群聊天资料。")
    public CompletionStage<Stickers.Receipt> status(TeaNekoAgentToolContext context,@Description("sticker_collect 返回的 jobId") String jobId){
        context.requireInvocation();return service.status(UUID.fromString(jobId),context.request().actor().scope(),context.request().actor().userId().orElseThrow());
    }
}
