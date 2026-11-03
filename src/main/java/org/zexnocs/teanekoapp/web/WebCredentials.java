package org.zexnocs.teanekoapp.web;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import org.jspecify.annotations.Nullable;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.logger.ILogger;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/**
 * 根凭据验证与代次管理；普通同值重载保留随机值，变更及重启使旧会话失效。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：启动时输出控制台地址、登录令牌及片段式快捷链接。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@Service
public final class WebCredentials implements AuthenticationProvider {
    private final IFileConfigService files;
    private final Clock clock;
    private final Path directory;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Window> attempts = new LinkedHashMap<>();
    private @Nullable State state;

    /** @param files 当前成功加载的本地安全配置 */
    @Autowired
    public WebCredentials(IFileConfigService files) {
        this(files, Clock.systemUTC(), Path.of("data", "web"));
    }

    /**
     * @param files 当前配置
     * @param clock 可信时间源
     * @param directory 固定私有目录
     */
    WebCredentials(IFileConfigService files, Clock clock, Path directory) {
        this.files = files;
        this.clock = clock;
        this.directory = directory;
    }

    /**
     * 2026/11/04：准备凭据并按操作者要求输出启动登录信息，失败仍只记录固定诊断。
     * @param event 容器已完成初始加载
     */
    @EventListener
    public synchronized void ready(ApplicationReadyEvent event) {
        try {
            if (settings().enabled()) {
                var active = current();
                String token = active.settings().adminToken();
                if (token.isEmpty()) {
                    token = Files.readString(directory.resolve("runtime-admin-token"), StandardCharsets.UTF_8);
                }
                String address = active.settings().publicOrigin() + "/console/";
                var startup = LoggerFactory.getLogger(getClass().getName() + ".startup");
                startup.info("Web 控制台：{}", address);
                startup.info("Web 登录令牌：{}", token);
                startup.info("Web 快捷入口：{}#token={}", address, URLEncoder.encode(token, StandardCharsets.UTF_8));
            }
        } catch (RuntimeException | IOException failure) {
            event.getApplicationContext().getBean(ILogger.class).error(getClass().getName(),
                    "Web 安全配置或受限令牌文件不可用，管理入口保持关闭。");
        }
    }

    /**
     * @return 最近一次加载必须成功的安全快照
     * @throws IllegalStateException 当配置不可用时
     */
    WebSecurityFileConfig.Snapshot settings() {
        return files.requireCurrent(WebSecurityFileConfig.class).snapshot();
    }

    /**
     * @return 当前可用认证状态
     * @throws IllegalStateException 当控制面关闭或凭据准备失败时
     */
    private synchronized State current() {
        var settings = settings();
        if (!settings.enabled()) {
            state = null;
            throw new IllegalStateException("Web 控制台尚未启用。");
        }
        if (state == null || !state.settings().equals(settings)) {
            String secret = settings.adminToken();
            if (secret.isEmpty()) {
                byte[] bytes = new byte[32];
                random.nextBytes(bytes);
                secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                WebTokenFile.write(directory, secret);
            }
            state = new State(settings, UUID.randomUUID(), digest(secret));
        }
        return state;
    }

    /**
     * @param principal 原认证主体
     * @return 是否仍在当前认证代次及绝对期限内
     */
    boolean valid(Principal principal) {
        var current = current();
        return current.epoch().equals(principal.epoch())
                && principal.authenticatedAt().plusSeconds(current.settings().absoluteHours() * 3600L)
                .isAfter(clock.instant());
    }

    /**
     * 限制每分钟单地址五次、全局三十次，记录最多二千零四十八地址。
     * @param remote 实际传输地址，不采信客户端转发头
     * @return 本次是否允许尝试
     */
    synchronized boolean allowAttempt(String remote) {
        return allow("login", remote, 5, 30);
    }

    /**
     * 限制匿名防伪会话创建，避免公开令牌端点无界分配容器会话。
     * @param remote 实际传输地址
     * @return 是否允许创建新匿名会话
     */
    synchronized boolean allowSession(String remote) {
        return allow("session", remote, 20, 120);
    }

    /**
     * @param bucket 独立操作窗口
     * @param remote 真实地址
     * @param localLimit 单地址上限
     * @param globalLimit 全局上限
     * @return 是否已经计入本次额度
     */
    private boolean allow(String bucket, String remote, int localLimit, int globalLimit) {
        long minute = clock.instant().getEpochSecond() / 60;
        attempts.values().removeIf(window -> window.minute() != minute);
        String key = bucket + ":ip:" + remote;
        String globalKey = bucket + ":global";
        if (!attempts.containsKey(key) && attempts.size() >= 2048) {
            return false;
        }
        var global = attempts.getOrDefault(globalKey, new Window(minute, 0));
        var local = attempts.getOrDefault(key, new Window(minute, 0));
        if (global.count() >= globalLimit || local.count() >= localLimit) {
            return false;
        }
        attempts.put(globalKey, new Window(minute, global.count() + 1));
        attempts.put(key, new Window(minute, local.count() + 1));
        return true;
    }

    /**
     * @param client 路径中明确的机器客户端
     * @param authorization 独立机器凭据头
     * @return 当前配置是否允许该握手
     */
    boolean machine(String client, @Nullable String authorization) {
        var config = settings();
        if (!config.enabled()) {
            return true;
        }
        String secret = config.machineTokens().get(client);
        return secret != null && authorization != null && authorization.startsWith("Bearer ")
                && MessageDigest.isEqual(digest(secret), digest(authorization.substring(7)));
    }

    /**
     * @param authentication 框架解析的登录表单
     * @return 不保留根秘密的已认证主体
     * @throws BadCredentialsException 当凭据、状态或身份无效时
     */
    @Override
    public Authentication authenticate(Authentication authentication) {
        try {
            var current = current();
            Object credentials = authentication.getCredentials();
            if (!"admin".equals(authentication.getName()) || !(credentials instanceof String value)
                    || value.length() > 256 || !MessageDigest.isEqual(current.hash(), digest(value))) {
                throw new BadCredentialsException("登录凭据无效。");
            }
            return UsernamePasswordAuthenticationToken.authenticated(
                    new Principal(current.epoch(), clock.instant()), null,
                    List.of(new SimpleGrantedAuthority("ROLE_WEB_ADMIN")));
        } catch (IllegalStateException | IllegalArgumentException failure) {
            throw new BadCredentialsException("登录凭据或控制台状态无效。");
        }
    }

    /**
     * @param authentication 框架认证类型
     * @return 是否只接受本登录表单的令牌类型
     */
    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    /**
     * @param value 高熵秘密
     * @return 固定长度不可逆校验值
     */
    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("运行环境不支持凭据校验。");
        }
    }

    /**
     * 会话只保留代次和认证时间。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param epoch 认证代次
     * @param authenticatedAt 可信认证时间
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    @JsonIgnoreType
    public record Principal(UUID epoch, Instant authenticatedAt) {}

    /**
     * 不导出的运行凭据状态。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param settings 原配置
     * @param epoch 认证代次
     * @param hash 根令牌校验值
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    @JsonIgnoreType
    private record State(WebSecurityFileConfig.Snapshot settings, UUID epoch, byte[] hash) {}

    /**
     * 有界登录限流窗口。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param minute 分钟定位
     * @param count 已计次数
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    private record Window(long minute, int count) {}
}
