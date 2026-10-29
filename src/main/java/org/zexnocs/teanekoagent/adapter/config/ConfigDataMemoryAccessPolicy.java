package org.zexnocs.teanekoagent.adapter.config;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.agent.AgentScope;
import org.zexnocs.teanekoagent.memory.access.IMemoryAccessPolicy;
import org.zexnocs.teanekoagent.memory.access.MemoryAccessPolicy;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;

import java.util.Objects;

/**
 * 从同一份真实 Agent 登记读取记忆开关，不为缺失记录构造默认启用配置。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service
public final class ConfigDataMemoryAccessPolicy implements IMemoryAccessPolicy {
    private final IConfigDataService configs;
    private final AgentScopeConfigManager manager;

    /**
     * 保存原配置服务及声明，构造不读取数据。
     * @param configs 原配置服务
     * @param manager Agent 配置声明
     * @throws NullPointerException 当依赖为空时
     */
    public ConfigDataMemoryAccessPolicy(IConfigDataService configs, AgentScopeConfigManager manager) {
        this.configs = Objects.requireNonNull(configs, "记忆配置服务不能为空。");
        this.manager = Objects.requireNonNull(manager, "Agent 配置声明不能为空。");
    }

    /**
     * 只读取当前真实登记，非法模式同样拒绝；注销不保留此前允许结果。
     * @param scope 可信来源作用域
     * @return 当前记忆策略，未登记时全部关闭
     * @throws IllegalArgumentException 当现有模式非法时
     */
    @Override public MemoryAccessPolicy read(AgentScope scope) {
        return configs.getConfigData(manager, AgentScopeConfigData.class, scope.scopeId()).map(data -> {
            var policy = data.toPolicy();
            return new MemoryAccessPolicy(policy.registered() && policy.enabled(), data.isMemoryReadEnabled(), data.isMemoryAddEnabled(), data.isMemoryLearningEnabled());
        }).orElseGet(() -> new MemoryAccessPolicy(false, false, false, false));
    }
}
