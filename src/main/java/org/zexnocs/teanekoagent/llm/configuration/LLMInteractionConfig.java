package org.zexnocs.teanekoagent.llm.configuration;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.zexnocs.teanekoagent.llm.framework.interaction.ILLMInteractionDefaults;
import org.zexnocs.teanekoagent.llm.framework.interaction.ILLMInteractionObserver;
import org.zexnocs.teanekoagent.llm.framework.interaction.ILLMInteractionService;
import org.zexnocs.teanekoagent.llm.framework.interaction.ILLMTokenEstimator;
import org.zexnocs.teanekoagent.llm.framework.interaction.JsonLLMTokenEstimator;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionService;

// 2026/11/05：模型专属装配统一归入 configuration，保留原 Bean 与文件契约。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelService;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 将纯交互服务接入已有 Core 执行资源，不建立额外线程池或 Agent 运行依赖。
 * <br>2026/11/02：安装安全失败日志，在异常转为业务状态前保留可诊断结构。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：模型交互装配归入组件的 configuration 目录。
 *
 * <br>2026/11/06：为交互链装配可配置的安全 INFO 观察器。
 * <br>2026/11/07：宿主观测实现为可选接入，独立 LLM 装配不要求 Agent 服务存在。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@Configuration(proxyBeanMethods = false)
public class LLMInteractionConfig {
    /**
     * 提供无网络的默认估算器，后续可按具体模型替换为本地 tokenizer。
     * @param mapper 应用已有 JSON 转换器
     * @return 输入用量估算端口
     */
    @Bean
    public ILLMTokenEstimator llmTokenEstimator(ObjectMapper mapper) { return new JsonLLMTokenEstimator(mapper); }

    /**
     * 复用异步业务池和计时池，交互服务只借用资源且不读取默认配置。
     * @param models 单次模型服务
     * @param defaults 懒加载默认设置
     * @param executor Core 现有虚拟线程池
     * @param scheduler Core 现有任务计时池
     * @param tokenEstimator 本地输入估算器
     * @return 可供插件或后续 Agent 使用的交互端口
     */
    public ILLMInteractionService llmInteractionService(ILLMModelService models, ILLMInteractionDefaults defaults,
            @Qualifier("virtualExecutor") ExecutorService executor,
            @Qualifier("taskExecutor") ScheduledExecutorService scheduler, ILLMTokenEstimator tokenEstimator) {
        // 2026/11/02：保留独立装配的原入口，Spring 默认使用带安全日志的组合。
        return new LLMInteractionService(models, defaults, executor, scheduler, tokenEstimator);
    }

    /**
     * 2026/11/02：在原借用资源之外安装安全诊断，不改变实际执行和预算语义。
     * @param models 单次模型服务
     * @param defaults 懒加载设置
     * @param executor 借用业务执行器
     * @param scheduler 借用计时器
     * @param tokenEstimator 本地估算器
     * @param logger 项目安全日志
     * @param observers 可选宿主终态观察端口
     * @return 宿主默认交互端口
     */
    @Bean(name = "llmInteractionService")
    public ILLMInteractionService llmInteractionServiceWithDiagnostics(ILLMModelService models, ILLMInteractionDefaults defaults,
            @Qualifier("virtualExecutor") ExecutorService executor,
            @Qualifier("taskExecutor") ScheduledExecutorService scheduler, ILLMTokenEstimator tokenEstimator,
            org.zexnocs.teanekocore.logger.ILogger logger, ObjectProvider<ILLMInteractionObserver> observers) {
        // 2026/11/02：只新增诊断协作，不改变原模型预算、重试和执行域。
        var service = new LLMInteractionService(models, defaults, executor, scheduler, tokenEstimator, logger);
        // 2026/11/07：独立模块可单独装配；宿主存在时仍接入原终态观察合同。
        observers.ifAvailable(service::installObserver);
        return service;
    }
}
