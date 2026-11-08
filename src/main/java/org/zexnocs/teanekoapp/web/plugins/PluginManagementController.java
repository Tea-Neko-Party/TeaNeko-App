package org.zexnocs.teanekoapp.web.plugins;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zexnocs.teanekoapp.web.WebManagementCalls;
import org.zexnocs.teanekoapp.web.WebSessionGuard;
import org.zexnocs.teanekoapp.web.error.WebManagedEndpoint;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * 插件管理的固定 HTTP 适配，身份、白名单、CSRF 和执行复核沿用原管理网关。
 * <br>2026/11/07：读取与写入均不直接调用裸插件开关。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@RestController
@WebManagedEndpoint
@RequestMapping("/api/v1/plugins")
public final class PluginManagementController {
    private final WebManagementCalls calls;
    private final WebSessionGuard sessions;
    private final ObjectMapper mapper;

    /**
     * @param calls 原授权桥
     * @param sessions 真实会话
     * @param mapper 固定请求编码器
     */
    public PluginManagementController(WebManagementCalls calls, WebSessionGuard sessions, ObjectMapper mapper) {
        this.calls = calls;
        this.sessions = sessions;
        this.mapper = mapper;
    }

    /**
     * @param request 当前请求
     * @param authentication 认证身份
     * @return 完整插件目录
     */
    @GetMapping
    public CompletionStage<?> catalog(HttpServletRequest request, Authentication authentication) {
        return invoke("catalog", mapper.createObjectNode(), request, authentication);
    }

    /**
     * @param request 当前请求
     * @param authentication 认证身份
     * @return 可见的插件专页
     */
    @GetMapping("/pages")
    public CompletionStage<?> pages(HttpServletRequest request, Authentication authentication) {
        return invoke("pages", mapper.createObjectNode(), request, authentication);
    }

    /**
     * @param body 明确预览请求
     * @param request 当前请求
     * @param authentication 认证身份
     * @return 影响预览
     */
    @PostMapping("/preview")
    public CompletionStage<?> preview(@RequestBody JsonNode body, HttpServletRequest request,
            Authentication authentication) {
        return invoke("preview", body, request, authentication);
    }

    /**
     * @param body 原版本与操作编号
     * @param request 当前请求
     * @param authentication 认证身份
     * @return 真实启停回执
     */
    @PostMapping("/actions/change")
    public CompletionStage<?> change(@RequestBody JsonNode body, HttpServletRequest request,
            Authentication authentication) {
        return invoke("change", body, request, authentication);
    }

    /**
     * @param id 原操作编号
     * @param request 当前请求
     * @param authentication 认证身份
     * @return 当前主体原操作的回执
     */
    @GetMapping("/operations/{id}")
    public CompletionStage<?> operation(@PathVariable UUID id, HttpServletRequest request,
            Authentication authentication) {
        return invoke("operation", mapper.valueToTree(new PluginManagementResources.OperationQuery(id)),
                request, authentication);
    }

    /**
     * @param action 固定适配动作
     * @param body 明确请求
     * @param request 当前请求
     * @param authentication 真实认证
     * @return 原网关调用阶段
     */
    private CompletionStage<?> invoke(String action, JsonNode body, HttpServletRequest request,
            Authentication authentication) {
        return calls.invokeGlobal("teaneko.core:plugins." + action, body,
                sessions.capture(request, authentication));
    }
}
