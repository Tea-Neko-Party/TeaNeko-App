package org.zexnocs.teanekoapp.web.console;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.zexnocs.teanekoapp.web.WebSessionGuard;
import org.zexnocs.teanekoapp.web.error.WebManagedEndpoint;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * 指令台只接收平台、账号及服务端聊天引用，不接收用户、角色或任意平台 JSON。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/05：通过端点声明接入统一安全错误映射。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.8.0-l
 */
@RestController
// 2026/11/05：主动接入统一安全错误映射，保持原认证与 HTTP 路径。
@WebManagedEndpoint
@RequestMapping("/api/v1/clients/{client}/accounts/{account}/targets/{target}/console")
public final class WebConsoleCommandController {
    private final ClientConsoleService service;
    private final WebSessionGuard sessions;

    /**
     * @param service 原指令台编排
     * @param sessions 当前会话能力
     */
    public WebConsoleCommandController(ClientConsoleService service, WebSessionGuard sessions) {
        this.service = service;
        this.sessions = sessions;
    }

    /**
     * @param client 原平台
     * @param account 原账号
     * @param target 原聊天
     * @param request 当前请求
     * @param authentication 真实认证
     * @return 当前目录和真实就绪状态
     */
    @GetMapping
    public CompletionStage<ClientConsole.Catalog> catalog(@PathVariable String client, @PathVariable String account,
            @PathVariable String target, HttpServletRequest request, Authentication authentication) {
        return service.catalog(client, account, target, sessions.capture(request, authentication));
    }

    /**
     * @param client 原平台
     * @param account 原账号
     * @param target 原聊天
     * @param body 原操作与正文，不允许借请求指定权限
     * @param request 当前请求
     * @param authentication 真实认证
     * @return 持久受理事实，终态由原编号查询
     */
    @PostMapping
    public CompletionStage<ClientConsole.Receipt> submit(@PathVariable String client, @PathVariable String account,
            @PathVariable String target, @RequestBody ClientConsole.Request body,
            HttpServletRequest request, Authentication authentication) {
        return service.submit(client, account, target, body, sessions.capture(request, authentication));
    }

    /**
     * @param client 原平台
     * @param account 原账号
     * @param target 原聊天
     * @param operation 原编号
     * @param request 当前请求
     * @param authentication 真实认证
     * @return 原真实阶段与有界结果，不自动重放
     */
    @GetMapping("/operations/{operation}")
    public CompletionStage<ClientConsole.Receipt> operation(@PathVariable String client, @PathVariable String account,
            @PathVariable String target, @PathVariable UUID operation,
            HttpServletRequest request, Authentication authentication) {
        return service.operation(client, account, target, operation, sessions.capture(request, authentication));
    }
}
