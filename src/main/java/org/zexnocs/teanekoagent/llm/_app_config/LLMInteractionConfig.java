package org.zexnocs.teanekoagent.llm._app_config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zexnocs.teanekoagent.llm.framework.interaction.*;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelService;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

/**
 * 将纯交互服务接入已有 Core 执行资源，不建立额外线程池或 Agent 运行依赖。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
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
    @Bean
    public ILLMInteractionService llmInteractionService(ILLMModelService models, ILLMInteractionDefaults defaults,
            @Qualifier("virtualExecutor") ExecutorService executor,
            @Qualifier("taskExecutor") ScheduledExecutorService scheduler, ILLMTokenEstimator tokenEstimator) {
        return new LLMInteractionService(models, defaults, executor, scheduler, tokenEstimator);
    }
}
