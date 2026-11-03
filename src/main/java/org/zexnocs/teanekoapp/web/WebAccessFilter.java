package org.zexnocs.teanekoapp.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.util.Set;

/**
 * 在登录和鉴权之前核验启用、HTTPS 来源及限流，在会话使用时复核代次和绝对期限。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
final class WebAccessFilter extends OncePerRequestFilter {
    private final WebCredentials credentials;
    private final ObjectMapper mapper;

    /**
     * @param credentials 本地认证状态
     * @param mapper 安全 JSON 响应
     */
    WebAccessFilter(WebCredentials credentials, ObjectMapper mapper) {
        this.credentials = credentials;
        this.mapper = mapper;
    }

    /**
     * @param request 当前请求
     * @param response 当前响应
     * @param chain 框架安全链
     * @throws ServletException 当下游处理失败时
     * @throws IOException 当响应写入失败时
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws ServletException, IOException {
        String path = request.getServletPath();
        if (!path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }
        WebSecurityFileConfig.Snapshot settings;
        try {
            settings = credentials.settings();
            if (!settings.enabled()) {
                reject(response, 503, "WEB_DISABLED", "控制台尚未启用。");
                return;
            }
            if (!secureOrigin(request, settings)) {
                reject(response, 403, "ORIGIN_REJECTED", "请从已配置的 HTTPS 控制台访问。");
                return;
            }
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof WebCredentials.Principal principal
                    && !credentials.valid(principal)) {
                var session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                SecurityContextHolder.clearContext();
                reject(response, 401, "SESSION_EXPIRED", "登录已失效，请重新登录。");
                return;
            }
        } catch (RuntimeException failure) {
            reject(response, 503, "WEB_UNAVAILABLE", "控制台安全配置不可用。");
            return;
        }
        if (path.equals("/api/v1/auth/csrf") && request.getSession(false) == null
                && !credentials.allowSession(request.getRemoteAddr())) {
            reject(response, 429, "SESSION_RATE_LIMITED", "访问过于频繁，请稍后再试。");
            return;
        }
        if (path.equals("/api/v1/auth/login") && request.getMethod().equals("POST")) {
            if (request.getContentLengthLong() < 0 || request.getContentLengthLong() > 4096
                    || request.getContentType() == null
                    || !request.getContentType().startsWith("application/x-www-form-urlencoded")) {
                reject(response, 400, "LOGIN_REQUEST_INVALID", "登录请求格式或长度无效。");
                return;
            }
            if (!credentials.allowAttempt(request.getRemoteAddr())) {
                response.setHeader("Retry-After", "60");
                reject(response, 429, "LOGIN_RATE_LIMITED", "尝试过于频繁，请稍后重试。");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * 仅在传输地址可信时解释单值代理头；不采信转发 IP，拒绝跨源写入。
     * @param request 实际传输请求
     * @param settings 已校验唯一来源
     * @return 是否来自预期的加密入口
     */
    private static boolean secureOrigin(HttpServletRequest request, WebSecurityFileConfig.Snapshot settings) {
        var origin = URI.create(settings.publicOrigin());
        boolean trusted = settings.trustedProxies().contains(request.getRemoteAddr());
        boolean secure = secureTransport(request, settings);
        String host = trusted && request.getHeader("X-Forwarded-Host") != null
                ? request.getHeader("X-Forwarded-Host") : request.getHeader("Host");
        if (!secure || host == null || !host.equalsIgnoreCase(origin.getRawAuthority())) {
            return false;
        }
        String supplied = request.getHeader("Origin");
        if (java.util.Collections.list(request.getHeaders("Origin")).size() > 1) {
            return false;
        }
        boolean mutation = !Set.of("GET", "HEAD", "OPTIONS").contains(request.getMethod());
        return supplied == null ? !mutation : supplied.equals(settings.publicOrigin());
    }

    /**
     * @param request 实际传输请求
     * @param settings 已校验可信代理
     * @return 是否已在当前可信链路加密
     */
    static boolean secureTransport(HttpServletRequest request, WebSecurityFileConfig.Snapshot settings) {
        return request.isSecure() || settings.trustedProxies().contains(request.getRemoteAddr())
                && "https".equals(request.getHeader("X-Forwarded-Proto"));
    }

    /**
     * @param response 当前响应
     * @param status 明确状态
     * @param code 安全码
     * @param message 固定说明
     * @throws IOException 当响应不可写时
     */
    private void reject(HttpServletResponse response, int status, String code, String message) throws IOException {
        WebResponses.json(response, mapper, status, new WebResponses.Error(code, message));
    }
}
