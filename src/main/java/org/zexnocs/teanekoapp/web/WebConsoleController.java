package org.zexnocs.teanekoapp.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zexnocs.teanekoapp.utils.VersionUtil;
import org.zexnocs.teanekoapp.web.client.ClientManagementDirectory;
import org.zexnocs.teanekoapp.web.client.IClientManagementAdapter;

import java.time.Instant;
import java.util.List;

/**
 * 有明确白名单的控制台入口，只返回安全视图，根令牌由框架登录过滤器消费。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@RestController
public final class WebConsoleController {
    private final WebCredentials credentials;
    private final ClientManagementDirectory clients;
    private final VersionUtil version;

    /**
     * @param credentials 会话策略
     * @param clients 明确注册的平台适配
     * @param version 原应用版本读取
     */
    public WebConsoleController(WebCredentials credentials, ClientManagementDirectory clients, VersionUtil version) {
        this.credentials = credentials;
        this.clients = clients;
        this.version = version;
    }

    /** @return 已打包的真实静态入口 */
    @GetMapping(value = "/console/", produces = "text/html")
    public Resource index() {
        return new ClassPathResource("static/console/index.html");
    }

    /**
     * @param token 框架本会话 CSRF 值
     * @return 浏览器本次请求使用的 CSRF，不是登录凭据
     */
    @GetMapping("/api/v1/auth/csrf")
    public CsrfView csrf(CsrfToken token) {
        return new CsrfView(token.getHeaderName(), token.getToken());
    }

    /**
     * @param authentication 已由框架认证的主体
     * @return 不含根令牌或会话标识的期限
     */
    @GetMapping("/api/v1/auth/session")
    public SessionView session(Authentication authentication) {
        var principal = (WebCredentials.Principal) authentication.getPrincipal();
        return new SessionView("本地管理员", principal.authenticatedAt(),
                principal.authenticatedAt().plusSeconds(credentials.settings().absoluteHours() * 3600L));
    }

    /**
     * @param request 当前可信会话
     * @param response 当前响应
     * @param authentication 已认证主体
     * @return 服务端撤销后的固定确认
     */
    @PostMapping("/api/v1/auth/logout")
    public WebResponses.Error logout(HttpServletRequest request, HttpServletResponse response,
                                     Authentication authentication) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        response.addHeader("Set-Cookie", ResponseCookie.from("__Host-TeaNekoSession", "")
                .httpOnly(true).secure(true).sameSite("Strict").path("/").maxAge(0).build().toString());
        return new WebResponses.Error("LOGGED_OUT", "已退出登录。");
    }

    /** @return 真实应用版本及当前批次的实际可用功能 */
    @GetMapping("/api/v1/console/overview")
    public Overview overview() {
        return new Overview("TeaNeko App", version.getVersion(), Instant.now(),
                List.of("会话管理", "平台连接"));
    }

    /** @return 当前可见适配器提供的真实平台状态 */
    @GetMapping("/api/v1/clients")
    public List<IClientManagementAdapter.View> clients() {
        return clients.list();
    }

    /**
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param headerName 写请求使用的头名
     * @param token 当前会话防伪值
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record CsrfView(String headerName, String token) {}

    /**
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param name 本地管理角色的中文名称
     * @param authenticatedAt 登录时间
     * @param expiresAt 绝对截止，空闲期限仍独立生效
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record SessionView(String name, Instant authenticatedAt, Instant expiresAt) {}

    /**
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param name 应用显示名
     * @param version 原权威版本
     * @param observedAt 当前观测时间
     * @param available 当前实际可用的页面能力
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    public record Overview(String name, String version, Instant observedAt, List<String> available) {}
}
