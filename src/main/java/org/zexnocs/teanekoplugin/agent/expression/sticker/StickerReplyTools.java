package org.zexnocs.teanekoplugin.agent.expression.sticker;

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolContext;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentTool;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentToolClass;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.concurrent.CompletionStage;

/**
 * 依赖本轮实际资源资料的注解工具，由明确 Feature 安装。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：修正媒体工具的显式能力归属，保持原资料与预算约束。
 * <br>2026/11/04：按实际业务组件装配开放工具，保留原权限与客户端校验。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：移除包迁移后重复或未使用的类型导入。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.9.0-l
 */
// 2026/11/04：直接引用宿主能力标识，避免工具声明指向不存在的能力。
@Component
@TeaNekoAgentToolClass(value = MediaReplyFeature.ID, toolPackage = "agent.sticker", managedByFeature = true, requires = "sticker")
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.expression")
public final class StickerReplyTools {
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
