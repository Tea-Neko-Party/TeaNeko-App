package org.zexnocs.teanekoagent.integration.web;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
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
 * <br>2026/11/05：新增绑定磁盘版本的管理员模型检测入口。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.5-l
 * @version 4.8.2-l
 */
@RestController
// 2026/11/05：主动接入统一安全错误映射，保持原认证与 HTTP 路径。
@WebManagedEndpoint
public final class ModelConfigurationController {
    private final LLMConfigurationDirectory directory;
    private final FileSettingsService files;
    private final WebSessionGuard sessions;
    private final ModelProbeService probes;

    /**
     * 2026/11/05：注入保存版本检测服务，保持原模板读取边界。
     * @param directory 模型与输入方式目录
     * @param files 原文件授权边界
     * @param sessions 原真实会话
     * @param probes 原协议及保存版本的检测边界
     */
    public ModelConfigurationController(LLMConfigurationDirectory directory, FileSettingsService files,
                                        WebSessionGuard sessions, ModelProbeService probes) {
        this.directory = directory;
        this.files = files;
        this.sessions = sessions;
        this.probes = probes;
    }

    /**
     * 2026/11/05：管理员显式发起一次实际模型检测，不自动应用磁盘配置。
     * @param id 配置路由
     * @param revision 页面固定文件版本
     * @param request 当前请求
     * @param authentication 真实管理员
     * @return 安全的检测结果
     */
    @PostMapping("/api/v1/model-configurations/{id}/probe")
    public CompletionStage<ModelProbeService.Result> probe(@PathVariable String id, @RequestParam String revision,
            HttpServletRequest request, Authentication authentication) {
        return probes.probe(id, revision, sessions.capture(request, authentication));
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
