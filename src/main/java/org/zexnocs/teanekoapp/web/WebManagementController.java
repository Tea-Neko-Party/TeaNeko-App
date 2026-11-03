package org.zexnocs.teanekoapp.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.util.concurrent.CompletionStage;

/**
 * 只接受已知平台目标与已声明资源，读写统一经过会话、CSRF 和原管理授权网关。
 * <br>2026/11/03：实例级资源单独路由，禁止用聊天目标扩大授权范围。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@RestController
public final class WebManagementController {
    private final WebManagementCalls calls;
    private final WebSessionGuard sessions;

    /**
     * @param calls 显式授权桥接
     * @param sessions 真实会话复核
     */
    public WebManagementController(WebManagementCalls calls, WebSessionGuard sessions) {
        this.calls = calls;
        this.sessions = sessions;
    }

    /**
     * 2026/11/03：全局资源仍经真实会话、防伪及原管理授权。
     * @param resource 已声明全局资源
     * @param body 明确请求
     * @param request 当前请求
     * @param authentication 真实认证
     * @return 原管理阶段
     */
    @PostMapping("/api/v1/management/{resource}")
    public CompletionStage<?> global(@PathVariable String resource, @RequestBody JsonNode body,
            HttpServletRequest request, Authentication authentication) {
        return calls.invokeGlobal(resource, body, sessions.capture(request, authentication));
    }

    /**
     * @param client 平台引用
     * @param account 账号引用
     * @param target 聊天引用
     * @param resource 原管理资源键
     * @param body 明确请求 DTO 的 JSON
     * @param request 真实请求
     * @param authentication 真实服务端认证
     * @return 实际完成阶段，不公开 Bean 或反射方法
     */
    @PostMapping("/api/v1/clients/{client}/accounts/{account}/targets/{target}/management/{resource}")
    public CompletionStage<?> invoke(@PathVariable String client, @PathVariable String account,
            @PathVariable String target, @PathVariable String resource, @RequestBody JsonNode body,
            HttpServletRequest request, Authentication authentication) {
        return calls.invoke(client, account, target, resource, body, sessions.capture(request, authentication));
    }
}
