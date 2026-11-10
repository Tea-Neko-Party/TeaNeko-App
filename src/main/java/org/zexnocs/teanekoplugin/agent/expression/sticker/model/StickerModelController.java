package org.zexnocs.teanekoplugin.agent.expression.sticker.model;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zexnocs.teanekoagent.llm.file_config.ProbeLLMRouteSource;
import org.zexnocs.teanekoagent.llm.integration.web.ModelVisionProbe;
import org.zexnocs.teanekoagent.runtime.AgentScope;
import org.zexnocs.teanekoapp.web.WebSessionGuard;
import org.zexnocs.teanekoapp.web.client.ClientManagementDirectory;
import org.zexnocs.teanekoapp.web.error.WebManagedEndpoint;
import org.zexnocs.teanekoapp.web.files.FileSettingsService;
import org.zexnocs.teanekoapp.web.settings.ChatSettingsService;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekoplugin.agent.expression.sticker.file_config.AgentStickerFileConfig;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * 表情包组件及全局设置的显式识图检测，复用真实管理身份、配置版本和插件排空。
 * <br>2026/11/10：只检测已保存选择，不以备用成功掩盖被检测模型失败。
 *
 * @author zExNocs
 * @date 2026/11/10
 * @since 4.9.1-l
 * @version 4.9.1-l
 */
@RestController
@WebManagedEndpoint
@PluginOwned("agent.expression")
@RequestMapping("/api/v1/plugins/sticker-models/probe")
public final class StickerModelController {
    private final WebSessionGuard sessions;
    private final PluginRegistry plugins;
    private final FileSettingsService files;
    private final IFileConfigService configuration;
    private final ChatSettingsService chats;
    private final ClientManagementDirectory clients;
    private final StickerModelSelection selections;
    private final StickerModelConfiguration models;
    private final ProbeLLMRouteSource routes;
    private final ModelVisionProbe probe;
    private final Executor executor;

    /**
     * @param sessions 管理会话
     * @param plugins 插件代次
     * @param files 原文件版本边界
     * @param configuration 原已应用图库配置
     * @param chats 原聊天授权及版本边界
     * @param clients 真实聊天定位
     * @param selections 当前模型选择
     * @param models 私有配置解析
     * @param routes 检测专用临时凭据来源
     * @param probe 通用测试图检测
     * @param executor 借用数据库业务执行域
     */
    public StickerModelController(WebSessionGuard sessions, PluginRegistry plugins, FileSettingsService files,
            IFileConfigService configuration, ChatSettingsService chats, ClientManagementDirectory clients,
            StickerModelSelection selections, StickerModelConfiguration models, ProbeLLMRouteSource routes,
            ModelVisionProbe probe, @Qualifier("virtualExecutor") Executor executor) {
        this.sessions = sessions;
        this.plugins = plugins;
        this.files = files;
        this.configuration = configuration;
        this.chats = chats;
        this.clients = clients;
        this.selections = selections;
        this.models = models;
        this.routes = routes;
        this.probe = probe;
        this.executor = executor;
    }

    /**
     * @param input 固定已保存文件版本
     * @param request 当前 HTTP 请求
     * @param authentication 当前认证
     * @return 所选全局备用模型的真实识图结果
     */
    @PostMapping("/global")
    public CompletionStage<ModelVisionProbe.Result> global(@RequestBody Global input,
            HttpServletRequest request, Authentication authentication) {
        var checked = guard(sessions.capture(request, authentication));
        return plugins.invoke("agent.expression", () -> files.inspect("agent.sticker-model", input.revision(),
                checked, (document, snapshot) -> ((StickerModelFileConfig) document.parse(snapshot.content())).getFallback())
                .thenComposeAsync(selected -> {
                    models.validate(selected, "none");
                    boolean custom = selected.path("source").asString().equals("custom");
                    String route = custom ? routes.register(models.configuration(selected))
                            : StickerModelSelection.fallback(selected).orElseThrow(
                                    () -> new IllegalArgumentException("请先保存需要检测的全局备用模型。"));
                    CompletionStage<ModelVisionProbe.Result> result;
                    try {
                        result = probe.probe(route, checked);
                    } catch (RuntimeException failure) {
                        if (custom) {
                            routes.release(route);
                        }
                        throw failure;
                    }
                    return result.whenComplete((value, failure) -> {
                        if (custom) {
                            routes.release(route);
                        }
                    });
                }, executor).thenCompose(result -> files.inspect("agent.sticker-model", input.revision(), checked,
                        (document, snapshot) -> result)));
    }

    /**
     * @param input 当前已保存聊天配置的位置与版本
     * @param request 当前 HTTP 请求
     * @param authentication 当前认证
     * @return 首选模型检测结果，不触发备用请求
     */
    @PostMapping("/component")
    public CompletionStage<ModelVisionProbe.Result> component(@RequestBody Chat input,
            HttpServletRequest request, Authentication authentication) {
        var checked = guard(sessions.capture(request, authentication));
        var clientGuard = clients.guard(input.client(), checked);
        return plugins.invoke("agent.expression", () -> verify(input, clientGuard)
                .thenCompose(ignored -> clients.invoke(input.client(), adapter -> adapter.resolve(input.account(), input.target())))
                .thenComposeAsync(target -> {
                    var scope = new AgentScope(target.clientId(), target.scope());
                    var settings = configuration.requireCurrent(AgentStickerFileConfig.class).snapshot();
                    var plan = selections.plan(scope, settings.modelId());
                    Runnable current = () -> {
                        clientGuard.run();
                        if (!settings.equals(configuration.requireCurrent(AgentStickerFileConfig.class).snapshot())
                                || !plan.equals(selections.plan(scope, settings.modelId()))) {
                            throw new ConcurrentModificationException("模型设置已经变化，请刷新后重新检测。");
                        }
                    };
                    return verify(input, current).thenComposeAsync(ignored -> probe.probe(plan.primary(), current), executor);
                }, executor).thenCompose(result -> verify(input, clientGuard).thenApply(ignored -> result)));
    }

    /**
     * @param input 已保存聊天目标
     * @param checked 当前权限
     * @return 版本仍有效的完成阶段
     */
    private CompletionStage<Void> verify(Chat input, Runnable checked) {
        return chats.read(input.client(), input.account(), input.target(), "agent", checked).thenAccept(view -> {
            if (!view.registered() || !view.revision().equals(input.revision())) {
                throw new ConcurrentModificationException("聊天配置已经变化，请保存并刷新后检测。");
            }
        });
    }

    /**
     * @param session 原管理会话
     * @return 固定当前插件代次的复核
     */
    private Runnable guard(Runnable session) {
        var token = plugins.capture("agent.expression");
        return () -> {
            session.run();
            plugins.check(token);
        };
    }

    /**
     * 全局检测只接受文件版本。
     * @param revision 已保存磁盘版本
     * @author zExNocs
     * @date 2026/11/10
     * @since 4.9.1-l
     * @version 4.9.1-l
     */
    public record Global(String revision) {
        /** @param revision 有界非空版本 */
        public Global {
            require(revision);
        }
    }

    /**
     * 聊天定位全部由客户端重新解析，不能提交内部 scope 或私有凭据。
     * @param client 客户端
     * @param account 账号引用
     * @param target 聊天引用
     * @param revision 已保存版本
     * @author zExNocs
     * @date 2026/11/10
     * @since 4.9.1-l
     * @version 4.9.1-l
     */
    public record Chat(String client, String account, String target, String revision) {
        /**
     * @param client 客户端
     * @param account 账号
     * @param target 聊天
     * @param revision 版本
     */
        public Chat {
            List.of(client, account, target, revision).forEach(StickerModelController::require);
        }
    }

    /**
     * @param value 外部定位值
     * @throws IllegalArgumentException 当缺失或超长时
     */
    private static void require(String value) {
        if (value == null || value.isBlank() || value.length() > 2048) {
            throw new IllegalArgumentException("模型检测位置或版本无效。");
        }
    }
}
