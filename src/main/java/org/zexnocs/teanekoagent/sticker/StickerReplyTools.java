package org.zexnocs.teanekoagent.sticker;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.tool.TeaNekoAgentToolContext;
import org.zexnocs.teanekoagent.tool.api.TeaNekoAgentTool;
import org.zexnocs.teanekoagent.tool.api.TeaNekoAgentToolClass;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.concurrent.CompletionStage;

/**
 * 依赖本轮实际资源资料的注解工具，由明确 Feature 安装。
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.1-l
 */
@Component @TeaNekoAgentToolClass(value="sticker-selection",toolPackage="agent.sticker",managedByFeature=true)
public final class StickerReplyTools{
    private final StickerReplyService service;
    /** @param service 当轮资源宿主 */ public StickerReplyTools(StickerReplyService service){this.service=service;}
    /**
     * @param context 实际宿主上下文
     * @return 资料保留且当前图片能力可用
     */
    public boolean available(TeaNekoAgentToolContext context){return service.canList(context.request(),context.input());}
    /**
     * @param context 真实调用
     * @param categoryId 本轮提供的类别
     * @return 完整类别回执
     */
    @TeaNekoAgentTool(value="sticker_list",condition="available")
    @Description("取得本轮已提供类别的全部可用表情包 ID、revision 和用途。complete=true 才可在最终 STICKER 指令中引用；不完整或预算不足时不用该类。最多展开两个类别，不返回来源聊天或路径。")
    public CompletionStage<MediaReplies.Page> list(TeaNekoAgentToolContext context,@Description("本轮类别摘要中的稳定类别 ID") String categoryId){
        var invocation=context.requireInvocation();return service.list(context.request(),java.util.Objects.requireNonNull(context.input()),categoryId,invocation.control());
    }
}
