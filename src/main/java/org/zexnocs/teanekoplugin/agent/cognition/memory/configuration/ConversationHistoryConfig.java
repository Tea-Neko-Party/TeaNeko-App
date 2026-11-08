package org.zexnocs.teanekoplugin.agent.cognition.memory.configuration;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.zexnocs.teanekoagent.integration.config.AgentComponentAccess;
import org.zexnocs.teanekocore.actuator.task.EmptyTaskResult;
import org.zexnocs.teanekocore.actuator.timer.TimerDisplay;
import org.zexnocs.teanekocore.actuator.timer.interfaces.ITimerService;
import org.zexnocs.teanekocore.logger.ILogger;
import org.zexnocs.teanekocore.plugin.IPluginLifecycle;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekoplugin.agent.cognition.memory.history.ConversationHistoryService;
import org.zexnocs.teanekoplugin.agent.cognition.memory.history.IConversationHistory;
import org.zexnocs.teanekoplugin.agent.cognition.memory.history.IHistoryStore;
import org.zexnocs.teanekoplugin.agent.cognition.memory.history.adapter.AgentHistoryToolSource;
import org.zexnocs.teanekoplugin.agent.cognition.memory.runtime.IAgentMemoryReferences;
import org.zexnocs.teanekoplugin.agent.cognition.personality.IAgentPersonalitySource;
import org.zexnocs.teanekoplugin.support.PluginTaskScope;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。
// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。
// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。
// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

/**
 * 历史组合根，默认构造无 I/O；清理使用既有调度器并限制同时一个批次。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：将原历史清理任务纳入记忆分类，只读展示。
 * <br>2026/11/04：补全定时任务的详细中文介绍，原调度和控制意图保持不变。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/06：保留失败阶段及安全根因，避免配置或异步边界丢失诊断。
 * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
 *
 *
 * <br>2026/11/07：从有效组件装配读取本插件参数，不依赖宿主业务访问器。
 *
 * @author zExNocs
 * @date 2026/10/30
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@Configuration(proxyBeanMethods = false)
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.cognition")
public class ConversationHistoryConfig {
    private @Nullable PluginRegistry plugins;

    /**
     * 2026/11/07：在每次配置读取时复核认知插件状态，不修改持久化的用户意愿。
     * @param plugins 实际插件目录
     */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void installPlugins(PluginRegistry plugins) {
        this.plugins = plugins;
    }

    /** @return 当前插件与依赖是否仍可提供认知能力。 */
    private boolean enabled() {
        return plugins == null || plugins.isEnabled("agent.cognition");
    }

    /**
     * 将真实 cfg 和原授权端口组合为历史服务。
     * @param store 历史存储
     * @param references 固定引用复核
     * @param personalities 原人格复核
     * @param components 当前有效组件参数
     * @param executor 借用业务执行器
     * @param environment 懒读取本地保留期，非法值不在构造期间影响其他功能
     * @return 默认历史业务服务及轮次记录端口
     */
    @Bean @ConditionalOnMissingBean(IConversationHistory.class)
    public ConversationHistoryService conversationHistory(IHistoryStore store, IAgentMemoryReferences references, IAgentPersonalitySource personalities,
            AgentComponentAccess components, @Qualifier("virtualExecutor") ExecutorService executor,
            Environment environment) {
        return new ConversationHistoryService(store, references, personalities, scope -> components.settings(scope, "memory")
                .map(data -> new ConversationHistoryService.Policy(enabled(), data.path("historyCaptureEnabled").asBoolean(true), data.path("historyReadEnabled").asBoolean(true)))
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
    public AgentHistoryToolSource historyTools(IConversationHistory history, ObjectMapper mapper) {
        return new AgentHistoryToolSource(history, mapper);
    }
    /**
     * 注册有界过期清理，不读取或恢复普通会话。
     * @param store 历史存储端口
     * @param logger 失败诊断
     * @return 调度拥有者
     */
    @Bean public Cleanup historyCleanup(IHistoryStore store, ILogger logger) {
        return new Cleanup(store, logger);
    }
    /**
     * 复用 Spring 已有定时设施，每次清理一百行，失败只记固定原因。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     * <br>2026/11/04：声明历史清理分类，网页不控制原清理生命周期。
     * <br>2026/11/04：补充历史维护的执行、保留和只读说明。
     * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
     * <br>2026/11/07：认知业务与宿主生命周期解耦，保留原身份、事务及异步完成边界。
     *
     *
     * @author zExNocs
     * @date 2026/10/30
     * @since 4.5.0-l
     * @version 4.9.0-l
     */
    // 2026/11/04：元数据从实际 Spring 方法的所属类型读取。
    // 2026/11/04：补充任务用途、执行边界与网页控制说明。
    @TimerDisplay(category = "agent.memory", categoryName = "Agent 记忆", name = "会话历史清理",
            description = "按配置周期分批清理超过保留期的会话文字历史，上一批尚未完成时跳过重复执行。"
            + "只清理历史存储中已到期的数据，不生成摘要或调用模型，也不等同于删除长期记忆。"
            + "数据库失败会保留后续重试机会；该维护来源只读，历史是否采集仍由各聊天的采集开关决定。", controllable = false)
    @PluginOwned("agent.cognition")
    public static final class Cleanup implements IPluginLifecycle {
        private final IHistoryStore store;
        private final ILogger logger;
        private final AtomicBoolean running = new AtomicBoolean();
        // 2026/11/07：Spring 轮询只在插件启用期间准入，停用等待真实清理事务结束。
        private boolean closed = true;
        private PluginTaskScope lifecycle = new PluginTaskScope();
        private @org.jspecify.annotations.Nullable ITimerService timers;
        private long intervalMillis = 60000;

        /**
         * 2026/11/07：保留原清理间隔，启动插件时才安装实际计时器。
         * @param timers Core 定时器入口
         * @param intervalMillis 原清理间隔
         */
        @Autowired
        public void installTimer(ITimerService timers,
                @Value("${tea-neko.agent.history.cleanup-interval-ms:60000}") long intervalMillis) {
            if (intervalMillis <= 0) {
                throw new IllegalArgumentException("历史清理间隔必须为正数。");
            }
            this.timers = timers;
            this.intervalMillis = intervalMillis;
        }

        /**
         * 保存借用端口，不启动新线程。
         * @param store 原历史存储
         * @param logger 原日志
         */
        Cleanup(IHistoryStore store, ILogger logger) {
            this.store = store;
            this.logger = logger;
        }
        /** 2026/11/07：仅在本代启用期间触发数据库任务，停止等待本次事务结束。 */
        public synchronized void prune() {
            if (closed || !running.compareAndSet(false, true)) {
                return;
            }
            try {
                lifecycle.executeAsync(() -> store.prune(Instant.now())).whenComplete((count, failure) -> {
                    running.set(false);
                    if (failure != null) {
                        logger.warn("Agent 历史", "历史过期清理事务失败，将在后续批次重试。", failure);
                    }
                });
            } catch (RuntimeException failure) {
                running.set(false);
                logger.warn("Agent 历史", "历史过期清理无法启动。", failure);
            }
        }

        /** @return 2026/11/07：历史清理所属插件。 */
        @Override
        public String pluginId() {
            return "agent.cognition";
        }

        /** @return 稳定的后台任务标识。 */
        @Override
        public String id() {
            return "history-cleanup";
        }

        /** @return 2026/11/07：上一代清理已结束时重新开放轮询的完成阶段。 */
        @Override
        public synchronized CompletionStage<Void> start() {
            if (!closed) {
                return CompletableFuture.completedStage(null);
            }
            if (!lifecycle.stop().toCompletableFuture().isDone()) {
                return CompletableFuture.failedStage(new IllegalStateException("上一代历史清理仍在执行。"));
            }
            var scope = new PluginTaskScope();
            lifecycle = scope;
            closed = false;
            if (timers != null) {
                var timer = timers.registerByDelay("会话历史清理", "agent-history-cleanup", () -> scope.execute(() -> {
                    prune();
                    return EmptyTaskResult.INSTANCE;
                }), Duration.ofMillis(intervalMillis), Void.class);
                scope.own(timer);
            }
            return CompletableFuture.completedStage(null);
        }

        /** @return 2026/11/07：拒绝新批次并等待真实清理事务结束的阶段。 */
        @Override
        public synchronized CompletionStage<Void> stop() {
            closed = true;
            return lifecycle.stop();
        }
    }
}
