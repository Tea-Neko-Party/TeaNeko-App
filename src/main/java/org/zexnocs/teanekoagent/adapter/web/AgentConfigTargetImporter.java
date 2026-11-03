package org.zexnocs.teanekoagent.adapter.web;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.adapter.config.AgentScopeConfigManager;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserService;
import org.zexnocs.teanekoapp.web.client.ChatTargets;
import org.zexnocs.teanekoapp.web.client.IChatTargetStore;
import org.zexnocs.teanekoapp.web.client.IKnownChatImporter;
import org.zexnocs.teanekocore.database.configdata.ConfigEditService;
import org.zexnocs.teanekocore.database.configdata.scanner.ConfigManager;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * 每批最多一百项原配置建立已知聊天映射，不把未归属历史伪装成当前账号联系人。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@Service
public final class AgentConfigTargetImporter implements IKnownChatImporter {
    private final IChatTargetStore targets;
    private final ConfigEditService configs;
    private final ITeaUserService users;
    private final Executor executor;

    /**
     * @param targets 元数据存储
     * @param configs 原配置有界当前读
     * @param users 原用户身份映射
     * @param executor 借用虚拟执行域
     */
    public AgentConfigTargetImporter(IChatTargetStore targets, ConfigEditService configs, ITeaUserService users,
                                     @Qualifier("virtualExecutor") Executor executor) {
        this.targets = targets;
        this.configs = configs;
        this.users = users;
        this.executor = executor;
    }

    /**
     * @param client 实际客户端
     * @param guard 当前授权
     * @return 本批目录及发现游标提交
     */
    @Override
    public CompletionStage<Void> discover(ITeaNekoClient client, Runnable guard) {
        String id = client.getClientId();
        return targets.importCursor(id).thenCompose(cursor -> {
            if (cursor.equals("!done")) {
                return CompletableFuture.completedStage(null);
            }
            return configs.keys(AgentScopeConfigManager.class.getAnnotation(ConfigManager.class), cursor, 100)
                    .thenComposeAsync(page -> {
                        guard.run();
                        var values = new ArrayList<ChatTargets.Observation>();
                        for (String key : page.keys()) {
                            String prefix = id + "-group@";
                            if (key.startsWith(prefix)) {
                                values.add(new ChatTargets.Observation(id, "", "GROUP", key.substring(prefix.length()),
                                        key, "", "CONFIG"));
                            } else if (key.startsWith("private@")) {
                                try {
                                    String platform = users.getPlatformId(client, UUID.fromString(key.substring(8)));
                                    if (platform != null) {
                                        values.add(new ChatTargets.Observation(id, "", "PRIVATE", platform, key, "", "CONFIG"));
                                    }
                                } catch (IllegalArgumentException ignored) {
                                    // 损坏或无法识别的旧键不冒充有效联系人。
                                }
                            }
                        }
                        return targets.remember(values, guard).thenCompose(ignored ->
                                targets.imported(id, page.next().isEmpty() ? "!done" : page.next()));
                    }, executor);
        });
    }
}
