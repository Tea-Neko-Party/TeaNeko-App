package org.zexnocs.teanekoclient.onebot.core;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserService;
import org.zexnocs.teanekoapp.utils.TeaNekoScopeService;
import org.zexnocs.teanekoapp.web.client.ChatTargets;
import org.zexnocs.teanekoapp.web.client.IChatTargetStore;
import org.zexnocs.teanekoapp.web.client.IKnownChatImporter;
import org.zexnocs.teanekoclient.onebot.sender.group.GetGroupListSender;
import org.zexnocs.teanekoclient.onebot.sender.private_.GetFriendListSender;
import org.zexnocs.teanekocore.actuator.task.TaskStages;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 合并真实 OneBot 联系人与已登记配置，断线时只使用已知目录，平台查询须唯一连接身份就绪。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：所有适用配置参与历史目录发现，显式刷新可重新扫描迁移数据。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.6-l
 */
@Service
public final class OnebotChatDirectory {
    private final IChatTargetStore store;
    private final ObjectProvider<OnebotTeaNekoClient> clients;
    private final ObjectProvider<OnebotClient> transport;
    private final ObjectProvider<IKnownChatImporter> importers;
    private final ObjectProvider<GetGroupListSender> groups;
    private final ObjectProvider<GetFriendListSender> friends;
    private final ITeaUserService users;
    private final TeaNekoScopeService scopes;
    private final Executor executor;
    private final AtomicBoolean syncing = new AtomicBoolean();
    private final AtomicLong lastNetwork = new AtomicLong();

    /**
     * @param store 已知目录
     * @param clients 真实客户端
     * @param transport 真实连接
     * @param importers 业务配置发现贡献
     * @param groups 原群列表端口
     * @param friends 原好友列表端口
     * @param users 原身份映射
     * @param scopes 原配置定位
     * @param executor 借用虚拟执行域
     */
    public OnebotChatDirectory(IChatTargetStore store, ObjectProvider<OnebotTeaNekoClient> clients,
                               ObjectProvider<OnebotClient> transport, ObjectProvider<IKnownChatImporter> importers,
                               ObjectProvider<GetGroupListSender> groups, ObjectProvider<GetFriendListSender> friends,
                               ITeaUserService users, TeaNekoScopeService scopes,
                               @Qualifier("virtualExecutor") Executor executor) {
        this.store = store;
        this.clients = clients;
        this.transport = transport;
        this.importers = importers;
        this.groups = groups;
        this.friends = friends;
        this.users = users;
        this.scopes = scopes;
        this.executor = executor;
    }

    /**
     * 2026/11/04：已有账号也继续导入，避免迁移后目录存在而通用配置从未发现。
     * @return 当前已知账号及未归属历史配置目录
     */
    public CompletionStage<List<ChatTargets.Account>> accounts() {
        return known(() -> {}).thenCompose(ignored -> store.accounts("onebot"));
    }

    /**
     * @param account 原账号
     * @param after 原页尾
     * @param kind 过滤类型
     * @return 有界已知目录
     */
    public CompletionStage<ChatTargets.Page> page(String account, String after, String kind) {
        return store.page("onebot", account, after, kind);
    }

    /**
     * @param account 原账号
     * @param target 原聊天
     * @return 提交时重核的真实目标
     */
    public CompletionStage<ChatTargets.Resolved> resolve(String account, String target) {
        return store.resolve("onebot", account, target).thenApplyAsync(value -> {
            var client = Objects.requireNonNull(clients.getIfAvailable(), "OneBot 平台尚未装配。");
            String expected;
            if (value.kind().equals("GROUP")) {
                expected = scopes.getGroupScopeId(client, value.platformId());
            } else {
                UUID uuid = users.get(client, value.platformId());
                if (uuid == null) {
                    throw new NoSuchElementException("该用户的身份映射已失效，请重新发现聊天目录。");
                }
                expected = scopes.getPrivateScopeId(uuid);
            }
            if (!expected.equals(value.scope())) {
                throw new IllegalStateException("聊天身份映射已经变化，请重新发现目录。");
            }
            return value;
        }, executor);
    }

    /**
     * 一次只读同步不重入；物理阶段完成后才释放名额，账号换代不写入迟到联系人。
     * @param guard 当前管理授权
     * @return 实际同步后的账号目录
     */
    public CompletionStage<List<ChatTargets.Account>> discover(Runnable guard) {
        if (!syncing.compareAndSet(false, true)) {
            return CompletableFuture.failedStage(new IllegalStateException("目录刷新正在进行，请稍后查看结果。"));
        }
        CompletionStage<List<ChatTargets.Account>> physical;
        try {
            // 2026/11/04：显式刷新重新发现迁移或新增的配置，在线联系人仍独立限流。
            physical = store.importCursor("onebot").thenCompose(cursor ->
                    Set.of("!done", "v2:!done").contains(cursor) ? store.imported("onebot", "")
                            : CompletableFuture.completedStage(null))
                    .thenCompose(ignored -> known(guard)).thenComposeAsync(ignored -> {
                        guard.run();
                        var client = transport.getIfAvailable();
                        var identity = client == null ? null : client.connectionIdentity();
                        if (identity == null || !identity.ready()) {
                            return store.accounts("onebot");
                        }
                        long now = System.currentTimeMillis();
                        long previous = lastNetwork.get();
                        if (now - previous < 60000 || !lastNetwork.compareAndSet(previous, now)) {
                            return store.accounts("onebot");
                        }
                        Runnable checked = () -> {
                            guard.run();
                            if (!identity.equals(client.connectionIdentity())) {
                                throw new IllegalStateException("OneBot 连接或账号已变化，请重新同步。");
                            }
                        };
                        checked.run();
                        return remote(identity.selfId(), checked).thenCompose(done -> store.accounts("onebot"));
                    }, executor);
        } catch (RuntimeException failure) {
            physical = CompletableFuture.failedStage(failure);
        }
        physical = physical.whenComplete((ignored, failure) -> syncing.set(false));
        return physical.toCompletableFuture().minimalCompletionStage();
    }

    /**
     * @param guard 当前授权
     * @return 已登记配置的一批发现
     */
    private CompletionStage<Void> known(Runnable guard) {
        var client = clients.getIfAvailable();
        if (client == null) {
            return CompletableFuture.completedStage(null);
        }
        CompletionStage<Void> result = CompletableFuture.completedStage(null);
        for (var importer : importers.orderedStream().toList()) {
            result = result.thenCompose(ignored -> importer.discover(client, guard));
        }
        return result;
    }

    /**
     * @param self 原连接确认账号
     * @param guard 原连接及会话复核
     * @return 有界联系人保存，不发送聊天消息
     */
    private CompletionStage<Void> remote(String self, Runnable guard) {
        var client = Objects.requireNonNull(clients.getIfAvailable(), "OneBot 平台尚未装配。");
        var groupSender = Objects.requireNonNull(groups.getIfAvailable(), "群列表端口尚未装配。");
        var friendSender = Objects.requireNonNull(friends.getIfAvailable(), "好友列表端口尚未装配。");
        return store.rememberAccount("onebot", self).thenCompose(ignored -> {
            guard.run();
            return TaskStages.successful(groupSender.get(), "查询 OneBot 群列表");
        }).thenComposeAsync(values -> {
            guard.run();
            if (values == null || values.size() > 2000) {
                throw new IllegalStateException("平台群目录为空或超出本批上限。");
            }
            var observations = values.stream().map(value -> new ChatTargets.Observation("onebot", self, "GROUP",
                    Long.toString(value.getGroupId()), scopes.getGroupScopeId(client, Long.toString(value.getGroupId())),
                    bounded(value.getGroupName()), "PLATFORM")).toList();
            return batches(observations, guard);
        }, executor).thenCompose(ignored -> {
            guard.run();
            return TaskStages.successful(friendSender.get(), "查询 OneBot 好友列表");
        }).thenComposeAsync(values -> {
            guard.run();
            if (values == null || values.size() > 2000) {
                throw new IllegalStateException("平台好友目录为空或超出本批上限。");
            }
            CompletionStage<Void> result = CompletableFuture.completedStage(null);
            var observations = new ArrayList<ChatTargets.Observation>();
            for (var value : values) {
                result = result.thenCompose(ignored -> {
                    guard.run();
                    String platform = Long.toString(value.getUserId());
                    return users.getOrCreate(client, platform).asStage().thenAccept(uuid -> observations.add(
                            new ChatTargets.Observation("onebot", self, "PRIVATE", platform,
                                    scopes.getPrivateScopeId(uuid), bounded(value.getRemark() == null
                                    || value.getRemark().isBlank() ? value.getNickname() : value.getRemark()), "PLATFORM")));
                });
            }
            return result.thenCompose(ignored -> batches(observations, guard));
        }, executor);
    }

    /**
     * @param values 已验证观察
     * @param guard 当前来源
     * @return 逐批实际保存
     */
    private CompletionStage<Void> batches(List<ChatTargets.Observation> values, Runnable guard) {
        CompletionStage<Void> result = CompletableFuture.completedStage(null);
        for (int start = 0; start < values.size(); start += 200) {
            var batch = List.copyOf(values.subList(start, Math.min(start + 200, values.size())));
            result = result.thenCompose(ignored -> store.remember(batch, guard));
        }
        return result;
    }

    /**
     * @param value 可选平台显示名
     * @return 有界名称，未知时不伪造
     */
    private static String bounded(@org.jspecify.annotations.Nullable String value) {
        return value == null ? "" : value.substring(0, Math.min(128, value.length()));
    }
}
