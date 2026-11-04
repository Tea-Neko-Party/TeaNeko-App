package org.zexnocs.teanekoapp.web.settings;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.web.client.ChatTargets;
import org.zexnocs.teanekoapp.web.client.ClientManagementDirectory;
import org.zexnocs.teanekoapp.web.client.IClientManagementAdapter;
import org.zexnocs.teanekocore.database.configdata.ConfigEditService;
import org.zexnocs.teanekocore.database.configdata.ConfigEdits;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.utils.StableDigest;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * 聊天设置的授权编排；平台解析和业务配置区分别扩展，核心事务不依赖 HTTP。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：自动配置目录复用原事务，提交前复核平台命名空间与配置分组授权。
 * <br>2026/11/04：管理员直接管理已启用配置，取消本地配置分组白名单，保留业务边界复核。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.3-l
 */
@Service
public final class ChatSettingsService {
    private final ChatSettingsDirectory sections;
    private final ClientManagementDirectory clients;
    private final ConfigEditService edits;
    private final PluginRegistry plugins;

    /**
     * 2026/11/04：配置可见性由管理身份、插件及客户端适用范围决定。
     * @param sections 已注册配置及专用投影目录
     * @param clients 原平台目录
     * @param edits 原子版本配置端口
     * @param plugins 原插件代次
     */
    public ChatSettingsService(ChatSettingsDirectory sections, ClientManagementDirectory clients, ConfigEditService edits,
                                PluginRegistry plugins) {
        this.clients = clients;
        this.edits = edits;
        this.plugins = plugins;
        this.sections = sections;
    }

    /**
     * 2026/11/04：管理员目录无需额外本地分组白名单。
     * @param guard 当前管理身份
     * @return 已注册且插件启用的配置区
     */
    public List<ChatSettings.Section> sections(Runnable guard) {
        guard.run();
        return sections.entries().keySet().stream().sorted().filter(this::allowed)
                .map(id -> describe(sections.entries().get(id))).toList();
    }

    /**
     * @param client 实际客户端
     * @param account 实际账号引用
     * @param target 实际聊天引用
     * @param guard 当前管理员
     * @return 适用于此真实目标且已授权的配置目录
     */
    public CompletionStage<List<ChatSettings.Section>> sections(String client, String account, String target,
            Runnable guard) {
        var checked = clients.guard(client, guard);
        return clients.invoke(client, adapter -> adapter.resolve(account, target).thenApply(resolved -> {
            checked.run();
            var namespaces = adapter.configNamespaces(resolved);
            return sections.entries().values().stream().filter(entry -> allowed(entry.id()))
                    .filter(entry -> java.util.Arrays.stream(entry.section().manager().namespaces()).anyMatch(namespaces::contains))
                    .sorted(java.util.Comparator.comparing(ChatSettingsDirectory.Entry::id))
                    .map(ChatSettingsService::describe).toList();
        }));
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
        return clients.invoke(client, adapter -> adapter.resolve(account, target).thenCompose(resolved -> {
            var checked = namespaceGuard(lease, adapter, resolved);
            return edits.read(lease.section().manager(), resolved.scope(), checked).thenCompose(snapshot ->
                    lease.section().view(resolved, snapshot)).thenApply(value -> {
                        checked.run();
                        return value;
                    });
        })).thenApply(value -> {
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
        return clients.invoke(client, adapter -> adapter.resolve(account, target).thenCompose(resolved -> {
            var checked = namespaceGuard(lease, adapter, resolved);
            return lease.section().jsonValues()
                    ? edits.changeJson(lease.section().manager(), resolved.scope(), "web.local-admin", change, checked)
                    : edits.change(lease.section().manager(), resolved.scope(), "web.local-admin", change, checked);
        }))
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
        return clients.invoke(client, adapter -> adapter.resolve(account, target).thenCompose(resolved -> {
            var checked = namespaceGuard(lease, adapter, resolved);
            return edits.operation(lease.section().manager(), operation, "web.local-admin", checked)
                        .thenApply(result -> {
                            lease.guard().run();
                            if (!result.targetDigest().equals(StableDigest.sha256(resolved.scope()))) {
                                throw new NoSuchElementException("该操作不属于当前聊天配置。");
                            }
                            return result;
                        });
        }));
    }

    /**
     * @param id 既有配置区
     * 2026/11/04：已注册配置无需本地白名单，所属插件仍须启用。
     * @return 配置是否存在且归属有效
     */
    private boolean allowed(String id) {
        var entry = sections.entries().get(id);
        if (entry == null) {
            return false;
        }
        var owner = AnnotatedElementUtils.findMergedAnnotation(entry.owner(), PluginOwned.class);
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
        var entry = sections.entries().get(id);
        var owner = AnnotatedElementUtils.findMergedAnnotation(entry.owner(), PluginOwned.class);
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
        return new Lease(entry.section(), checked);
    }

    /**
     * @param lease 已捕获配置区权限
     * @param adapter 真实客户端适配器
     * @param target 已解析目标
     * @return 在读取和实际提交前复核适用范围的能力
     */
    private static Runnable namespaceGuard(Lease lease, IClientManagementAdapter adapter, ChatTargets.Resolved target) {
        Runnable checked = () -> {
            lease.guard().run();
            var namespaces = adapter.configNamespaces(target);
            if (java.util.Arrays.stream(lease.section().manager().namespaces()).noneMatch(namespaces::contains)) {
                throw new SecurityException("该配置不适用于所选客户端或聊天类型。");
            }
        };
        checked.run();
        return checked;
    }

    /**
     * @param entry 已登记的配置元数据
     * @return 不含内部类型与存储键的目录项
     */
    private static ChatSettings.Section describe(ChatSettingsDirectory.Entry entry) {
        return new ChatSettings.Section(entry.id(), entry.section().name(), entry.group(),
                entry.section().manager().description());
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
