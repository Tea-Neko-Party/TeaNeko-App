package org.zexnocs.teanekoagent._app_config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigData;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigManager;
import org.zexnocs.teanekoagent.adapter.history.AgentHistoryToolSource;
import org.zexnocs.teanekoagent.agent.memory.IAgentMemoryReferences;
import org.zexnocs.teanekoagent.agent.personality.IAgentPersonalitySource;
import org.zexnocs.teanekoagent.history.ConversationHistoryService;
import org.zexnocs.teanekoagent.history.IConversationHistory;
import org.zexnocs.teanekoagent.history.IHistoryStore;
import org.zexnocs.teanekocore.database.configdata.interfaces.IConfigDataService;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 历史组合根，默认构造无 I/O；清理使用既有调度器并限制同时一个批次。
 * @author zExNocs
 * @date 2026/10/30
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Configuration(proxyBeanMethods = false)
public class ConversationHistoryConfig {
    /**
     * 将真实 cfg 和原授权端口组合为历史服务。
     * @param store 历史存储
     * @param references 固定引用复核
     * @param personalities 原人格复核
     * @param configs 当前配置服务
     * @param manager Agent 登记声明
     * @param executor 借用业务执行器
     * @param environment 懒读取本地保留期，非法值不在构造期间影响其他功能
     * @return 默认历史业务服务及轮次记录端口
     */
    @Bean @ConditionalOnMissingBean(IConversationHistory.class)
    public ConversationHistoryService conversationHistory(IHistoryStore store, IAgentMemoryReferences references, IAgentPersonalitySource personalities,
            IConfigDataService configs, AgentScopeConfigManager manager, @Qualifier("virtualExecutor") ExecutorService executor,
            Environment environment) {
        return new ConversationHistoryService(store, references, personalities, scope -> configs.getConfigData(manager, AgentScopeConfigData.class, scope.scopeId())
                .map(data -> new ConversationHistoryService.Policy(data.toPolicy().enabled(), data.isHistoryCaptureEnabled(), data.isHistoryReadEnabled()))
                .orElseGet(() -> new ConversationHistoryService.Policy(false, false, false)), executor, Clock.systemUTC(),
                () -> Duration.ofDays(environment.getProperty("tea-neko.agent.history.retention-days", Integer.class, 7)));
    }
    /**
     * 提供可替换的显式历史工具源，不改变自定义主工具策略。
     * @param history 已授权业务入口
     * @param mapper JSON 转换器
     * @return 只读查询工具源
     */
    @Bean @ConditionalOnMissingBean(AgentHistoryToolSource.class)
    public AgentHistoryToolSource historyTools(IConversationHistory history, ObjectMapper mapper) { return new AgentHistoryToolSource(history, mapper); }
    /**
     * 注册有界过期清理，不读取或恢复普通会话。
     * @param store 历史存储端口
     * @param logger 失败诊断
     * @return 调度拥有者
     */
    @Bean public Cleanup historyCleanup(IHistoryStore store, ILogger logger) { return new Cleanup(store, logger); }
    /**
     * 复用 Spring 已有定时设施，每次清理一百行，失败只记固定原因。
     * @author zExNocs
     * @date 2026/10/30
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public static final class Cleanup {
        private final IHistoryStore store;
        private final ILogger logger;
        private final AtomicBoolean running = new AtomicBoolean();
        /**
         * 保存借用端口，不启动新线程。
         * @param store 原历史存储
         * @param logger 原日志
         */
        Cleanup(IHistoryStore store, ILogger logger) { this.store = store; this.logger = logger; }
        /** 在既有调度器触发独立数据库任务，上一批未结束时跳过。 */
        @Scheduled(fixedDelayString = "${tea-neko.agent.history.cleanup-interval-ms:60000}", initialDelayString = "${tea-neko.agent.history.cleanup-interval-ms:60000}")
        public void prune() {
            if (!running.compareAndSet(false, true)) { return; }
            try {
                store.prune(Instant.now()).whenComplete((count, failure) -> {
                    running.set(false); if (failure != null) { logger.warn("Agent 历史", "历史过期清理失败，将在后续批次重试。"); }
                });
            } catch (RuntimeException ignored) { running.set(false); logger.warn("Agent 历史", "历史过期清理无法启动。"); }
        }
    }
}
