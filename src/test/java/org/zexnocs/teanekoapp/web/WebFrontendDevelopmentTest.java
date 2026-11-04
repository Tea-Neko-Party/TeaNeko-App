package org.zexnocs.teanekoapp.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.zexnocs.teanekocore.logger.ILogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 验证开发服务的显式启用边界及真实 Node 进程树清理。
 *
 * 修改记录：<br>
 * 2026/11/04：覆盖默认不启动和上下文关闭时释放开发进程。
 * <br>2026/11/04：验证非默认实际端口传递到 Node 开发进程。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@Timeout(15)
class WebFrontendDevelopmentTest {
    @TempDir
    Path directory;

    /** 默认启动不装配开发服务，不要求 Node 或本地源码。 */
    @Test
    void staysDisabledWithoutDevelopmentProfile() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(WebFrontendDevelopment.class);
            context.refresh();

            assertTrue(context.getBeansOfType(WebFrontendDevelopment.class).isEmpty());
        }
    }

    /**
     * 2026/11/04：验证非默认端口传递，并在正常关闭上下文时释放真实父子进程。
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
            context.getEnvironment().setActiveProfiles("web-dev");
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
