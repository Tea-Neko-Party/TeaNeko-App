package org.zexnocs.teanekoapp.web.files;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.zexnocs.teanekoapp.web.WebSessionGuard;

import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * 管理员专用机器令牌入口，所有读写禁止缓存，显式查看同样要求防伪校验。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.1-l
 * @version 4.7.1-l
 */
@RestController
@RequestMapping("/api/v1/client-tokens")
public final class MachineTokenController {
    private final MachineTokenService service;
    private final WebSessionGuard sessions;

    /**
     * @param service 受限令牌用例
     * @param sessions 原管理员会话守卫
     */
    public MachineTokenController(MachineTokenService service, WebSessionGuard sessions) {
        this.service = service;
        this.sessions = sessions;
    }

    /**
     * @param response 当前响应，包含错误时也不得缓存秘密
     */
    @ModelAttribute
    public void noCache(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
    }

    /**
     * @param request 原请求
     * @param authentication 真实管理员
     * @return 无秘密目录与版本状态
     */
    @GetMapping
    public CompletionStage<MachineTokens.View> read(HttpServletRequest request, Authentication authentication) {
        return service.read(sessions.capture(request, authentication));
    }

    /**
     * @param client 已注册机器客户端
     * @param request 通过防伪校验的原请求
     * @param authentication 真实管理员
     * @return 只包含该客户端令牌的专用响应
     */
    @PostMapping("/{client}/reveal")
    public CompletionStage<MachineTokens.Secret> reveal(@PathVariable String client,
            HttpServletRequest request, Authentication authentication) {
        return service.reveal(client, sessions.capture(request, authentication));
    }

    /**
     * @param client 已注册机器入口
     * @param change 原版本及明确候选
     * @param request 原请求
     * @param authentication 真实管理员
     * @return 仅磁盘保存回执
     */
    @PostMapping("/{client}/actions/save")
    public CompletionStage<FileSettings.Receipt> save(@PathVariable String client, @RequestBody MachineTokens.Change change,
            HttpServletRequest request, Authentication authentication) {
        return service.save(client, change, sessions.capture(request, authentication));
    }

    /**
     * @param application 固定磁盘版本的应用请求
     * @param request 原请求
     * @param authentication 发布前仍有效的管理员
     * @return 原发布事实，客户端随后重新登录
     */
    @PostMapping("/actions/apply")
    public CompletionStage<FileSettings.Receipt> apply(@RequestBody FileSettings.Application application,
            HttpServletRequest request, Authentication authentication) {
        return service.apply(application, sessions.capture(request, authentication));
    }

    /**
     * @param operation 原操作编号
     * @param request 原请求
     * @param authentication 当前有效管理员
     * @return 原持久回执
     */
    @GetMapping("/operations/{operation}")
    public CompletionStage<FileSettings.Receipt> operation(@PathVariable UUID operation,
            HttpServletRequest request, Authentication authentication) {
        return service.operation(operation, sessions.capture(request, authentication));
    }
}
