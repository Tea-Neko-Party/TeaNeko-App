package org.zexnocs.teanekoplugin.general.info;

import org.zexnocs.teanekocore.plugin.PluginOwned;

import org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolContext;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentTool;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentToolClass;
import org.zexnocs.teanekocore.command.api.DefaultValue;
import org.zexnocs.teanekocore.framework.description.Description;

/**
 * 用户留言板独立工具，沿用原资料插件归属和真实 DEBUG 权限。
 *
 * <br>2026/11/07：按业务插件分类声明归属，保留原业务标识与持久化格式。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.0-l
 * @version 4.9.0-l
 */
@TeaNekoAgentToolClass(value = "user-info", pluginId = InfoAgentPlugin.ID,
        toolPackage = "general.info", managedByFeature = true)
@PluginOwned("general.info")
public final class InfoUserMessagesTool {
    private final InfoAgentTools source;

    /**
     * 注入原资料读取和授权逻辑。
     * @param source 共享资料工具服务
     */
    public InfoUserMessagesTool(InfoAgentTools source) {
        this.source = source;
    }

    /**
     * 根据仍有效的真实消息来源复核完整留言权限。
     * @param context 固定宿主上下文
     * @return 当前拥有 DEBUG 权限时为真
     */
    public boolean canReadMessages(TeaNekoAgentToolContext context) {
        return source.canReadMessages(context);
    }

    /**
     * 有界分页读取留言，执行和返回前继续由共享业务服务复核权限。
     * @param uuid 目标完整 TeaNeko UUID
     * @param offset 本页起始偏移
     * @param limit 本页条数
     * @param context 原宿主上下文
     * @return 留言页或固定错误信封
     */
    @TeaNekoAgentTool(value = InfoAgentTools.MESSAGES, condition = "canReadMessages")
    @Description("分页读取该 UUID 收到的留言板留言、留言者 UUID 和留言时间；不是聊天记录，需要真实 DEBUG 权限。")
    public String userMessages(@Description("目标用户完整的 TeaNeko UUID，不是 QQ 号或消息 ID。") String uuid,
            @DefaultValue("0") @Description("起始偏移，默认 0，最大 10000。") int offset,
            @DefaultValue("5") @Description("本页条数，默认 5，范围 1 到 10。") int limit,
            TeaNekoAgentToolContext context) {
        return source.userMessages(uuid, offset, limit, context);
    }
}
