package org.zexnocs.teanekoagent.component.memory.shared;

import org.springframework.beans.factory.ObjectProvider;
import org.zexnocs.teanekoagent.component.DeclaredAgentComponent;
import org.zexnocs.teanekoagent.component.TeaNekoAgentComponent;
import org.zexnocs.teanekoagent.runtime.prompt.IAgentPromptSource;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 选择性召回同一用户在共享空间其他聊天中的近期对话。
 * <br>2026/11/06：独立装配，群聊不会取得私聊原文。
 * <br>2026/11/06：目录构建与运行服务分离，避免空间策略反向请求尚未完成的目录。
 * @author zExNocs
 * @date 2026/11/06
 * @since 4.8.6-l
 * @version 4.8.6-l
 */
@TeaNekoAgentComponent(id = "shared-memory", order = 25, name = "跨聊天回忆",
        description = "在同一记忆空间关联同一用户的群聊经历，并保留来源。", requires = "memory")
@PluginOwned("teaneko.agent")
public final class SharedMemoryComponent extends DeclaredAgentComponent {
    private final ObjectProvider<SharedMemoryPromptSource> source;

    /**
     * 2026/11/06：声明阶段仅保存提供者，不解析有作用域策略依赖的服务。
     * @param source 有来源及实时权限复核的资料服务提供者
     */
    public SharedMemoryComponent(ObjectProvider<SharedMemoryPromptSource> source) {
        super("shared-memory", Map.of("enabled", true), List.of(
                new Field("enabled", "跨聊天回忆", "允许读取同空间中当前用户参与的群聊；私聊原文不向其他聊天公开。", "BOOLEAN")));
        this.source = source;
    }

    /**
     * 2026/11/06：执行提示构建或复核时取得已管理的单例，不重新扫描组件。
     * @return 独立参考来源
     */
    @Override
    public IAgentPromptSource prompts() {
        return source.getObject();
    }

    /** @return 不与普通近期记忆重名的来源 */
    @Override public Set<String> promptNames() { return Set.of(SharedMemoryPromptSource.NAME); }
}
