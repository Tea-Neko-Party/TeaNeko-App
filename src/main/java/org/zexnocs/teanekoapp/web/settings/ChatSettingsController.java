package org.zexnocs.teanekoapp.web.settings;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.zexnocs.teanekoapp.web.WebSessionGuard;
import org.zexnocs.teanekoapp.web.client.ChatTargets;
import org.zexnocs.teanekoapp.web.client.ClientManagementDirectory;
import org.zexnocs.teanekoapp.web.client.IClientManagementAdapter;
import org.zexnocs.teanekocore.database.configdata.ConfigEdits;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * 按平台、账号和聊天引用路由，不接受浏览器提交真实 scope、身份或存储类型。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按真实目标查询适用配置目录，浏览器不提供内部命名空间。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.1-l
 */
@RestController
@RequestMapping("/api/v1")
public final class ChatSettingsController {
    private static final String TARGET = "/clients/{client}/accounts/{account}/targets/{target}/settings/{section}";
    private final ClientManagementDirectory clients;
    private final ChatSettingsService settings;
    private final WebSessionGuard sessions;

    /**
     * @param clients 真实平台目录
     * @param settings 配置应用用例
     * @param sessions 真实会话能力
     */
    public ChatSettingsController(ClientManagementDirectory clients, ChatSettingsService settings,
                                   WebSessionGuard sessions) {
        this.clients = clients;
        this.settings = settings;
        this.sessions = sessions;
    }

    /**
     * @param client 已注册平台
     * @param request 原请求
     * @param authentication 原认证
     * @return 已知真实账号或显式历史目录
     */
    @GetMapping("/clients/{client}/accounts")
    public CompletionStage<List<ChatTargets.Account>> accounts(@PathVariable String client,
            HttpServletRequest request, Authentication authentication) {
        var guard = sessions.capture(request, authentication);
        return clients.invoke(client, IClientManagementAdapter::accounts).thenApply(values -> {
            guard.run();
            return values;
        });
    }

    /**
     * @param client 原平台
     * @param request 原请求
     * @param authentication 原认证
     * @return 当前实际完成的目录刷新
     */
    @PostMapping("/clients/{client}/actions/discover")
    public CompletionStage<List<ChatTargets.Account>> discover(@PathVariable String client,
            HttpServletRequest request, Authentication authentication) {
        var guard = clients.guard(client, sessions.capture(request, authentication));
        return clients.invoke(client, adapter -> adapter.discover(guard)).thenApply(values -> {
            guard.run();
            return values;
        });
    }

    /**
     * @param client 原平台
     * @param account 原账号
     * @param after 原页尾
     * @param kind 类型过滤
     * @param request 原请求
     * @param authentication 原认证
     * @return 稳定有界聊天页
     */
    @GetMapping("/clients/{client}/accounts/{account}/targets")
    public CompletionStage<ChatTargets.Page> targets(@PathVariable String client, @PathVariable String account,
            @RequestParam(defaultValue = "") String after, @RequestParam(defaultValue = "") String kind,
            HttpServletRequest request, Authentication authentication) {
        var guard = sessions.capture(request, authentication);
        return clients.invoke(client, adapter -> adapter.targets(account, after, kind)).thenApply(value -> {
            guard.run();
            return value;
        });
    }

    /**
     * @param request 原请求
     * @param authentication 原认证
     * @return 当前明确允许的业务区
     */
    @GetMapping("/settings/sections")
    public List<ChatSettings.Section> sections(HttpServletRequest request, Authentication authentication) {
        return settings.sections(sessions.capture(request, authentication));
    }

    /**
     * @param client 已注册平台
     * @param account 原账号引用
     * @param target 原聊天引用
     * @param request 原请求
     * @param authentication 真实管理员
     * @return 此目标可登记和编辑的配置目录
     */
    @GetMapping("/clients/{client}/accounts/{account}/targets/{target}/settings")
    public CompletionStage<List<ChatSettings.Section>> targetSections(@PathVariable String client,
            @PathVariable String account, @PathVariable String target,
            HttpServletRequest request, Authentication authentication) {
        return settings.sections(client, account, target, sessions.capture(request, authentication));
    }

    /**
     * @param client 原平台
     * @param account 原账号
     * @param target 原聊天
     * @param section 原配置区
     * @param request 原请求
     * @param authentication 原认证
     * @return 当前实际配置与默认值来源
     */
    @GetMapping(TARGET)
    public CompletionStage<ChatSettings.View> read(@PathVariable String client, @PathVariable String account,
            @PathVariable String target, @PathVariable String section,
            HttpServletRequest request, Authentication authentication) {
        return settings.read(client, account, target, section, sessions.capture(request, authentication));
    }

    /**
     * @param client 原平台
     * @param account 原账号
     * @param target 原聊天
     * @param section 原配置区
     * @param change 明确版本写入
     * @param request 原请求
     * @param authentication 原认证
     * @return 实际提交回执，取消返回不证明未提交
     */
    @PostMapping(TARGET)
    public CompletionStage<ConfigEdits.Receipt> change(@PathVariable String client, @PathVariable String account,
            @PathVariable String target, @PathVariable String section, @RequestBody ConfigEdits.Change change,
            HttpServletRequest request, Authentication authentication) {
        return settings.change(client, account, target, section, change, sessions.capture(request, authentication));
    }

    /**
     * @param client 原平台
     * @param account 原账号
     * @param target 原聊天
     * @param section 原配置区
     * @param operation 原幂等标识
     * @param request 原请求
     * @param authentication 原认证
     * @return 与该真实配置匹配的原结果
     */
    @GetMapping(TARGET + "/operations/{operation}")
    public CompletionStage<ConfigEdits.Receipt> operation(@PathVariable String client, @PathVariable String account,
            @PathVariable String target, @PathVariable String section, @PathVariable UUID operation,
            HttpServletRequest request, Authentication authentication) {
        return settings.operation(client, account, target, section, operation, sessions.capture(request, authentication));
    }
}
