package org.zexnocs.teanekoapp.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 给异步管理事务捕获真实会话并重复复核，不依赖数据库线程上的浏览器 ThreadLocal。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@Component
public final class WebSessionGuard {
    private final WebCredentials credentials;

    /** @param credentials 当前认证代次 */
    public WebSessionGuard(WebCredentials credentials) {
        this.credentials = credentials;
    }

    /**
     * @param request 原可信 HTTP 请求
     * @param authentication 原认证结果
     * @return 在提交及返回前执行的授权复核
     */
    public Runnable capture(HttpServletRequest request, Authentication authentication) {
        HttpSession session = request.getSession(false);
        if (!(authentication.getPrincipal() instanceof WebCredentials.Principal principal)
                || session == null) {
            throw new SecurityException("当前管理会话不可用。");
        }
        Runnable guard = () -> {
            try {
                Object value = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
                if (!(value instanceof SecurityContext context) || context.getAuthentication() == null
                        || !context.getAuthentication().isAuthenticated()
                        || !principal.equals(context.getAuthentication().getPrincipal())
                        || context.getAuthentication().getAuthorities().stream()
                        .noneMatch(authority -> Objects.equals(authority.getAuthority(), "ROLE_WEB_ADMIN"))
                        || !credentials.valid(principal)
                        || session.getMaxInactiveInterval() > 0 && System.currentTimeMillis()
                        - session.getLastAccessedTime() > session.getMaxInactiveInterval() * 1000L) {
                    throw new SecurityException("管理会话已失效。");
                }
            } catch (IllegalStateException failure) {
                throw new SecurityException("管理会话已退出或不可用。");
            }
        };
        guard.run();
        return guard;
    }
}
