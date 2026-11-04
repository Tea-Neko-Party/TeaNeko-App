package org.zexnocs.teanekoagent.integration.web;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zexnocs.teanekoagent.component.llm.file_config.form.LLMConfigurationDirectory;
import org.zexnocs.teanekoapp.web.WebSessionGuard;
import org.zexnocs.teanekoapp.web.error.WebManagedEndpoint;
import org.zexnocs.teanekoapp.web.files.FileSettingsService;
import java.util.concurrent.CompletionStage;

/**
 * 模型配置的公开样板入口，继续执行原文件配置区授权，不提供已配置秘密。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：通过端点声明接入统一安全错误映射。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.5-l
 * @version 4.8.0-l
 */
@RestController
// 2026/11/05：主动接入统一安全错误映射，保持原认证与 HTTP 路径。
@WebManagedEndpoint
public final class ModelConfigurationController {
    private final LLMConfigurationDirectory directory;
    private final FileSettingsService files;
    private final WebSessionGuard sessions;

    /**
     * @param directory 模型与输入方式目录
     * @param files 原文件授权边界
     * @param sessions 原真实会话
     */
    public ModelConfigurationController(LLMConfigurationDirectory directory, FileSettingsService files,
                                        WebSessionGuard sessions) {
        this.directory = directory;
        this.files = files;
        this.sessions = sessions;
    }

    /**
     * @param request 当前请求
     * @param authentication 真实管理员
     * @return 只有模型配置区允许访问时才能读取的公开样板
     */
    @GetMapping("/api/v1/model-configurations")
    public CompletionStage<LLMConfigurationDirectory.Catalog> templates(HttpServletRequest request,
                                                                      Authentication authentication) {
        var guard = sessions.capture(request, authentication);
        return files.read("agent.models", guard).thenApply(ignored -> {
            guard.run();
            var result = directory.describe();
            guard.run();
            return result;
        });
    }
}
