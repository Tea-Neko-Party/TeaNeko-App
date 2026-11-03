package org.zexnocs.teanekoagent._app_config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.zexnocs.teanekoagent.agent.memory.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.memory.access.IMemoryAccessPolicy;
import org.zexnocs.teanekoagent.memory.access.MemoryAccessService;
import org.zexnocs.teanekoagent.memory.fact.MemoryFacts;
import org.zexnocs.teanekoagent.memory.layered.ILongTermMemoryStore;
import org.zexnocs.teanekoagent.memory.profile.IUserProfileService;
import org.zexnocs.teanekoagent.memory.recall.IMemoryRecallService;

import java.util.concurrent.ExecutorService;

/**
 * 组合显式记忆访问边界，不自动注册模型工具、学习或管理权限。
 * <br>2026/11/03：明确保存默认进入同一长期领域，关闭新模块后保留原入口。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.6.1-l
 */
@Configuration(proxyBeanMethods = false)
public class MemoryAccessConfig {
    /**
     * 2026/11/03：默认历史记录器在模型前复用此边界，不要求模型恰好调用新增工具。
     * @param access 可信记忆服务
     * @param policies 当前保存开关
     * @return 原文明确保存适配器
     */
    @Bean public org.zexnocs.teanekoagent.memory.access.ExplicitMemoryCapture explicitMemoryCapture(MemoryAccessService access, IMemoryAccessPolicy policies) {
        return new org.zexnocs.teanekoagent.memory.access.ExplicitMemoryCapture(access, policies);
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
            return facts.append(command).thenApply(receipt -> new org.zexnocs.teanekoagent.memory.access.MemoryAddResult(
                    receipt.status(), receipt.record() == null ? null : receipt.record().id()));
        });
    }
}
