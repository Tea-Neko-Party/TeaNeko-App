package org.zexnocs.teanekoplugin.agent.cognition.memory.configuration;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.zexnocs.teanekoplugin.agent.cognition.memory.access.IMemoryAccessPolicy;
import org.zexnocs.teanekoplugin.agent.cognition.memory.access.MemoryAccessService;
import org.zexnocs.teanekoplugin.agent.cognition.memory.fact.MemoryFacts;
import org.zexnocs.teanekoplugin.agent.cognition.memory.layered.ILongTermMemoryStore;
import org.zexnocs.teanekoplugin.agent.cognition.memory.profile.IUserProfileService;
import org.zexnocs.teanekoplugin.agent.cognition.memory.recall.IMemoryRecallService;
import org.zexnocs.teanekoplugin.agent.cognition.memory.runtime.IAgentMemoryReferences;

import java.util.concurrent.ExecutorService;

/**
 * 组合显式记忆访问边界，不自动注册模型工具、学习或管理权限。
 * <br>2026/11/03：明确保存默认进入同一长期领域，关闭新模块后保留原入口。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@Configuration(proxyBeanMethods = false)
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.cognition")
public class MemoryAccessConfig {
    /**
     * 2026/11/03：默认历史记录器在模型前复用此边界，不要求模型恰好调用新增工具。
     * @param access 可信记忆服务
     * @param policies 当前保存开关
     * @return 原文明确保存适配器
     */
    @Bean public org.zexnocs.teanekoplugin.agent.cognition.memory.access.ExplicitMemoryCapture explicitMemoryCapture(MemoryAccessService access, IMemoryAccessPolicy policies) {
        return new org.zexnocs.teanekoplugin.agent.cognition.memory.access.ExplicitMemoryCapture(access, policies);
    }
    /**
     * 借用已有引用、事实、检索及画像服务，构造没有配置或数据库访问。
     * @param policies 当前记忆开关端口
     * @param references 当前引用只读校验端口
     * @param recall 检索端口
     * @param profiles 画像端口
     * @param facts 事实追加服务
     * @param executor Core 业务执行器
     * @param stores 可选新长期端口
     * @param environment 当前本地模块开关
     * @return 宿主可显式使用的记忆访问服务
     */
    @Bean public MemoryAccessService memoryAccessService(IMemoryAccessPolicy policies, IAgentMemoryReferences references,
            IMemoryRecallService recall, IUserProfileService profiles, MemoryFacts facts, @Qualifier("virtualExecutor") ExecutorService executor,
            ObjectProvider<ILongTermMemoryStore> stores, Environment environment) {
        // 2026/11/03：实例只在实际保存时取得，不在配置扫描期启动依赖链。
        return new MemoryAccessService(policies, references, recall, profiles, facts, executor, command -> {
            var store = stores.getIfAvailable();
            if (store != null && environment.getProperty("tea-neko.agent.memory.layered.enabled", Boolean.class, true)
                    && environment.getProperty("tea-neko.agent.memory.long-term.enabled", Boolean.class, true)) {
                return store.appendExplicit(command, java.time.Instant.now());
            }
            return facts.append(command).thenApply(receipt -> new org.zexnocs.teanekoplugin.agent.cognition.memory.access.MemoryAddResult(
                    receipt.status(), receipt.record() == null ? null : receipt.record().id()));
        });
    }
}
