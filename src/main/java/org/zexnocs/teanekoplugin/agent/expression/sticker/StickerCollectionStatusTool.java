package org.zexnocs.teanekoplugin.agent.expression.sticker;

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolContext;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentTool;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentToolClass;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.concurrent.CompletionStage;

/**
 * 表情包采集作业状态工具，和采集工具共享原来源及作业权限。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.0-l
 * @version 4.9.0-l
 */
@Component
@TeaNekoAgentToolClass(value = "sticker-collection", toolPackage = "agent.sticker", requires = "sticker")
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.expression")
public final class StickerCollectionStatusTool {
    private final StickerCollectionTools source;

    /**
     * 注入原来源检查和状态读取逻辑。
     * @param source 共享采集工具服务
     */
    public StickerCollectionStatusTool(StickerCollectionTools source) {
        this.source = source;
    }

    /**
     * 按真实原生消息来源复核当前采集策略。
     * @param context 固定宿主上下文
     * @return 当前来源允许使用时为真
     */
    public boolean available(TeaNekoAgentToolContext context) {
        return source.available(context);
    }

    /**
     * 查询当前用户在当前区域提交的作业，不导出源图片链接。
     * @param context 原宿主上下文
     * @param jobId 原采集回执中的作业标识
     * @return 受权限约束的作业状态
     */
    @TeaNekoAgentTool(value = "sticker_collection_status", condition = "available")
    @Description("读取本人在当前区域提交的表情包采集作业状态；SAVED 才表示入库，不返回源图片链接或其他群聊天资料。")
    public CompletionStage<Stickers.Receipt> status(TeaNekoAgentToolContext context,
            @Description("sticker_collect 返回的 jobId") String jobId) {
        return source.status(context, jobId);
    }
}
