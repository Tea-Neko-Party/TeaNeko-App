package org.zexnocs.teanekoapp.web.settings;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.web.WebSecurityFileConfig;
import org.zexnocs.teanekoapp.web.client.ClientManagementDirectory;
import org.zexnocs.teanekocore.database.configdata.ConfigEditService;
import org.zexnocs.teanekocore.database.configdata.ConfigEdits;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.framework.extension.AnnotatedBeanRegistry;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.utils.StableDigest;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * 聊天设置的授权编排；平台解析和业务配置区分别扩展，核心事务不依赖 HTTP。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@Service
public final class ChatSettingsService {
    private final AnnotatedBeanRegistry<String, IChatSettingsSection> sections;
    private final ClientManagementDirectory clients;
    private final ConfigEditService edits;
    private final PluginRegistry plugins;
    private final IFileConfigService files;

    /**
     * @param scanner 原 Bean 扫描器
     * @param clients 原平台目录
     * @param edits 原子版本配置端口
     * @param plugins 原插件代次
     * @param files 本地允许配置区
     */
    public ChatSettingsService(IBeanScanner scanner, ClientManagementDirectory clients, ConfigEditService edits,
                                PluginRegistry plugins, IFileConfigService files) {
        this.clients = clients;
        this.edits = edits;
        this.plugins = plugins;
        this.files = files;
        sections = new AnnotatedBeanRegistry<>(scanner, TeaNekoChatSettings.class, IChatSettingsSection.class,
                declaration -> {
                    if (!declaration.value().matches("[a-z][a-z0-9_.-]{0,63}") || declaration.version() != 1) {
                        throw new IllegalArgumentException("聊天设置区的标识或版本无效。");
                    }
                    return declaration.value();
                });
    }

    /**
     * @param guard 当前管理身份
     * @return 本地允许且插件启用的配置区
     */
    public List<ChatSettings.Section> sections(Runnable guard) {
        guard.run();
        return sections.entries().keySet().stream().sorted().filter(this::allowed)
                .map(id -> new ChatSettings.Section(id, sections.require(id).bean().name())).toList();
    }

    /**
     * @param client 平台
     * @param account 账号引用
     * @param target 聊天引用
     * @param section 配置区
     * @param guard 当前会话
     * @return 实际当前读与业务安全字段
     */
    public CompletionStage<ChatSettings.View> read(String client, String account, String target,
                                                  String section, Runnable guard) {
        var lease = lease(section, clients.guard(client, guard));
        return clients.invoke(client, adapter -> adapter.resolve(account, target).thenCompose(resolved ->
                edits.read(lease.section().manager(), resolved.scope(), lease.guard()).thenCompose(snapshot ->
                        lease.section().view(resolved, snapshot)))).thenApply(value -> {
                            lease.guard().run();
                            return value;
                        });
    }

    /**
     * @param client 原平台
     * @param account 原账号
     * @param target 原聊天
     * @param section 已允许配置区
     * @param change 明确版本动作
     * @param guard 当前会话
     * @return 已提交操作回执
     */
    public CompletionStage<ConfigEdits.Receipt> change(String client, String account, String target,
                                                      String section, ConfigEdits.Change change, Runnable guard) {
        var lease = lease(section, clients.guard(client, guard));
        lease.section().validate(change);
        return clients.invoke(client, adapter -> adapter.resolve(account, target).thenCompose(resolved ->
                edits.change(lease.section().manager(), resolved.scope(), "web.local-admin", change, lease.guard())))
                .thenApply(value -> {
                    lease.guard().run();
                    return value;
                });
    }

    /**
     * @param client 原平台
     * @param account 原账号
     * @param target 原聊天
     * @param section 原配置区
     * @param operation 原操作 UUID
     * @param guard 当前会话
     * @return 与该真实聊天匹配的持久回执
     */
    public CompletionStage<ConfigEdits.Receipt> operation(String client, String account, String target,
                                                         String section, UUID operation, Runnable guard) {
        var lease = lease(section, clients.guard(client, guard));
        return clients.invoke(client, adapter -> adapter.resolve(account, target).thenCompose(resolved ->
                edits.operation(lease.section().manager(), operation, "web.local-admin", lease.guard())
                        .thenApply(result -> {
                            lease.guard().run();
                            if (!result.targetDigest().equals(StableDigest.sha256(resolved.scope()))) {
                                throw new NoSuchElementException("该操作不属于当前聊天配置。");
                            }
                            return result;
                        })));
    }

    /**
     * @param id 既有配置区
     * @return 是否明确允许且归属有效
     */
    private boolean allowed(String id) {
        if (!files.requireCurrent(WebSecurityFileConfig.class).snapshot().settingsSections().contains(id)) {
            return false;
        }
        var owner = AnnotatedElementUtils.findMergedAnnotation(sections.require(id).type(), PluginOwned.class);
        return owner == null || plugins.isEnabled(owner.value());
    }

    /**
     * @param id 原区标识
     * @param guard 当前身份复核
     * @return 固定实现与重复授权
     */
    private Lease lease(String id, Runnable guard) {
        guard.run();
        if (!allowed(id)) {
            throw new SecurityException("该聊天设置区尚未在本地允许或插件已关闭。");
        }
        var entry = sections.require(id);
        var owner = AnnotatedElementUtils.findMergedAnnotation(entry.type(), PluginOwned.class);
        var token = owner == null ? null : plugins.capture(owner.value());
        Runnable checked = () -> {
            guard.run();
            if (!allowed(id)) {
                throw new SecurityException("聊天设置授权已经变化。");
            }
            if (token != null) {
                plugins.check(token);
            }
        };
        return new Lease(entry.bean(), checked);
    }

    /**
     * 不导出的本次设置实现与授权能力。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param section 原业务端口
     * @param guard 原身份及插件复核
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    @JsonIgnoreType
    private record Lease(IChatSettingsSection section, Runnable guard) {}
}
