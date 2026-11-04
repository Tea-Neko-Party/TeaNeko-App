package org.zexnocs.teanekoapp.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zexnocs.teanekocore.actuator.runtime.MachineMonitor;

/**
 * 管理会话下的独立机器观测入口，指标由 Core 提供，不借用任何聊天客户端身份。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.1-l
 * @version 4.7.1-l
 */
@RestController
public final class WebMachineController {
    private final MachineMonitor monitor;
    private final WebSessionGuard sessions;

    /**
     * @param monitor 原 JVM 指标来源
     * @param sessions 当前管理身份复核
     */
    public WebMachineController(MachineMonitor monitor, WebSessionGuard sessions) {
        this.monitor = monitor;
        this.sessions = sessions;
    }

    /**
     * @param request 当前请求
     * @param authentication 真实管理员身份
     * @return 采样前后复核同一管理会话的只读指标
     */
    @GetMapping("/api/v1/console/machine")
    public MachineMonitor.Snapshot machine(HttpServletRequest request, Authentication authentication) {
        var guard = sessions.capture(request, authentication);
        var snapshot = monitor.sample();
        guard.run();
        return snapshot;
    }
}
