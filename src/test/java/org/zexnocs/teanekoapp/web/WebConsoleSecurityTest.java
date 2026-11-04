package org.zexnocs.teanekoapp.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.zexnocs.teanekoapp.client.GuardedClientWebSocketHandler;
import org.zexnocs.teanekoapp.utils.VersionUtil;
import org.zexnocs.teanekoapp.web.chat.WebChatController;
import org.zexnocs.teanekoapp.web.client.ClientManagementDirectory;
import org.zexnocs.teanekoapp.web.console.ClientConsole;
import org.zexnocs.teanekoapp.web.console.ClientConsoleService;
import org.zexnocs.teanekoapp.web.console.WebConsoleCommandController;
import org.zexnocs.teanekoapp.web.files.*;
import org.zexnocs.teanekoapp.web.settings.ChatSettingsController;
import org.zexnocs.teanekoapp.web.settings.ChatSettingsErrors;
import org.zexnocs.teanekoapp.web.settings.ChatSettingsService;
import org.zexnocs.teanekoclient.web.WebChat;
import org.zexnocs.teanekoclient.web.WebChatService;
import org.zexnocs.teanekoclient.web.WebChatStore;
import org.zexnocs.teanekoclient.web.WebImages;
import org.zexnocs.teanekocore.database.configdata.ConfigEdits;
import org.zexnocs.teanekocore.file_config.FileConfigDocument;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import tools.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

/**
 * 使用真实 Spring Security 过滤器验证登录、全入口拒绝、会话撤销与机器凭据边界。
 * <br>2026/11/03：新增类型化管理及文件配置路由的真实会话与防伪验证。
 * <br>2026/11/03：实例级运行入口也必须通过同一真实会话和防伪链。
 * <br>2026/11/03：默认日志采集不格式化正文，结构化 UUID 保持可关联。
 * <br>2026/11/03：指令台目录、提交和查询共用原会话与防伪，验证实际请求绑定。
 * <br>2026/11/03：网页消息、成员与受控图片共用原会话，读写与删除均不能匿名绕过。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：验证启动令牌提示及网页日志隔离。
 * <br>2026/11/04：验证机器令牌专用路由、空白策略与随机管理员令牌保持。
 * <br>2026/11/04：机器采样端点通过同一真实管理会话与来源限制。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.1-l
 */
class WebConsoleSecurityTest {
    /**
     * 新机器入口复用真实会话与防伪链，显式查看禁止缓存且绑定原候选秘密。
     * @throws Exception 当 MVC 请求执行失败时
     */
    @Test
    void machineTokenRoutesRequireSessionAndCsrf() throws Exception {
        try (var fixture = new Fixture(directory)) {
            fixture.mvc.perform(fixture.request("/api/v1/client-tokens", false)).andExpect(status().isUnauthorized());
            var session = fixture.authenticate();
            String reveal = "/api/v1/client-tokens/onebot/reveal";
            fixture.mvc.perform(fixture.request(reveal, true).session(session)).andExpect(status().isForbidden());
            verifyNoInteractions(fixture.machineTokens);
            String csrf = fixture.mapper.readTree(fixture.csrf(session).getResponse().getContentAsString()).get("token").asString();
            when(fixture.machineTokens.reveal(eq("onebot"), any())).thenReturn(
                    java.util.concurrent.CompletableFuture.completedStage(new MachineTokens.Secret("v1", "machine-only")));
            var shown = fixture.mvc.perform(fixture.request(reveal, true).session(session).header("X-CSRF-TOKEN", csrf))
                    .andExpect(request().asyncStarted()).andReturn();
            fixture.mvc.perform(asyncDispatch(shown)).andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.value").value("machine-only"));
            var id = UUID.randomUUID();
            when(fixture.machineTokens.save(eq("onebot"), any(), any())).thenAnswer(call -> {
                MachineTokens.Change change = call.getArgument(1);
                Runnable guard = call.getArgument(2);
                guard.run();
                assertEquals("short", change.value());
                return java.util.concurrent.CompletableFuture.completedStage(new FileSettings.Receipt(id,
                        "SAVED", "v2", new FileConfigDocument.Stamp("v1", 1, true),
                        "已保存，尚未应用", List.of("onebot"), Instant.now(), false));
            });
            String body = fixture.mapper.writeValueAsString(Map.of("operationId", id, "expectedRevision", "v1", "action", "SET", "value", "short"));
            var saved = fixture.mvc.perform(fixture.request("/api/v1/client-tokens/onebot/actions/save", true)
                    .session(session).header("X-CSRF-TOKEN", csrf).contentType("application/json").content(body))
                    .andExpect(request().asyncStarted()).andReturn();
            fixture.mvc.perform(asyncDispatch(saved)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SAVED"));
        }
    }

    /**
     * 机器策略换代撤销旧会话，仍可使用本次启动生成的管理员令牌重新登录。
     * @throws Exception 当随机令牌文件不可读时
     */
    @Test
    void blankMachinePolicyDoesNotRotateTheGeneratedAdminToken() throws Exception {
        try (var fixture = new Fixture(directory)) {
            fixture.file.setAdminToken("");
            assertThrows(BadCredentialsException.class, () -> fixture.credentials.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated("admin", "wrong")));
            String token = Files.readString(directory.resolve("runtime-admin-token"));
            var authentication = fixture.credentials.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("admin", token));
            fixture.file.setMachineTokens(Map.of("onebot", " \t "));
            assertFalse(fixture.credentials.valid((WebCredentials.Principal) authentication.getPrincipal()));
            assertTrue(fixture.credentials.machine("onebot", null));
            assertTrue(fixture.credentials.machine("onebot", "Bearer arbitrary"));
            assertFalse(fixture.credentials.machine("unknown", null));
            assertTrue(fixture.credentials.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("admin", token)).isAuthenticated());
            assertEquals(token, Files.readString(directory.resolve("runtime-admin-token")));
            fixture.file.setMachineTokens(Map.of("onebot", "a"));
            assertTrue(fixture.credentials.machine("onebot", "Bearer a"));
            assertFalse(fixture.credentials.machine("onebot", "Bearer b"));
        }
    }

    /** 2026/11/03：真实日志采集适配器默认不触发正文格式化，开启后认证字段仍隐藏。 */
    @Test
    void runtimeLogCaptureDoesNotFormatDefaultMessagesOrExposeCredentials() {
        var settings = new org.zexnocs.teanekocore.actuator.runtime.RuntimeSettings();
        var monitor = new org.zexnocs.teanekocore.actuator.runtime.RuntimeMonitor(Clock.systemUTC(), settings::snapshot);
        var capture = new WebRuntimeLogCapture(monitor);
        var backend = new ch.qos.logback.classic.LoggerContext();
        capture.setContext(backend);
        capture.start();
        try {
            var event = mock(ch.qos.logback.classic.spi.ILoggingEvent.class);
            String requestId = UUID.randomUUID().toString();
            when(event.getMDCPropertyMap()).thenReturn(Map.of("requestId", requestId));
            when(event.getLoggerName()).thenReturn("org.zexnocs.teanekocore.logger.DefaultLogger");
            when(event.getArgumentArray()).thenReturn(new Object[]{"测试来源", "秘密正文"});
            when(event.getLevel()).thenReturn(ch.qos.logback.classic.Level.INFO);
            capture.doAppend(event);
            verify(event, never()).getFormattedMessage();
            var query = new org.zexnocs.teanekocore.actuator.runtime.RuntimeMonitor.LogQuery("", 0, 10, "", "", "", requestId, "", "", null, null);
            var first = monitor.logs(query).items().getFirst();
            assertEquals(requestId, first.requestId());
            assertEquals("正文采集未开启", first.message());
            settings.setCaptureMessages(true);
            when(event.getFormattedMessage()).thenReturn("Authorization: Bearer test-private-secret");
            capture.doAppend(event);
            assertFalse(monitor.logs(query).items().getLast().message().contains("test-private-secret"));
            // 2026/11/04：即使开启正文采集，启动根凭据也不进入网页日志。
            int count = monitor.logs(query).items().size();
            when(event.getLoggerName()).thenReturn(WebCredentials.class.getName() + ".startup");
            when(event.getFormattedMessage()).thenReturn(TOKEN);
            capture.doAppend(event);
            assertEquals(count, monitor.logs(query).items().size());
        } finally {
            capture.detach();
            backend.stop();
        }
    }
    private static final String ORIGIN = "https://console.example";
    private static final String TOKEN = "test-credential-3bM8qV6nW1sR5tY9pX2zH4aF7cD0";
    @TempDir Path directory;

    /**
     * 2026/11/04：配置令牌和随机令牌均输出可用快捷链接，且只使用专用启动日志。
     *
     * @throws Exception 当装配或读取受限令牌文件失败时
     */
    @Test
    void announcesConfiguredAndGeneratedStartupTokens() throws Exception {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(
                WebCredentials.class.getName() + ".startup");
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try (var fixture = new Fixture(directory)) {
            var event = new org.springframework.boot.context.event.ApplicationReadyEvent(
                    new org.springframework.boot.SpringApplication(WebConsoleSecurityTest.class), new String[0],
                    fixture.context, Duration.ZERO);
            for (String configured : List.of(TOKEN, "")) {
                fixture.file.setAdminToken(configured);
                appender.list.clear();
                fixture.credentials.ready(event);
                String expected = configured.isEmpty()
                        ? Files.readString(directory.resolve("runtime-admin-token")) : configured;
                var messages = appender.list.stream().map(
                        ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage).toList();
                assertEquals(List.of("Web 控制台：" + ORIGIN + "/console/", "Web 登录令牌：" + expected,
                        "Web 快捷入口：" + ORIGIN + "/console/#token=" + URLEncoder.encode(expected, StandardCharsets.UTF_8)),
                        messages);
                assertNotNull(fixture.credentials.authenticate(
                        UsernamePasswordAuthenticationToken.unauthenticated("admin", expected)));
            }
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    /**
     * 只启动 MVC 与安全链，不启动机器人、数据库或外部服务。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    static class MvcConfiguration {}

    /**
     * 机器地址的局部传输替身，验证握手前的真实安全链。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    @RestController
    static class MachineEndpoint {
        /** @return 安全链通过后的固定响应 */
        @GetMapping("/ws/onebot")
        public String handshake() {
            return "独立机器凭据已通过";
        }
    }

    /**
     * 配置和时间可控，认证、会话与 CSRF 均为真实框架实现。
     *
     * <br>2026/11/04：校正 @since 为首次引入版本。
     * <br>2026/11/04：装配机器令牌控制器并保留真实身份与防伪过滤器。
     * <br>2026/11/04：注册纯 JDK 机器观测及其独立控制器，不启动业务服务。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.1-l
     */
    private static final class Fixture implements AutoCloseable {
        final WebSecurityFileConfig file = new WebSecurityFileConfig();
        final IFileConfigService files = mock(IFileConfigService.class);
        final AtomicReference<Instant> time = new AtomicReference<>(Instant.parse("2026-11-03T00:00:00Z"));
        final Clock clock = mock(Clock.class);
        final ObjectMapper mapper = new ObjectMapper();
        final AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        final WebCredentials credentials;
        final MockMvc mvc;
        final ChatSettingsService settings = mock(ChatSettingsService.class);
        // 2026/11/03：文件业务由临时文件用例覆盖，这里验证实际 HTTP 鉴权和参数绑定。
        final FileSettingsService fileSettings = mock(FileSettingsService.class);
        // 2026/11/04：机器文件事务由真实文件夹具覆盖，此处保留完整 MVC 安全链。
        final MachineTokenService machineTokens = mock(MachineTokenService.class);
        // 2026/11/03：HTTP 用例验证真实安全链，领域提交另由事务夹具验证。
        final WebManagementCalls management = mock(WebManagementCalls.class);
        // 2026/11/03：真实持久执行另由事务夹具覆盖，HTTP 保留实际认证链。
        final ClientConsoleService console = mock(ClientConsoleService.class);
        // 2026/11/03：网页领域由原事务夹具验证，这里只替换业务，保留完整安全链。
        final WebChatService chat = mock(WebChatService.class);
        final WebChatStore chatStore = mock(WebChatStore.class);
        final WebImages chatImages = mock(WebImages.class);

        /**
         * @param path 本测试拥有的私有目录
         * @throws Exception 当 MVC 安全链装配失败时
         */
        Fixture(Path path) throws Exception {
            file.setEnabled(true);
            file.setPublicOrigin(ORIGIN);
            file.setAdminToken(TOKEN);
            when(files.requireCurrent(WebSecurityFileConfig.class)).thenReturn(file);
            when(clock.instant()).thenAnswer(ignored -> time.get());
            credentials = new WebCredentials(files, clock, path);
            var clients = mock(ClientManagementDirectory.class);
            when(clients.list()).thenReturn(List.of());
            var version = mock(VersionUtil.class);
            when(version.getVersion()).thenReturn("4.7.0-l");
            var servlet = new MockServletContext();
            context.setServletContext(servlet);
            context.addBeanFactoryPostProcessor(factory -> {
                factory.registerSingleton("mapper", mapper);
                factory.registerSingleton("credentials", credentials);
                factory.registerSingleton("clients", clients);
                factory.registerSingleton("version", version);
                // 2026/11/04：真实 MVC 绑定指标 DTO，值由本机 JDK 只读接口提供。
                factory.registerSingleton("machineMonitor", new org.zexnocs.teanekocore.actuator.runtime.MachineMonitor());
                factory.registerSingleton("settings", settings);
                factory.registerSingleton("fileSettings", fileSettings);
                factory.registerSingleton("machineTokens", machineTokens);
                // 2026/11/03：将新路由纳入同一 MVC 容器，避免绕过框架鉴权。
                factory.registerSingleton("management", management);
                // 2026/11/03：指令台沿用同一入口，禁止旁路认证。
                factory.registerSingleton("console", console);
                // 2026/11/03：真实控制器绑定消息与媒体参数。
                factory.registerSingleton("chat", chat);
                factory.registerSingleton("chatStore", chatStore);
                factory.registerSingleton("chatImages", chatImages);
            });
            context.register(MvcConfiguration.class, WebSecurityConfig.class, WebConsoleController.class,
                    WebMachineController.class,
                    MachineEndpoint.class, ChatSettingsController.class, ChatSettingsErrors.class, WebSessionGuard.class,
                    WebManagementController.class, WebFileSettingsController.class, WebConsoleCommandController.class,
                    WebChatController.class, MachineTokenController.class);
            context.refresh();
            context.getBean(WebSecurityConfig.class).webSessionCookie().onStartup(servlet);
            assertTrue(servlet.getSessionCookieConfig().isSecure());
            assertTrue(servlet.getSessionCookieConfig().isHttpOnly());
            assertEquals("Strict", servlet.getSessionCookieConfig().getAttribute("SameSite"));
            mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        }

        /**
         * @param path 明确路径
         * @param post 是否为写请求
         * @return 带可信 HTTPS 来源的实际请求
         */
        MockHttpServletRequestBuilder request(String path, boolean post) {
            return (post ? post(path) : get(path)).servletPath(path).secure(true)
                    .header("Host", "console.example").header("Origin", ORIGIN);
        }

        /**
         * @param session 可选已有会话
         * @return 原始防伪端点响应
         * @throws Exception 当 MVC 执行失败时
         */
        MvcResult csrf(@org.jspecify.annotations.Nullable MockHttpSession session) throws Exception {
            var request = request("/api/v1/auth/csrf", false);
            if (session != null) {
                request.session(session);
            }
            return mvc.perform(request).andExpect(status().isOk()).andReturn();
        }

        /**
         * @param session 原匿名会话
         * @param token 根凭据
         * @param csrf 浏览器取得的真实防伪值
         * @return 与真实表单一致的登录请求
         */
        MockHttpServletRequestBuilder login(MockHttpSession session, String token, String csrf) {
            String body = "username=admin&token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
            return request("/api/v1/auth/login", true).session(session)
                    .contentType("application/x-www-form-urlencoded").content(body)
                    .param("username", "admin").param("token", token).header("X-CSRF-TOKEN", csrf)
                    .with(request -> {
                        // 真实浏览器会返回 Cookie，容器据此识别有效旧会话并轮换标识。
                        request.setRequestedSessionId(session.getId());
                        request.setRequestedSessionIdValid(true);
                        return request;
                    });
        }

        /**
         * @return 经过实际登录和会话标识轮换的会话
         * @throws Exception 当认证失败时
         */
        MockHttpSession authenticate() throws Exception {
            var csrf = csrf(null);
            var session = (MockHttpSession) Objects.requireNonNull(csrf.getRequest().getSession(false));
            String oldId = session.getId();
            String value = mapper.readTree(csrf.getResponse().getContentAsString()).get("token").asString();
            mvc.perform(login(session, file.getAdminToken(), value)).andExpect(status().isOk());
            assertNotEquals(oldId, session.getId());
            return session;
        }

        /** 仅释放本测试拥有的 MVC 容器。 */
        @Override
        public void close() {
            context.close();
        }
    }

    /**
     * 根凭据不替代 CSRF，认证之后也不能访问未声明端点，退出撤销服务端会话。
     * @throws Exception 当请求执行失败时
     */
    @Test
    void loginProtectsRoutesOriginCsrfAndLogout() throws Exception {
        try (var fixture = new Fixture(directory)) {
            assertFalse(fixture.mapper.writeValueAsString(fixture.file.snapshot()).contains(TOKEN));
            // 2026/11/04：系统指标也不能通过匿名路径读取。
            for (String path : List.of("/api/v1/auth/session", "/api/v1/console/overview", "/api/v1/console/machine", "/api/v1/clients",
                    "/api/v1/settings/sections", "/api/v1/clients/onebot/accounts", "/api/v1/file-configs", "/api/v1/file-configs/agent.models",
                    // 2026/11/04：目标适用目录同样要求真实登录。
                    "/api/v1/clients/onebot/accounts/a/targets/t/settings",
                    "/api/v1/clients/onebot/accounts/a/targets/t/settings/agent",
                    // 2026/11/03：指令台只读目录和原操作查询也必须登录。
                    "/api/v1/clients/onebot/accounts/a/targets/t/console",
                    "/api/v1/clients/onebot/accounts/a/targets/t/console/operations/00000000-0000-0000-0000-000000000001",
                    // 2026/11/03：图片二进制读取也必须先认证。
                    "/api/v1/chat/rooms", "/api/v1/chat/rooms/r/messages", "/api/v1/chat/rooms/r/media/i",
                    "/actuator/env", "/data/web/runtime-admin-token", "/config/web/security.yml")) {
                fixture.mvc.perform(fixture.request(path, false)).andExpect(status().isUnauthorized());
            }
            var csrf = fixture.csrf(null);
            var anonymous = (MockHttpSession) Objects.requireNonNull(csrf.getRequest().getSession(false));
            String value = fixture.mapper.readTree(csrf.getResponse().getContentAsString()).get("token").asString();
            fixture.mvc.perform(fixture.login(anonymous, TOKEN, "错误防伪值")).andExpect(status().isForbidden());
            fixture.mvc.perform(fixture.login(anonymous, TOKEN, value).header("Origin", "https://untrusted.example"))
                    .andExpect(status().isForbidden());
            fixture.mvc.perform(fixture.request("/api/v1/auth/session", false).secure(false)
                    .header("X-Forwarded-Proto", "https")).andExpect(status().isForbidden());
            var session = fixture.authenticate();
            var response = fixture.mvc.perform(fixture.request("/api/v1/console/overview", false).session(session))
                    .andExpect(status().isOk()).andReturn().getResponse();
            assertFalse(response.getContentAsString().contains(TOKEN));
            // 2026/11/04：验证真实安全链下的新端点和字段序列化。
            fixture.mvc.perform(fixture.request("/api/v1/console/machine", false).session(session))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.heapUsedBytes").isNumber());
            fixture.mvc.perform(fixture.request("/api/v1/not-registered", false).session(session))
                    .andExpect(status().isForbidden());
            var renewed = fixture.csrf(session);
            String current = fixture.mapper.readTree(renewed.getResponse().getContentAsString()).get("token").asString();
            fixture.mvc.perform(fixture.request("/api/v1/auth/logout", true).session(session)
                    .header("X-CSRF-TOKEN", current)).andExpect(status().isOk());
            assertTrue(session.isInvalid());
        }
    }

    /**
     * 配置提交必须携带真实防伪值，异步事务仍复核原会话，退出不能留下可用写能力。
     * @throws Exception 当真实 MVC 请求失败时
     */
    @Test
    void settingsRequireCsrfAndRecheckTheSessionInsideAsyncWork() throws Exception {
        try (var fixture = new Fixture(directory)) {
            var session = fixture.authenticate();
            String path = "/api/v1/clients/onebot/accounts/a/targets/t/settings/agent";
            // 2026/11/04：新目录端点通过实际 MVC 异步绑定和安全链。
            when(fixture.settings.sections(eq("onebot"), eq("a"), eq("t"), any()))
                    .thenReturn(java.util.concurrent.CompletableFuture.completedStage(List.of(
                            new org.zexnocs.teanekoapp.web.settings.ChatSettings.Section("agent", "Agent", "agent", "聊天助手"))));
            var directory = fixture.mvc.perform(fixture.request(
                    "/api/v1/clients/onebot/accounts/a/targets/t/settings", false).session(session))
                    .andExpect(request().asyncStarted()).andReturn();
            fixture.mvc.perform(asyncDispatch(directory)).andExpect(status().isOk());
            org.mockito.Mockito.clearInvocations(fixture.settings);
            var body = new ConfigEdits.Change(UUID.randomUUID(), "absent", ConfigEdits.Action.REGISTER, Map.of());
            String json = fixture.mapper.writeValueAsString(body);
            fixture.mvc.perform(fixture.request(path, true).session(session)
                    .contentType("application/json").content(json)).andExpect(status().isForbidden());
            verifyNoInteractions(fixture.settings);
            // 2026/11/03：通用类型化管理入口同样不能绕过原会话和防伪链。
            fixture.mvc.perform(fixture.request("/api/v1/clients/onebot/accounts/a/targets/t/management/teaneko.agent:memory", true)
                    .session(session).contentType("application/json").content("{\"action\":\"READ\"}"))
                    .andExpect(status().isForbidden());
            verifyNoInteractions(fixture.management);
            // 2026/11/03：全局只读也经 POST 与真实防伪，不能开放平行匿名入口。
            String globalPath = "/api/v1/management/teaneko.core:runtime";
            fixture.mvc.perform(fixture.request(globalPath, true).session(session)
                    .contentType("application/json").content("{\"action\":\"TIMERS\",\"limit\":10}"))
                    .andExpect(status().isForbidden());
            // 2026/11/03：保存文件也必须通过原防伪链，不能只依赖业务方法检查。
            String filePath = "/api/v1/file-configs/agent.main/actions/save";
            var fileChange = new FileSettings.Change(UUID.randomUUID(), "test-version", List.of(
                    new FileSettings.Edit("/enabled", "SET", fixture.mapper.valueToTree(true))));
            String fileJson = fixture.mapper.writeValueAsString(fileChange);
            fixture.mvc.perform(fixture.request(filePath, true).session(session)
                    .contentType("application/json").content(fileJson)).andExpect(status().isForbidden());
            verifyNoInteractions(fixture.fileSettings);
            // 2026/11/03：命令正文经过实际 JSON 绑定，根令牌不能替代 CSRF。
            String consolePath = "/api/v1/clients/onebot/accounts/a/targets/t/console";
            var consoleBody = new ClientConsole.Request(UUID.randomUUID(), "/console", "connection-1", Instant.now());
            String consoleJson = fixture.mapper.writeValueAsString(consoleBody);
            fixture.mvc.perform(fixture.request(consolePath, true).session(session)
                    .contentType("application/json").content(consoleJson)).andExpect(status().isForbidden());
            verifyNoInteractions(fixture.console);
            // 2026/11/03：聊天消息和图片上传均不能用已登录状态替代 CSRF。
            String chatPath = "/api/v1/chat/rooms/r/messages";
            var chatBody = new WebChat.Send(UUID.randomUUID(), "web-local-admin", "测试网页消息", true, "", "", Instant.now());
            String chatJson = fixture.mapper.writeValueAsString(chatBody);
            fixture.mvc.perform(fixture.request(chatPath, true).session(session).contentType("application/json")
                    .content(chatJson)).andExpect(status().isForbidden());
            fixture.mvc.perform(fixture.request("/api/v1/chat/rooms/r/media", true).param("operation", UUID.randomUUID().toString())
                    .session(session).contentType("application/octet-stream").content(new byte[]{1})).andExpect(status().isForbidden());
            verifyNoInteractions(fixture.chat, fixture.chatImages);
            var csrf = fixture.csrf(session);
            String value = fixture.mapper.readTree(csrf.getResponse().getContentAsString()).get("token").asString();
            var captured = new AtomicReference<Runnable>();
            // 2026/11/03：经框架绑定的原文只选择服务端档案，不携带执行角色。
            when(fixture.chat.submit(eq("r"), any(), any())).thenAnswer(call -> {
                WebChat.Send bound = call.getArgument(1);
                Runnable guard = call.getArgument(2);
                guard.run();
                assertEquals(chatBody, bound);
                return java.util.concurrent.CompletableFuture.completedStage(new WebChat.Receipt(bound.operationId(),
                        "web-msg:r:1", "PERSISTED", "消息已经保存，不代表回复完成。"));
            });
            var chatRequest = fixture.mvc.perform(fixture.request(chatPath, true).session(session).contentType("application/json")
                    .content(chatJson).header("X-CSRF-TOKEN", value)).andExpect(request().asyncStarted()).andReturn();
            fixture.mvc.perform(asyncDispatch(chatRequest)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PERSISTED"));
            // 2026/11/03：认证图片以二进制返回并禁止缓存；限长在解码服务之前执行。
            when(fixture.chatStore.image(eq("r"), eq("i"), any())).thenAnswer(call -> {
                Runnable guard = call.getArgument(2);
                guard.run();
                return java.util.concurrent.CompletableFuture.completedStage(new byte[]{1, 2, 3});
            });
            var imageRequest = fixture.mvc.perform(fixture.request("/api/v1/chat/rooms/r/media/i", false).session(session))
                    .andExpect(request().asyncStarted()).andReturn();
            fixture.mvc.perform(asyncDispatch(imageRequest)).andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(content().contentType("image/png")).andExpect(content().bytes(new byte[]{1, 2, 3}));
            fixture.mvc.perform(fixture.request("/api/v1/chat/rooms/r/media", true).session(session)
                    .param("operation", UUID.randomUUID().toString()).contentType("application/octet-stream")
                    .content(new byte[1_048_577]).header("X-CSRF-TOKEN", value)).andExpect(status().isBadRequest());
            verifyNoInteractions(fixture.chatImages);
            // 2026/11/03：只返回真实受理含义，并将原会话检查传入异步编排。
            when(fixture.console.submit(eq("onebot"), eq("a"), eq("t"), any(), any())).thenAnswer(call -> {
                ClientConsole.Request bound = call.getArgument(3);
                Runnable guard = call.getArgument(4);
                guard.run();
                assertEquals(consoleBody, bound);
                return java.util.concurrent.CompletableFuture.completedStage(new ClientConsole.Receipt(
                        bound.operationId(), "ACCEPTED", "", List.of(), List.of(), "已受理，尚未执行", Instant.now()));
            });
            var submitted = fixture.mvc.perform(fixture.request(consolePath, true).session(session)
                    .contentType("application/json").content(consoleJson).header("X-CSRF-TOKEN", value))
                    .andExpect(request().asyncStarted()).andReturn();
            fixture.mvc.perform(asyncDispatch(submitted)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("ACCEPTED"));
            when(fixture.management.invokeGlobal(eq("teaneko.core:runtime"), any(), any())).thenAnswer(call -> {
                Runnable guard = call.getArgument(2);
                guard.run();
                return java.util.concurrent.CompletableFuture.completedStage(Map.of("items", List.of()));
            });
            var globalRead = fixture.mvc.perform(fixture.request(globalPath, true).session(session)
                    .contentType("application/json").content("{\"action\":\"TIMERS\",\"limit\":10}")
                    .header("X-CSRF-TOKEN", value)).andExpect(request().asyncStarted()).andReturn();
            fixture.mvc.perform(asyncDispatch(globalRead)).andExpect(status().isOk());
            when(fixture.fileSettings.save(eq("agent.main"), any(), any())).thenAnswer(call -> {
                Runnable guard = call.getArgument(2);
                guard.run();
                FileSettings.Change bound = call.getArgument(1);
                assertEquals(fileChange.operationId(), bound.operationId());
                return java.util.concurrent.CompletableFuture.completedStage(new FileSettings.Receipt(
                        bound.operationId(), "SAVED", "saved-version", null, "文件已保存", List.of("/enabled"), Instant.now(), false));
            });
            var fileSave = fixture.mvc.perform(fixture.request(filePath, true).session(session)
                    .contentType("application/json").content(fileJson).header("X-CSRF-TOKEN", value))
                    .andExpect(request().asyncStarted()).andReturn();
            fixture.mvc.perform(asyncDispatch(fileSave)).andExpect(status().isOk());
            when(fixture.management.invoke(eq("onebot"), eq("a"), eq("t"), eq("teaneko.agent:memory"), any(), any()))
                    .thenAnswer(call -> {
                        Runnable guard = call.getArgument(5);
                        guard.run();
                        return java.util.concurrent.CompletableFuture.completedStage(Map.of("status", "已授权"));
                    });
            var management = fixture.mvc.perform(fixture.request(
                    "/api/v1/clients/onebot/accounts/a/targets/t/management/teaneko.agent:memory", true)
                    .session(session).contentType("application/json").content("{\"action\":\"READ\"}")
                    .header("X-CSRF-TOKEN", value)).andExpect(request().asyncStarted()).andReturn();
            fixture.mvc.perform(asyncDispatch(management)).andExpect(status().isOk());
            var completion = new java.util.concurrent.CompletableFuture<ConfigEdits.Receipt>();
            when(fixture.settings.change(eq("onebot"), eq("a"), eq("t"), eq("agent"), any(), any()))
                    .thenAnswer(call -> {
                        captured.set(call.getArgument(5));
                        return completion;
                    });
            var result = fixture.mvc.perform(fixture.request(path, true).session(session)
                    .contentType("application/json").content(json).header("X-CSRF-TOKEN", value))
                    .andExpect(request().asyncStarted()).andReturn();
            Objects.requireNonNull(captured.get()).run();
            session.invalidate();
            assertThrows(SecurityException.class, Objects.requireNonNull(captured.get())::run);
            completion.completeExceptionally(new SecurityException("测试会话失效。"));
            fixture.mvc.perform(asyncDispatch(result)).andExpect(status().isUnauthorized());
        }
    }

    /**
     * 根凭据轮换、绝对超时、关闭与坏配置均不能继续使用旧登录。
     * @throws Exception 当请求执行失败时
     */
    @Test
    void rejectsRotatedExpiredDisabledAndBrokenCredentials() throws Exception {
        try (var fixture = new Fixture(directory)) {
            var first = fixture.authenticate();
            fixture.file.setAdminToken(TOKEN + "new");
            fixture.mvc.perform(fixture.request("/api/v1/auth/session", false).session(first))
                    .andExpect(status().isUnauthorized());
            var second = fixture.authenticate();
            fixture.time.updateAndGet(now -> now.plus(Duration.ofHours(8)));
            fixture.mvc.perform(fixture.request("/api/v1/auth/session", false).session(second))
                    .andExpect(status().isUnauthorized());
            fixture.file.setEnabled(false);
            fixture.mvc.perform(fixture.request("/api/v1/auth/csrf", false)).andExpect(status().isServiceUnavailable());
            fixture.file.setEnabled(true);
            fixture.file.setAdminToken("不安全的短口令");
            fixture.mvc.perform(fixture.request("/api/v1/auth/csrf", false)).andExpect(status().isServiceUnavailable());
        }
    }

    /**
     * 随机根凭据只在本次实例有效，机器接入不能复用管理员登录且必须加密。
     * @throws Exception 当本机受限文件或请求失败时
     */
    @Test
    void generatesPrivateTokenAndSeparatesMachineAuthorization() throws Exception {
        try (var fixture = new Fixture(directory)) {
            fixture.file.setAdminToken("");
            assertThrows(BadCredentialsException.class, () -> fixture.credentials.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated("admin", "错误令牌")));
            Path file = directory.resolve("runtime-admin-token");
            String generated = Files.readString(file);
            assertEquals(43, generated.length());
            var authentication = fixture.credentials.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated("admin", generated));
            assertEquals(generated, Files.readString(file));
            var restarted = new WebCredentials(fixture.files, fixture.clock, directory);
            assertFalse(restarted.valid((WebCredentials.Principal) authentication.getPrincipal()));
            assertNotEquals(generated, Files.readString(file));
            assertThrows(BadCredentialsException.class, () -> restarted.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated("admin", generated)));
            String machine = TOKEN + "-machine";
            fixture.file.setMachineTokens(Map.of("onebot", machine));
            fixture.mvc.perform(fixture.request("/ws/onebot", false)).andExpect(status().isUnauthorized());
            fixture.mvc.perform(fixture.request("/ws/onebot", false).header("Authorization", "Bearer " + TOKEN))
                    .andExpect(status().isUnauthorized());
            fixture.mvc.perform(fixture.request("/ws/onebot", false).secure(false)
                    .header("Authorization", "Bearer " + machine)).andExpect(status().isUnauthorized());
            fixture.mvc.perform(fixture.request("/ws/onebot", false).header("Authorization", "Bearer " + machine))
                    .andExpect(status().isOk());
            var source = new MockHttpServletRequest("GET", "/ws/onebot");
            source.setSecure(true);
            source.addHeader("Authorization", "Bearer " + machine);
            var attributes = new HashMap<String, Object>();
            var guard = new WebSocketAccessGuard(fixture.credentials);
            var delegate = mock(WebSocketHandler.class);
            assertTrue(guard.beforeHandshake(new ServletServerHttpRequest(source),
                    mock(ServerHttpResponse.class), delegate, attributes));
            var socket = mock(WebSocketSession.class);
            when(socket.getId()).thenReturn("受控连接");
            when(socket.getAttributes()).thenReturn(attributes);
            var handler = new GuardedClientWebSocketHandler(delegate, List.of(guard));
            handler.afterConnectionEstablished(socket);
            var captured = org.mockito.ArgumentCaptor.forClass(WebSocketSession.class);
            verify(delegate).afterConnectionEstablished(captured.capture());
            captured.getValue().sendMessage(new TextMessage("首次允许"));
            fixture.file.setMachineTokens(Map.of("onebot", machine + "rotated"));
            assertThrows(java.io.IOException.class, () -> captured.getValue().sendMessage(new TextMessage("禁止迟到")));
            handler.handleMessage(socket, new TextMessage("禁止入站"));
            verify(delegate, never()).handleMessage(any(), any());
            handler.afterConnectionClosed(socket, CloseStatus.POLICY_VIOLATION);
            for (int i = 0; i < 5; i++) {
                assertTrue(fixture.credentials.allowAttempt("限流测试地址"));
            }
            assertFalse(fixture.credentials.allowAttempt("限流测试地址"));
        }
    }
}
