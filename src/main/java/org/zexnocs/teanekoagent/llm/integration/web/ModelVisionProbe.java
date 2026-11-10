package org.zexnocs.teanekoagent.llm.integration.web;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMImageDetail;
import org.zexnocs.teanekoagent.llm.framework.message.content.TextLLMContentPart;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.execution.ResourceBudget;
import org.zexnocs.teanekocore.logger.FailureDiagnostic;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;

/**
 * 使用随程序打包的低分辨率测试图验证实际图像输入与内容识别。
 * <br>2026/11/10：只发送合成图片，结果须匹配画面，不以非空文本冒充识图成功。
 *
 * @author zExNocs
 * @date 2026/11/10
 * @since 4.9.1-l
 * @version 4.9.1-l
 */
@Service
public final class ModelVisionProbe {
    private final ILLMModelService models;
    private final ScheduledExecutorService scheduler;
    private final Executor executor;
    private final Semaphore slots = new Semaphore(2);
    private final String image;

    /**
     * @param models 原模型服务
     * @param scheduler 借用截止调度器
     * @param executor 借用管理复核执行域，避免网络线程访问数据库
     * @throws IOException 当内置测试图缺失时
     */
    public ModelVisionProbe(ILLMModelService models,
            @Qualifier("taskExecutor") ScheduledExecutorService scheduler,
            @Qualifier("virtualExecutor") Executor executor) throws IOException {
        this.models = models;
        this.scheduler = scheduler;
        this.executor = executor;
        try (var input = new ClassPathResource("llm/vision-test.png").getInputStream()) {
            image = "data:image/png;base64," + Base64.getEncoder().encodeToString(input.readAllBytes());
        }
    }

    /**
     * 一次明确授权的管理检测，等待真实资源结束后释放控制及并发槽。
     * @param route 已授权模型路由，空字符串使用系统默认
     * @param guard 会话、插件和配置版本复核
     * @return 不含密钥或模型原文的检测结果
     */
    public CompletionStage<Result> probe(String route, Runnable guard) {
        guard.run();
        if (!slots.tryAcquire()) {
            return CompletableFuture.failedStage(new IllegalStateException("识图检测正在进行，请稍后重试。"));
        }
        final ExecutionControl control;
        try {
            control = ExecutionControl.withDeadline(new ResourceBudget(Map.of()),
                    Clock.systemUTC().instant().plusSeconds(60), Clock.systemUTC(), scheduler);
        } catch (RuntimeException failure) {
            slots.release();
            throw failure;
        }
        try {
            var options = LLMModelOptions.builder().provider(route.isEmpty() ? null : route).tools(List.of()).build();
            var resolved = models.resolveOptions(new LLMPrompt(List.of(), options));
            var model = models.getModel(resolved.getProvider());
            var capabilities = model.inputCapabilities(resolved);
            // 兼容型号未声明时，本次显式检测可试送图片；检测成功不擅自修改持久配置。
            boolean trial = !capabilities.supportsImages() && resolved.findImageInput().isEmpty();
            if (trial) {
                resolved.setImageInput(true);
                capabilities = model.inputCapabilities(resolved);
            }
            if (!capabilities.supportsImages()) {
                throw new IllegalArgumentException("所选协议未声明该型号的图片输入能力，请核对模型名称与协议适配器。");
            }
            var detail = capabilities.imageDetails().contains(LLMImageDetail.LOW) ? LLMImageDetail.LOW
                    : capabilities.imageDetails().stream().sorted().findFirst().orElseThrow();
            var content = new LLMContentListBuilder().addText(
                    "识别图片中从左到右两个图形的颜色和形状。只输出两项大写英文 COLOR_SHAPE，用英文逗号分隔，不解释。")
                    .addImage(image, detail).build();
            var prompt = new LLMPrompt(List.of(LLMUserMessage.builder().contents(content).build()), resolved,
                    org.zexnocs.teanekoagent.llm.framework.input.LLMOptionsPolicy.RESOLVED);
            guard.run();
            control.checkActive();
            var task = models.call(prompt, ExecutionPolicy.controlled(Duration.ofSeconds(60), control));
            var observed = control.guard(task.asStage()).handle((result, failure) -> failure == null
                    ? result(result, trial) : new Result("FAILED", FailureDiagnostic.from(failure).description()));
            return observed.thenCombineAsync(task.physicalCompletion().handle((ignored, failure) -> true),
                    (result, ignored) -> {
                        guard.run();
                        return result;
                    }, executor).whenComplete((result, failure) -> {
                        control.close();
                        slots.release();
                    });
        } catch (RuntimeException failure) {
            control.close();
            slots.release();
            guard.run();
            return CompletableFuture.completedStage(new Result("FAILED", FailureDiagnostic.from(failure).description()));
        }
    }

    /**
     * @param response 实际响应
     * @param trial 是否临时请求未知型号识图
     * @return 带配置提示的准确结果
     */
    private static Result result(ILLMResult response, boolean trial) {
        var result = inspect(response);
        return trial && result.status().equals("SUCCESS") ? new Result(result.status(), result.reason()
                + "此型号尚未声明图片能力；请将模型配置的图片输入设为允许并保存应用。") : result;
    }

    /**
     * @param response 原模型结果
     * @return 真实完成且识别内容正确才为成功
     */
    static Result inspect(ILLMResult response) {
        var choice = response.getFirstChoice().orElse(null);
        if (choice == null || choice.getMessage() == null || !"stop".equals(choice.getFinishReason())) {
            return new Result("FAILED", "模型未完整返回识图结果，可能拒绝、截断或协议不兼容。");
        }
        var text = new StringBuilder();
        choice.getMessage().getContents().forEach(content -> {
            if (content.getContentPart() instanceof TextLLMContentPart part) {
                text.append(part.getText());
            }
        });
        boolean matched = text.toString().strip().toUpperCase(Locale.ROOT).replaceAll("\\s", "")
                .equals("RED_SQUARE,BLUE_CIRCLE");
        return new Result(matched ? "SUCCESS" : "FAILED", matched
                ? "识图检测通过：正确识别内置测试图的颜色、形状和位置。"
                : "模型已返回，但未正确识别测试图；请核对图片能力或兼容服务实现。");
    }

    /**
     * 可公开的检测结果。
     * @param status SUCCESS 或 FAILED
     * @param reason 安全说明
     * @author zExNocs
     * @date 2026/11/10
     * @since 4.9.1-l
     * @version 4.9.1-l
     */
    public record Result(String status, String reason) {}
}
