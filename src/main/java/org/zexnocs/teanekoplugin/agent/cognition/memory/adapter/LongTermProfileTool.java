package org.zexnocs.teanekoplugin.agent.cognition.memory.adapter;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.LongMemories;
import org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolContext;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentTool;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentToolClass;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * 自动学习画像的独立工具入口，沿用长期记忆共享空间及主体边界。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.0-l
 * @version 4.9.0-l
 */
@TeaNekoAgentToolClass(value = "long-memory", toolPackage = "agent.memory", managedByFeature = true, requires = "memory")
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.cognition")
public final class LongTermProfileTool {
    private final LongTermMemoryTools source;

    /**
     * 注入原长期记忆访问逻辑。
     * @param source 共享长期工具服务
     */
    public LongTermProfileTool(LongTermMemoryTools source) {
        this.source = source;
    }

    /**
     * 按真实上下文复核读取资格。
     * @param context 固定宿主上下文
     * @return 允许查询时为真
     */
    public boolean canRead(TeaNekoAgentToolContext context) {
        return source.canRead(context);
    }

    /**
     * 读取长期事实投影，省略主体时使用真实发起者。
     * @param subjectId 可选主体
     * @param context 原宿主上下文
     * @return 有来源的画像结果
     */
    @TeaNekoAgentTool(value = "memory_user_profile", condition = "canRead")
    @Description("读取自动学习的长期用户画像；默认本人，也可明确查询共享空间其他 UUID。只含有证据的事实和偏好，不包含其他群原聊天或人格推测。")
    public CompletionStage<LongMemories.Result> profile(
            @Nullable @Description("可选真实 TeaNeko UUID，省略时使用当前用户。") UUID subjectId,
            TeaNekoAgentToolContext context) {
        return source.profile(subjectId, context);
    }
}
