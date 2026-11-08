package org.zexnocs.teanekoplugin.agent.cognition.memory.adapter;

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolContext;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentTool;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentToolClass;
import org.zexnocs.teanekoagent.component.tool.api.TeaNekoAgentToolParam;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.concurrent.CompletionStage;

/**
 * 旧事实画像的独立工具入口，复用共享记忆访问边界。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.0-l
 * @version 4.9.0-l
 */
@TeaNekoAgentToolClass(value = "memory-tools", toolPackage = "agent.memory", managedByFeature = true, requires = "memory")
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.cognition")
public final class MemoryProfileTool {
    private final AgentMemoryToolSource source;

    /**
     * 注入共享查询和预算逻辑。
     * @param source 可信记忆工具服务
     */
    public MemoryProfileTool(AgentMemoryToolSource source) {
        this.source = source;
    }

    /**
     * 在选择和执行时复核实际读取开关。
     * @param context 固定宿主上下文
     * @return 当前可以读取时为真
     */
    public boolean canRead(TeaNekoAgentToolContext context) {
        return source.canRead(context);
    }

    /**
     * 读取指定用户画像，不允许替换宿主空间或身份。
     * @param args 画像主体和预算
     * @param context 原宿主上下文
     * @return 有界画像结果
     */
    @TeaNekoAgentTool(value = AgentMemoryToolSource.PROFILE, condition = "canRead")
    @Description("读取指定用户的可追溯事实/偏好画像，结果可能因预算不完整；不推断人格或关系。")
    public CompletionStage<String> profile(
            @TeaNekoAgentToolParam(flatten = true) AgentMemoryToolSource.ProfileArgs args,
            TeaNekoAgentToolContext context) {
        return source.profile(args, context);
    }
}
