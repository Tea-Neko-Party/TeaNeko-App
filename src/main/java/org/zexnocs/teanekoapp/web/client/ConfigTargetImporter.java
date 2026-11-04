package org.zexnocs.teanekoapp.web.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.config.ConfigNamespace;
import org.zexnocs.teanekoapp.config.TeaNekoConfigNamespaces;
import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserService;
import org.zexnocs.teanekoapp.web.settings.ChatSettingsDirectory;
import org.zexnocs.teanekocore.database.configdata.ConfigEditService;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * 从适用的所有已登记业务配置恢复已知聊天，通过各管理器原 EasyData 存储读取。
 *
 * 修改记录：<br>
 * 2026/11/04：由 AgentConfigTargetImporter 移至平台管理层，恢复通用配置并按管理器分别分页。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.6-l
 */
@Service
public final class ConfigTargetImporter implements IKnownChatImporter {
    private final IChatTargetStore targets;
    private final ConfigEditService configs;
    private final ITeaUserService users;
    private final ChatSettingsDirectory directory;
    private final PluginRegistry plugins;
    private final ObjectMapper mapper;
    private final Executor executor;

    /**
     * @param targets 原聊天目录
     * @param configs 各管理器的有界配置读取
     * @param users 已迁移的用户映射
     * @param directory 实际启用配置声明
     * @param plugins 原插件代次
     * @param mapper 游标结构编码
     * @param executor 原业务执行域
     */
    public ConfigTargetImporter(IChatTargetStore targets, ConfigEditService configs, ITeaUserService users,
            ChatSettingsDirectory directory, PluginRegistry plugins, ObjectMapper mapper,
            @Qualifier("virtualExecutor") Executor executor) {
        this.targets = targets;
        this.configs = configs;
        this.users = users;
        this.directory = directory;
        this.plugins = plugins;
        this.mapper = mapper;
        this.executor = executor;
    }

    /**
     * 2026/11/04：每个管理器至多读取一百个键，旧 Agent 单游标自动重新建立通用目录。
     * @param client 真实已装配客户端
     * @param guard 当前管理授权
     * @return 本批观察和所有管理器分页位置实际提交后的阶段
     */
    @Override
    public CompletionStage<Void> discover(ITeaNekoClient client, Runnable guard) {
        guard.run();
        String id = client.getClientId();
        return targets.importCursor(id).thenCompose(cursor -> {
            if (cursor.equals("v2:!done")) {
                return CompletableFuture.completedStage(null);
            }
            var positions = new TreeMap<String, String>();
            if (cursor.startsWith("v2:")) {
                mapper.readTree(cursor.substring(3)).properties()
                        .forEach(entry -> positions.put(entry.getKey(), entry.getValue().asString()));
            }
            var namespaces = new HashSet<>(Set.of(TeaNekoConfigNamespaces.GENERAL,
                    TeaNekoConfigNamespaces.GROUP, TeaNekoConfigNamespaces.PRIVATE));
            var declaration = AnnotatedElementUtils.findMergedAnnotation(client.getClass(), ConfigNamespace.class);
            if (declaration != null) {
                namespaces.addAll(List.of(declaration.generalNamespace()));
                namespaces.addAll(List.of(declaration.groupNamespace()));
                namespaces.addAll(List.of(declaration.privateNamespace()));
            }
            CompletionStage<Void> work = CompletableFuture.completedStage(null);
            for (var entry : directory.entries().values().stream()
                    .sorted(Comparator.comparing(ChatSettingsDirectory.Entry::id)).toList()) {
                var manager = entry.section().manager();
                var owner = AnnotatedElementUtils.findMergedAnnotation(entry.owner(), PluginOwned.class);
                if (Arrays.stream(manager.namespaces()).noneMatch(namespaces::contains)
                        || owner != null && !plugins.isEnabled(owner.value())) {
                    continue;
                }
                if (positions.getOrDefault(entry.id(), "").equals("!done")) {
                    continue;
                }
                var token = owner == null ? null : plugins.capture(owner.value());
                Runnable checked = () -> {
                    guard.run();
                    if (token != null) {
                        plugins.check(token);
                    }
                };
                work = work.thenCompose(ignored -> {
                    checked.run();
                    return configs.keys(manager, positions.getOrDefault(entry.id(), ""), 100)
                            .thenComposeAsync(page -> {
                                checked.run();
                                var observations = new ArrayList<ChatTargets.Observation>();
                                for (String key : page.keys()) {
                                    observation(client, key).ifPresent(observations::add);
                                }
                                return targets.remember(observations, checked).thenRun(() ->
                                        positions.put(entry.id(), page.next().isEmpty() ? "!done" : page.next()));
                            }, executor);
                });
            }
            return work.thenCompose(ignored -> {
                guard.run();
                String next = positions.values().stream().allMatch("!done"::equals)
                        ? "v2:!done" : "v2:" + mapper.writeValueAsString(positions);
                return targets.imported(id, next);
            });
        });
    }

    /**
     * @param client 实际客户端
     * @param key 数据库中的真实配置键
     * @return 可验证的群或私聊定位，不推定所属机器人账号
     */
    private Optional<ChatTargets.Observation> observation(ITeaNekoClient client, String key) {
        String id = client.getClientId();
        String prefix = id + "-group@";
        if (key.startsWith(prefix) && key.length() > prefix.length()) {
            return Optional.of(new ChatTargets.Observation(id, "", "GROUP", key.substring(prefix.length()), key, "", "CONFIG"));
        }
        if (key.startsWith("private@")) {
            final UUID uuid;
            try {
                uuid = UUID.fromString(key.substring(8));
            } catch (IllegalArgumentException failure) {
                return Optional.empty();
            }
            String platform = users.getPlatformId(client, uuid);
            if (platform != null) {
                return Optional.of(new ChatTargets.Observation(id, "", "PRIVATE", platform, key, "", "CONFIG"));
            }
        }
        return Optional.empty();
    }
}
