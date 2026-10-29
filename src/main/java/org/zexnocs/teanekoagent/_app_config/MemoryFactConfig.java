package org.zexnocs.teanekoagent._app_config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekoagent.memory.fact.IMemoryFactStore;
import org.zexnocs.teanekoagent.memory.fact.MemoryFacts;
import org.zexnocs.teanekoagent.memory.profile.UserProfileService;
import org.zexnocs.teanekoagent.memory.recall.IMemoryRecallDefaults;
import org.zexnocs.teanekoagent.memory.recall.IMemoryRecallService;
import org.zexnocs.teanekoagent.memory.recall.IMemoryRecallStore;
import org.zexnocs.teanekoagent.memory.recall.MemoryRecallService;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

import java.time.Clock;
import java.util.concurrent.ExecutorService;

/**
 * 装配事实追加、独立检索及证据画像，不注册管理工具、执行学习或在启动时访问数据库。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Configuration(proxyBeanMethods = false)
public class MemoryFactConfig {
    /**
     * 将事务端口与可信记录时钟组合，时间展示仍由上层配置时区决定。
     * @param store 事实事务端口
     * @return 可信宿主使用的显式追加服务
     */
    @Bean public MemoryFacts memoryFacts(IMemoryFactStore store) { return new MemoryFacts(store, Clock.systemUTC()); }

    /**
     * 组合独立 Recall 与实际紧凑 JSON 数组度量，不注册工具或注入 Prompt。
     * @param store 稳定候选端口
     * @param defaults 每次本地策略
     * @param mapper 项目 JSON 转换器
     * @param executor Core 业务执行器
     * @return 可信宿主显式调用的检索服务
     */
    @Bean public MemoryRecallService memoryRecallService(IMemoryRecallStore store, IMemoryRecallDefaults defaults, ObjectMapper mapper,
            @Qualifier("virtualExecutor") ExecutorService executor) {
        var writer = mapper.writer().without(SerializationFeature.INDENT_OUTPUT, SerializationFeature.WRAP_ROOT_VALUE);
        return new MemoryRecallService(store, defaults, Clock.systemUTC(), executor, evidence -> writer.writeValueAsString(evidence).length());
    }

    /**
     * 通过独立 Recall 组合画像读取，不创建画像缓存、事实或后台学习任务。
     * @param recall 事实检索端口
     * @param executor Core 业务执行器
     * @return 可信宿主显式使用的画像服务
     */
    @Bean public UserProfileService userProfileService(IMemoryRecallService recall, @Qualifier("virtualExecutor") ExecutorService executor) {
        return new UserProfileService(recall, executor);
    }
}
