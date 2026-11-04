package org.zexnocs.teanekoagent.integration.web;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.component.llm.file_config.LLMModelFileConfig;
import org.zexnocs.teanekoagent.component.llm.file_config.ProbeLLMRouteSource;
import org.zexnocs.teanekoagent.component.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.component.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.component.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelId;
import org.zexnocs.teanekoagent.component.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.component.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekoapp.web.files.FileSettingsService;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.execution.ResourceBudget;
import org.zexnocs.teanekocore.logger.FailureDiagnostic;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;

/**
 * 对已保存文件快照执行一次无工具模型检测；不应用配置，不记录密钥或模型响应正文。
 *
 * @author zExNocs
 * @date 2026/11/05
 * @since 4.8.2-l
 * @version 4.8.2-l
 */
@Service
public final class ModelProbeService {
    private final FileSettingsService files;
    private final ILLMModelService models;
    private final ProbeLLMRouteSource routes;
    private final ScheduledExecutorService scheduler;
    private final Semaphore slots = new Semaphore(4);

    /**
     * @param files 文件授权与版本边界
     * @param models 原模型协议服务
     * @param routes 检测快照来源
     * @param scheduler 借用超时调度器
     */
    public ModelProbeService(FileSettingsService files, ILLMModelService models, ProbeLLMRouteSource routes,
            @Qualifier("taskExecutor") ScheduledExecutorService scheduler) {
        this.files = files;
        this.models = models;
        this.routes = routes;
        this.scheduler = scheduler;
    }

    /**
     * @param id 页面选中的配置路由
     * @param revision 固定磁盘版本
     * @param guard 当前管理员会话
     * @return 只含安全原因的检测结果，配置变化时拒绝旧结果
     */
    public CompletionStage<Result> probe(String id, String revision, Runnable guard) {
        guard.run();
        if (!slots.tryAcquire()) {
            return CompletableFuture.failedStage(new IllegalStateException("模型检测正在进行，请稍后重试。"));
        }
        CompletionStage<Result> work;
        try {
            work = files.inspect("agent.models", revision, guard, (document, snapshot) -> {
                var config = (LLMModelFileConfig) document.parse(snapshot.content());
                return config.findModelConfig(LLMModelId.of(id)).orElseThrow(
                        () -> new IllegalArgumentException("所选模型配置不存在。"));
            }).thenCompose(configuration -> {
                guard.run();
                var clock = Clock.systemUTC();
                var control = ExecutionControl.withDeadline(new ResourceBudget(Map.of()),
                        clock.instant().plusSeconds(60), clock, scheduler);
                String route = routes.register(configuration);
                CompletionStage<Result> request;
                try {
                    var prompt = new LLMPrompt(java.util.List.of(LLMUserMessage.builder()
                            .contents(new LLMContentListBuilder().addText("这是连接检测，请只回复 OK，不要调用工具。").build()).build()),
                            LLMModelOptions.builder().provider(route).build());
                    request = control.guard(models.call(prompt,
                                    ExecutionPolicy.controlled(Duration.ofSeconds(60), control)).asStage())
                            .thenApply(response -> inspectResponse(revision, response));
                } catch (RuntimeException failure) {
                    request = CompletableFuture.failedStage(failure);
                }
                return request.whenComplete((result, failure) -> {
                    routes.release(route);
                    control.close();
                });
            }).exceptionally(failure -> new Result(revision, "FAILED", FailureDiagnostic.from(failure).description()))
                    // 返回前重新检查原授权及磁盘版本，不把旧配置成功投射到新配置。
                    .thenCompose(result -> files.inspect("agent.models", revision, guard, (document, snapshot) -> result));
        } catch (RuntimeException failure) {
            slots.release();
            throw failure;
        }
        return work.whenComplete((result, failure) -> slots.release());
    }

    /**
     * 检测必须收到完整的文本回答，不把空消息、截断或拒绝视为成功。
     * @param revision 检测版本
     * @param response 协议适配器返回的响应
     * @return 不含模型正文的结果
     */
    private static Result inspectResponse(String revision,
            org.zexnocs.teanekoagent.component.llm.framework.response.interfaces.ILLMResult response) {
        var choice = response.getFirstChoice().orElse(null);
        if (choice == null || choice.getMessage() == null) {
            return new Result(revision, "FAILED", "模型未返回可识别的消息。");
        }
        String reason = choice.getFinishReason();
        if ("content_filter".equals(reason) || "refusal".equals(reason)) {
            return new Result(revision, "FAILED", "模型拒绝了检测请求。");
        }
        if (!"stop".equals(reason)) {
            return new Result(revision, "FAILED", "模型未完整结束回答，请检查输出额度和协议适配器。");
        }
        boolean hasText = choice.getMessage().getContents().stream().anyMatch(content ->
                content.getContentPart() instanceof org.zexnocs.teanekoagent.component.llm.framework.message.content.TextLLMContentPart text
                        && !text.getText().isBlank());
        return new Result(revision, hasText ? "SUCCESS" : "FAILED",
                hasText ? "模型连接与响应正常。" : "模型返回的文本为空。");
    }

    /**
     * 安全检测结果，不含秘密、响应正文或运行资源。
     * @param revision 被检测的磁盘版本
     * @param status SUCCESS 或 FAILED
     * @param reason 可公开的原因
     * @author zExNocs
     * @date 2026/11/05
     * @since 4.8.2-l
     * @version 4.8.2-l
     */
    public record Result(String revision, String status, String reason) {}
}
