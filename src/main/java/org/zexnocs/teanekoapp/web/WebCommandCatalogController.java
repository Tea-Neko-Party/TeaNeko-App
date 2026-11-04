package org.zexnocs.teanekoapp.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.zexnocs.teanekoclient.web.WebCommandCatalog;
import org.zexnocs.teanekocore.command.CommandCatalog;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * 管理员指令说明与网页成员补全入口，两者均不触发命令执行。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.5-l
 * @version 4.7.5-l
 */
@RestController
public final class WebCommandCatalogController {
    private final CommandCatalog catalog;
    private final WebCommandCatalog web;
    private final WebSessionGuard sessions;
    private final Executor executor;

    /**
     * @param catalog 原命令说明服务
     * @param web 当前网页成员目录
     * @param sessions 原会话边界
     * @param executor 原业务域
     */
    public WebCommandCatalogController(CommandCatalog catalog, WebCommandCatalog web, WebSessionGuard sessions,
                                       @Qualifier("virtualExecutor") Executor executor) {
        this.catalog = catalog;
        this.web = web;
        this.sessions = sessions;
        this.executor = executor;
    }

    /**
     * @param request 当前请求
     * @param authentication 真实管理员
     * @return 所有实际启用的命令及其适用条件
     */
    @GetMapping("/api/v1/commands")
    public CompletionStage<List<CommandCatalog.Entry>> list(HttpServletRequest request, Authentication authentication) {
        var guard = sessions.capture(request, authentication);
        return CompletableFuture.supplyAsync(() -> catalog.list(null, guard), executor);
    }

    /**
     * @param room 已保存房间
     * @param body 原成员档案选择
     * @param request 当前请求
     * @param authentication 真实管理员会话，不成为成员权限
     * @return 当前成员可用的补全目录
     */
    @PostMapping("/api/v1/chat/rooms/{room}/commands")
    public CompletionStage<List<CommandCatalog.Entry>> room(@PathVariable String room, @RequestBody Query body,
            HttpServletRequest request, Authentication authentication) {
        return web.list(room, body.profile(), sessions.capture(request, authentication));
    }

    /**
     * 只接受已存在成员引用。
     * @param profile 网页档案引用
     * @author zExNocs
     * @date 2026/11/04
     * @since 4.7.5-l
     * @version 4.7.5-l
     */
    public record Query(String profile) {}
}
