package org.zexnocs.teanekoagent._app_config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekoagent.agent.memory.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.memory.access.IMemoryAccessPolicy;
import org.zexnocs.teanekoagent.memory.access.MemoryAccessService;
import org.zexnocs.teanekoagent.memory.fact.MemoryFacts;
import org.zexnocs.teanekoagent.memory.profile.IUserProfileService;
import org.zexnocs.teanekoagent.memory.recall.IMemoryRecallService;

import java.util.concurrent.ExecutorService;

/**
 * 组合显式记忆访问边界，不自动注册模型工具、学习或管理权限。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Configuration(proxyBeanMethods = false)
public class MemoryAccessConfig {
    /**
     * 借用已有引用、事实、检索及画像服务，构造没有配置或数据库访问。
     * @param policies 当前记忆开关端口
     * @param references 当前引用只读校验端口
     * @param recall 检索端口
     * @param profiles 画像端口
     * @param facts 事实追加服务
     * @param executor Core 业务执行器
     * @return 宿主可显式使用的记忆访问服务
     */
    @Bean public MemoryAccessService memoryAccessService(IMemoryAccessPolicy policies, IAgentMemoryReferences references,
            IMemoryRecallService recall, IUserProfileService profiles, MemoryFacts facts, @Qualifier("virtualExecutor") ExecutorService executor) {
        return new MemoryAccessService(policies, references, recall, profiles, facts, executor);
    }
}
