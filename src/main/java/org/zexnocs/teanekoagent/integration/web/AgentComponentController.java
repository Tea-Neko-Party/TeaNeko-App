package org.zexnocs.teanekoagent.integration.web;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zexnocs.teanekoagent.component.AgentComponentRegistry;
import org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolRegistry;
import org.zexnocs.teanekoagent.integration.config.AgentModelSelection;
import org.zexnocs.teanekoagent.llm.file_config.form.LLMConfigurationDirectory;
import org.zexnocs.teanekoapp.web.WebSessionGuard;
import org.zexnocs.teanekoapp.web.error.WebManagedEndpoint;
import org.zexnocs.teanekocore.plugin.PluginRegistry;

import java.util.Map;

/**
 * 管理员组件及工具目录，不接受 scope、凭据或任意类型，不产生模型调用。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：通过端点声明接入统一安全错误映射。
 *
 * <br>2026/11/05：简化冗余类型实参、变量或转换，保持原业务与测试边界。
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.8.0-l
 */
@RestController
// 2026/11/05：主动接入统一安全错误映射，保持原认证与 HTTP 路径。
@WebManagedEndpoint
@RequestMapping("/api/v1/agent/catalog")
public final class AgentComponentController {
    private final AgentComponentRegistry components;
    private final TeaNekoAgentToolRegistry tools;
    private final PluginRegistry plugins;
    private final WebSessionGuard sessions;
    private final LLMConfigurationDirectory models;
    private final AgentModelSelection selections;

    /**
     * @param components 组件目录
     * @param tools 工具目录
     * @param plugins 归属检查
     * @param sessions 当前会话
     * @param models 模型样板
     * @param selections 已应用系统路由
     */
    public AgentComponentController(AgentComponentRegistry components, TeaNekoAgentToolRegistry tools,
            PluginRegistry plugins, WebSessionGuard sessions, LLMConfigurationDirectory models, AgentModelSelection selections) {
        this.components = components;
        this.tools = tools;
        this.plugins = plugins;
        this.sessions = sessions;
        this.models = models;
        this.selections = selections;
    }

    /**
     * 2026/11/05：简化冗余类型实参、变量或转换，保持原业务与测试边界。
     * @param request 原请求
     * @param authentication 真实管理员
     * @return 无秘密的组件字段及模型模板
     */
    @GetMapping("/components")
    public Map<String, Object> components(HttpServletRequest request, Authentication authentication) {
        var guard = sessions.capture(request, authentication);
        guard.run();
        Map<String, String> systemRoutes;
        String modelError = "";
        try {
            systemRoutes = selections.choices();
        } catch (org.zexnocs.teanekoagent.llm.framework.model.LLMConfigurationException | IllegalArgumentException failure) {
            systemRoutes = Map.of("", "系统模型配置尚不可用");
            modelError = "请修复系统模型文件，或选择聊天私有模型。";
        }
        Map<String, Object> result = Map.of("components", components.describe(), "models", models.describe(),
                "systemRoutes", systemRoutes, "modelError", modelError);
        guard.run();
        return result;
    }

    /**
     * @param request 原请求
     * @param authentication 真实管理员
     * @return 当前已启用工具的实际参数目录
     */
    @GetMapping("/tools")
    public java.util.List<org.zexnocs.teanekoagent.component.tool.TeaNekoAgentToolMethods.Documentation> tools(
            HttpServletRequest request, Authentication authentication) {
        var guard = sessions.capture(request, authentication);
        guard.run();
        var result = tools.describe(plugins);
        guard.run();
        return result;
    }
}
