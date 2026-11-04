package org.zexnocs.teanekoapp.web;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekocore.logger.ILogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * 本地开发时随应用启动 Vite，并在正常关闭或热重启时释放本实例启动的进程。
 * 仅显式启用 web-dev Profile 时装配，工作目录须为项目根目录。
 *
 * 修改记录：<br>
 * 2026/11/04：提供前后端联合开发启动，直接运行 Node，避免 npm 命令包装进程滞留。
 * <br>2026/11/04：向 Vite 传递实际后端端口和统一配置的前端端口。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@Component
@Profile("web-dev")
public final class WebFrontendDevelopment implements DisposableBean {
    private final Path directory;
    private final ILogger logger;
    private final Environment environment;
    private final int port;
    private @Nullable Process frontend;

    /**
     * 固定本实例使用的前端目录和日志入口。
     * 2026/11/04：使用 Spring 环境读取实际监听端口，支持 Profile 和启动参数覆盖。
     *
     * @param workingDirectory 应用工作目录，应为项目根目录
     * @param logger 宿主日志
     * @param environment 当前生效的 Spring 环境
     * @param port 本机前端开发端口
     */
    public WebFrontendDevelopment(@Value("${user.dir}") String workingDirectory, ILogger logger,
                                  Environment environment, @Value("${tea-neko.web.frontend.port}") int port) {
        directory = Path.of(workingDirectory).resolve("frontend");
        this.logger = logger;
        this.environment = environment;
        this.port = port;
    }

    /**
     * 2026/11/04：后端就绪后启动本机 Vite，沿用原 HTTPS、端口及代理配置。
     *
     * @throws IOException 当无法执行 Node 时
     * @throws IllegalStateException 当工作目录或前端依赖未准备好时
     */
    @EventListener(ApplicationReadyEvent.class)
    public synchronized void start() throws IOException {
        if (frontend != null) {
            return;
        }

        Path entry = directory.resolve("node_modules/vite/bin/vite.js");
        if (!Files.isRegularFile(entry)) {
            throw new IllegalStateException("找不到前端依赖，请以项目根目录为工作目录，先执行 Gradle webInstall 任务。");
        }

        // 2026/11/04：隔离标准输入，避免与后端争用控制台；输出仍沿用后端终端。
        // 2026/11/04：就绪事件发生后读取真实端口，避免覆盖配置或随机后端端口时代理指向旧值。
        int backendPort = environment.getRequiredProperty("local.server.port", Integer.class);
        if (port < 1 || port > 65535 || port == backendPort) {
            throw new IllegalStateException("前端开发端口必须为 1 至 65535 的整数，且不能与后端端口相同。");
        }
        var launcher = new ProcessBuilder("node", entry.toAbsolutePath().toString(), "--host", "127.0.0.1")
                .directory(directory.toFile())
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT);
        launcher.environment().put("TEANEKO_BACKEND_PORT", Integer.toString(backendPort));
        launcher.environment().put("TEANEKO_FRONTEND_PORT", Integer.toString(port));
        Process process = launcher.start();
        frontend = process;
        process.onExit().thenAccept(this::exited);
        logger.info(getClass().getName(), "已启动前端开发进程，请查看 Vite 输出并访问 https://localhost:" + port + "/console/。");
    }

    /**
     * 报告开发服务意外结束，不接管其他已占用端口的服务，也不自动重试。
     *
     * @param process 已退出的本实例前端进程
     */
    private synchronized void exited(Process process) {
        if (frontend == process) {
            frontend = null;
            logger.error(getClass().getName(), "前端开发进程已退出，退出码为 " + process.exitValue()
                    + "，请检查上方 Vite 输出；后端继续运行。");
        }
    }

    /**
     * 2026/11/04：先解除退出回调，再终止本实例的前端及其子进程，不影响手动启动的服务。
     * 中断时保留线程中断状态，并完成强制清理。
     */
    @Override
    public synchronized void destroy() {
        Process process = frontend;
        frontend = null;
        if (process == null) {
            return;
        }

        var descendants = process.descendants().toList();
        descendants.forEach(ProcessHandle::destroy);
        process.destroy();
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException failure) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
        } finally {
            descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        }
    }
}
