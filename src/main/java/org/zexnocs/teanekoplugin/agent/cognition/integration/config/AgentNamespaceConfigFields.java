package org.zexnocs.teanekoplugin.agent.cognition.integration.config;

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.integration.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoapp.config.IConfigCommandFields;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekoplugin.agent.cognition.integration.command.AgentNamespaceCommands;
import org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.MemoryNamespaceBindings;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 将 /cfg 的 memoryNamespace 字段桥接到权威绑定，不写入作用域 DTO。
 * 注册或注销配置保留原绑定；重新启用时仍使用该引用，未注册读取不创建默认空间。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * @author zExNocs
 * @date 2026/11/01
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@Service
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.cognition")
public final class AgentNamespaceConfigFields implements IConfigCommandFields {
    private final IConfigDataService configs;
    private final AgentScopeConfigManager manager;
    private final MemoryNamespaceBindings bindings;
    private final AgentNamespaceCommands changes;

    /**
     * 保存配置及空间端口，避免引入默认 Agent/命令分发器的装配依赖。
     * @param configs 注册状态读取
     * @param manager Agent 管理器
     * @param bindings 权威空间引用
     * @param changes 真实身份切换边界
     */
    public AgentNamespaceConfigFields(IConfigDataService configs, AgentScopeConfigManager manager,
            MemoryNamespaceBindings bindings, AgentNamespaceCommands changes) {
        this.configs = configs; this.manager = manager; this.bindings = bindings; this.changes = changes;
    }
    /** @return 既有 Agent 配置名称 */
    @Override public String managerName() { return "agent"; }
    /** @return 唯一可桥接字段及其权限说明 */
    @Override public Map<String, String> fields() { return Map.of("memoryNamespace", "OWNER 绑定已有共享空间；权威值独立保存，注册/注销不删除空间或绑定。"); }
    /**
     * 读取当前绑定，不通过默认解析创建空间。
     * @param command 实际配置查询
     * @return 注册状态下的当前值或未初始化说明
     */
    @Override public CompletionStage<String> read(CommandData<ITeaNekoMessageData> command) {
        if (configs.getConfigData(manager, AgentScopeConfigData.class, command.getScopeId()).isEmpty()) {
            return CompletableFuture.completedStage("");
        }
        var scope = new AgentScope(command.getRawData().getClient().getClientId(), command.getScopeId());
        return bindings.readReference(scope).thenApply(reference -> "agent.memoryNamespace = "
                + reference.map(value -> value.namespaceId().value() + "；generation=" + value.generation()).orElse("尚未初始化（默认共享空间）"));
    }
    /**
     * 通过共用业务入口完成绑定及会话同步，不把拒绝反馈改写为设置成功。
     * @param command 含实际来源的配置命令
     * @param field 必须为 memoryNamespace
     * @param value 已有空间名称
     * @return 原业务反馈
     * @throws IllegalArgumentException 当字段未声明时
     */
    @Override public CompletionStage<String> set(CommandData<ITeaNekoMessageData> command, String field, String value) {
        if (!fields().containsKey(field)) { throw new IllegalArgumentException("该字段不属于 Agent 空间绑定。"); }
        return changes.change(command.getRawData(), value, command.getParentExecution());
    }
}
