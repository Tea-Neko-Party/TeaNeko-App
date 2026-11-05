package org.zexnocs.teanekoapp.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.mock.env.MockEnvironment;
import org.zexnocs.teanekocore.file_config.parser.YAMLFileTypeParser;
import org.zexnocs.teanekocore.logger.ILogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * 验证默认生产配置、显式联合开发及真实 Node 进程树清理。
 *
 * 修改记录：<br>
 * 2026/11/04：覆盖默认不启动和上下文关闭时释放开发进程。
 * <br>2026/11/04：验证非默认实际端口传递到 Node 开发进程。
 * <br>2026/11/05：默认生产环境不装配开发进程，显式联合开发保持生效，并核对认证模板。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.0-l
 * @version 4.8.2-l
 */
@Timeout(15)
class WebFrontendDevelopmentTest {
    @TempDir
    Path directory;

    /**
     * 2026/11/05：读取项目默认生产 Profile，不装配开发服务，不要求 Node 或本地源码。
     *
     * @throws IOException 当默认配置资源不可读取时
     */
    @Test
    void staysDisabledInDefaultProductionProfile() throws IOException {
        try (var context = new AnnotationConfigApplicationContext()) {
            var defaults = new MockEnvironment();
            defaults.getPropertySources().addLast(new ResourcePropertySource(
                    "项目默认配置", new ClassPathResource("application.properties")));
            context.getEnvironment().setActiveProfiles(
                    defaults.getRequiredProperty("spring.profiles.active", String[].class));
            assertArrayEquals(new String[]{"prod"}, context.getEnvironment().getActiveProfiles());
            context.register(WebFrontendDevelopment.class);
            context.refresh();

            assertTrue(context.getBeansOfType(WebFrontendDevelopment.class).isEmpty());
        }
    }

    /**
     * 2026/11/05：默认模板启用本机控制台，并信任实际开发代理，避免首页可打开却无法登录。
     *
     * @throws IOException 当模板不可读取时
     */
    @Test
    void defaultTemplateSupportsLocalWebLogin() throws IOException {
        var parser = new YAMLFileTypeParser(new MockEnvironment());
        try (var stream = new ClassPathResource("templates/config/web/security.yml").getInputStream()) {
            var settings = parser.fromFileToData(stream, WebSecurityFileConfig.class).snapshot();
            assertTrue(settings.enabled());
            assertEquals("https://localhost:5173", settings.publicOrigin());
            assertEquals(java.util.List.of("127.0.0.1"), settings.trustedProxies());
        }
    }

    /**
     * 2026/11/04：验证非默认端口传递，并在正常关闭上下文时释放真实父子进程。
     * 2026/11/05：联合开发须显式选择 dev,web-dev，与默认生产启动隔离。
     *
     * @throws IOException 当创建或读取临时开发入口失败时
     */
    @Test
    void closesOwnedNodeProcessesWithTheApplicationContext() throws IOException {
        Path frontend = directory.resolve("frontend");
        Path entry = frontend.resolve("node_modules/vite/bin/vite.js");
        Files.createDirectories(entry.getParent());
        Files.writeString(entry, """
                const child = require('node:child_process').spawn(process.execPath,
                  ['-e', 'setInterval(() => {}, 1000)'], { stdio: 'ignore' });
                process.stdin.resume();
                process.stdin.on('end', () => { child.kill(); process.exit(1); });
                require('node:fs').writeFileSync('development-ports',
                  process.env.TEANEKO_BACKEND_PORT + ':' + process.env.TEANEKO_FRONTEND_PORT);
                require('node:fs').writeFileSync('development-pids', `${process.pid}
                ${child.pid}`);
                setInterval(() => {}, 1000);
                """);
        ProcessHandle parent;
        ProcessHandle child;
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("dev", "web-dev");
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("开发夹具", Map.of("user.dir", directory.toString(),
                            "local.server.port", 24691, "tea-neko.web.frontend.port", 25173)));
            context.registerBean(ILogger.class, () -> mock(ILogger.class));
            context.register(WebFrontendDevelopment.class);
            context.refresh();
            context.getBean(WebFrontendDevelopment.class).start();

            Path pids = frontend.resolve("development-pids");
            await().atMost(Duration.ofSeconds(5)).until(() -> Files.exists(pids));
            var values = Files.readAllLines(pids);
            parent = ProcessHandle.of(Long.parseLong(values.getFirst())).orElseThrow();
            child = ProcessHandle.of(Long.parseLong(values.get(1))).orElseThrow();
            assertTrue(parent.isAlive());
            assertTrue(child.isAlive());
            assertEquals("24691:25173", Files.readString(frontend.resolve("development-ports")));
        }

        await().atMost(Duration.ofSeconds(5)).until(() -> !parent.isAlive() && !child.isAlive());
    }
}
