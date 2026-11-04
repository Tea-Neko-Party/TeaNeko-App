package org.zexnocs.teanekoapp.web;

import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;
import java.util.Set;

/**
 * 全站默认拒绝；浏览器仅开放明确 API，机器入口使用独立凭据，登录复用 Spring Security。
 * <br>2026/11/03：聊天目录与版本配置路由逐项授权，继续拒绝任意配置反射入口。
 * <br>2026/11/03：类型化管理路由继续要求真实会话与 CSRF，不开放原生身份伪造入口。
 * <br>2026/11/03：受限文件配置读写与回执纳入同一安全链。
 * <br>2026/11/03：实例级管理路由继续要求真实会话和防伪。
 * <br>2026/11/03：指令台读写与回执逐项授权，提交仍要求防伪和当前目标能力。
 * <br>2026/11/03：网页聊天、成员与认证媒体读写纳入同一安全链。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：机器令牌目录、显式查看与修改沿用原会话及防伪边界。
 * <br>2026/11/04：独立机器指标仅允许管理会话读取，不开放原 Actuator。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.1-l
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class WebSecurityConfig {
    /**
     * @param http 框架安全构建器
     * @param credentials 唯一本地认证提供方
     * @param mapper 安全 DTO 序列化
     * @return 所有请求经过的安全链
     */
    @Bean
    public SecurityFilterChain webSecurity(HttpSecurity http, WebCredentials credentials, ObjectMapper mapper) {
        http.authenticationProvider(credentials)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(SessionManagementConfigurer.SessionFixationConfigurer::changeSessionId))
                .csrf(csrf -> csrf.ignoringRequestMatchers("/ws/**"))
                .addFilterBefore(new WebAccessFilter(credentials, mapper), CsrfFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/console/", "/console/index.html", "/console/assets/**",
                                "/api/v1/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        // 2026/11/04：机器令牌使用独立管理员接口，不开放认证根文件下载。
                        .requestMatchers(HttpMethod.GET, "/api/v1/client-tokens", "/api/v1/client-tokens/operations/*")
                        .hasRole("WEB_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/client-tokens/*/reveal",
                                "/api/v1/client-tokens/*/actions/save", "/api/v1/client-tokens/actions/apply")
                        .hasRole("WEB_ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/session", "/api/v1/console/overview",
                                // 2026/11/04：机器观测通过原 HTTPS 与管理会话边界。
                                "/api/v1/console/machine",
                                "/api/v1/clients", "/api/v1/settings/sections", "/api/v1/clients/*/accounts",
                                "/api/v1/clients/*/accounts/*/targets",
                                // 2026/11/04：目录同样按真实客户端与聊天解析。
                                "/api/v1/clients/*/accounts/*/targets/*/settings",
                                "/api/v1/clients/*/accounts/*/targets/*/settings/*",
                                // 2026/11/03：仅开放注册配置区和原编号回执，不提供任意文件下载。
                                "/api/v1/file-configs", "/api/v1/file-configs/*", "/api/v1/file-configs/*/operations/*",
                                "/api/v1/clients/*/accounts/*/targets/*/settings/*/operations/*",
                                // 2026/11/03：不开放任意客户端协议入口。
                                "/api/v1/clients/*/accounts/*/targets/*/console",
                                "/api/v1/clients/*/accounts/*/targets/*/console/operations/*").hasRole("WEB_ADMIN")
                        // 2026/11/03：网页聊天与图片下载都必须经过真实会话，媒体没有匿名入口。
                        .requestMatchers(HttpMethod.GET, "/api/v1/chat/rooms", "/api/v1/chat/rooms/*/messages",
                                "/api/v1/chat/rooms/*/operations/*", "/api/v1/chat/rooms/*/media",
                                "/api/v1/chat/rooms/*/media/*").hasRole("WEB_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/chat/rooms", "/api/v1/chat/profiles",
                                "/api/v1/chat/rooms/*/members", "/api/v1/chat/rooms/*/messages",
                                "/api/v1/chat/rooms/*/media").hasRole("WEB_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/chat/rooms/*/media/*").hasRole("WEB_ADMIN")
                        // 2026/11/03：写路由仍经过原 CSRF、真实会话及事务内再次授权。
                        // 2026/11/03：实例级资源的进一步授权由原管理网关完成。
                        .requestMatchers(HttpMethod.POST, "/api/v1/management/*", "/api/v1/auth/logout", "/api/v1/clients/*/actions/discover",
                                "/api/v1/clients/*/accounts/*/targets/*/settings/*",
                                "/api/v1/clients/*/accounts/*/targets/*/console",
                                "/api/v1/file-configs/*/actions/validate", "/api/v1/file-configs/*/actions/save",
                                "/api/v1/file-configs/*/actions/apply",
                                "/api/v1/clients/*/accounts/*/targets/*/management/*").hasRole("WEB_ADMIN")
                        .requestMatchers("/ws/*").access((authentication, context) -> {
                            try {
                                String path = Objects.requireNonNull(context).getRequest().getServletPath();
                                var settings = credentials.settings();
                                boolean secure = !settings.enabled()
                                        || WebAccessFilter.secureTransport(context.getRequest(), settings);
                                return new AuthorizationDecision(secure && credentials.machine(path.substring("/ws/".length()),
                                        context.getRequest().getHeader("Authorization")));
                            } catch (RuntimeException failure) {
                                return new AuthorizationDecision(false);
                            }
                        })
                        .anyRequest().denyAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, failure) -> WebResponses.json(response, mapper,
                                401, new WebResponses.Error("LOGIN_REQUIRED", "请先登录。")))
                        .accessDeniedHandler((request, response, failure) -> WebResponses.json(response, mapper,
                                403, new WebResponses.Error("ACCESS_DENIED", "请求未通过权限或安全校验。"))))
                .formLogin(login -> login.loginPage("/console/").loginProcessingUrl("/api/v1/auth/login")
                        .passwordParameter("token")
                        .successHandler((request, response, authentication) -> {
                            request.getSession().setMaxInactiveInterval(credentials.settings().idleMinutes() * 60);
                            WebResponses.json(response, mapper, 200, new WebResponses.Error("LOGIN_SUCCEEDED", "登录成功。"));
                        })
                        .failureHandler((request, response, failure) -> WebResponses.json(response, mapper,
                                401, new WebResponses.Error("LOGIN_FAILED", "令牌无效或登录状态不可用。"))))
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                                + "img-src 'self' data: blob:; connect-src 'self'; object-src 'none'; "
                                + "base-uri 'self'; frame-ancestors 'none'; form-action 'self'")));
        return http.build();
    }

    /**
     * 将会话限制为 HTTPS Cookie，不从 URL 恢复会话标识。
     * @return 容器启动设置
     */
    @Bean
    public ServletContextInitializer webSessionCookie() {
        return context -> {
            context.setSessionTrackingModes(Set.of(jakarta.servlet.SessionTrackingMode.COOKIE));
            var cookie = context.getSessionCookieConfig();
            cookie.setName("__Host-TeaNekoSession");
            cookie.setHttpOnly(true);
            cookie.setSecure(true);
            cookie.setPath("/");
            cookie.setAttribute("SameSite", "Strict");
        };
    }
}
