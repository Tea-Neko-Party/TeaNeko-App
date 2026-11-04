package org.zexnocs.teanekoclient.web;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.teauser.interfaces.ITeaUserService;
import org.zexnocs.teanekoapp.utils.TeaNekoScopeService;
import org.zexnocs.teanekocore.actuator.task.TaskStages;
import org.zexnocs.teanekocore.event.interfaces.IEventService;
import org.zexnocs.teanekocore.utils.StableDigest;
import tools.jackson.databind.ObjectMapper;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * 网页档案、成员管理和入站交接；服务器身份、管理会话与模拟成员权限分别处理。
 * 先持久受理再通知原消息总线，重复页面与重连不得重放入站或另起模型循环。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：进入房间时调用模块准备扩展，入站回执等待实际异步业务阶段。
 *
 * <br>2026/11/05：删除房间时撤销旧来源并阻止迟到发送。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.8.2-l
 */
@Service
public final class WebChatService {
    private final WebChatStore store;
    private final WebTeaNekoClient client;
    private final ITeaUserService users;
    private final TeaNekoScopeService scopes;
    private final WebMessageFactory messages;
    private final IEventService events;
    private final ObjectMapper mapper;
    private final Executor executor;
    private final UUID instance = UUID.randomUUID();
    private final ConcurrentMap<UUID, Work> active = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Gate> gates = new ConcurrentHashMap<>();
    private final Semaphore slots = new Semaphore(8);
    private volatile boolean closed;
    // 2026/11/04：独立网页宿主可不装配扩展；完整 App 由 Spring 安装准备目录。
    private @org.jspecify.annotations.Nullable WebRoomPreparations preparations;

    /** @param preparations 原扫描式房间准备目录 */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void installPreparations(WebRoomPreparations preparations) {
        this.preparations = preparations;
    }

    /**
     * 2026/11/04：打开已保存房间并完成幂等准备，不从浏览器接受 scope。
     *
     * @param room 已保存的房间引用
     * @param guard 当前真实会话
     * @return 完成准备后的房间视图
     */
    public CompletionStage<WebChat.RoomView> open(String room, Runnable guard) {
        check(guard);
        return store.room(room, guard).thenCompose(value -> prepare(value, guard)).thenApply(WebChatStore::view);
    }

    /**
     * @param room 服务端已解析的房间
     * @param guard 当前真实会话
     * @return 模块准备完成后的同一房间
     */
    private CompletionStage<WebChat.Room> prepare(WebChat.Room room, Runnable guard) {
        check(guard);
        return preparations == null ? CompletableFuture.completedStage(room)
                : preparations.prepare(room, guard).thenApply(ignored -> room);
    }

    /**
     * @param store 原网页持久入口
     * @param client 原已注册平台
     * @param users 原 TeaUser 身份映射
     * @param scopes 原作用域规则
     * @param messages 原消息映射
     * @param events 原事件服务
     * @param mapper 固定请求摘要
     * @param executor 借用原业务执行域
     */
    public WebChatService(WebChatStore store, WebTeaNekoClient client, ITeaUserService users, TeaNekoScopeService scopes,
            WebMessageFactory messages, IEventService events, ObjectMapper mapper,
            @Qualifier("virtualExecutor") Executor executor) {
        this.store = store;
        this.client = client;
        this.users = users;
        this.scopes = scopes;
        this.messages = messages;
        this.events = events;
        this.mapper = mapper;
        this.executor = executor;
    }

    /**
     * @param guard 当前会话
     * @return 默认网页档案及实际聊天，初次访问不启动 Agent
     */
    public CompletionStage<WebChat.Home> home(Runnable guard) {
        check(guard);
        return users.getOrCreate(client, WebChat.DEFAULT_PROFILE).asStage().thenCompose(uuid -> store.home(uuid, guard));
    }

    /**
     * @param request 显式创建档案
     * @param guard 当前会话
     * @return 由原身份服务映射的独立网页档案
     */
    public CompletionStage<WebChat.Profile> profile(WebChat.CreateProfile request, Runnable guard) {
        check(guard);
        String id = WebChatStore.generated("profile", request.operationId());
        return home(guard).thenCompose(ignored -> users.getOrCreate(client, id).asStage())
                .thenCompose(uuid -> store.profile(request, uuid, guard));
    }

    /**
     * 2026/11/04：创建成功后完成模块准备，私聊默认配置只在缺失时初始化。
     * @param request 显式创建聊天，不接收原 scope
     * @param guard 当前会话
     * @return 已保存聊天的安全视图
     */
    public CompletionStage<WebChat.RoomView> create(WebChat.CreateRoom request, Runnable guard) {
        check(guard);
        String id = WebChatStore.generated("room", request.operationId());
        return store.profile(request.profiles().getFirst(), guard).thenCompose(profile -> {
            String scope = request.kind().equals("GROUP") ? scopes.getGroupScopeId(client, id)
                    : scopes.getPrivateScopeId(profile.uuid());
            return store.create(request, scope, guard);
        }).thenCompose(room -> prepare(room, guard)).thenApply(WebChatStore::view);
    }

    /**
     * @param room 原聊天
     * @param request 专用成员管理
     * @param guard 当前管理会话
     * @return 已提交成员版本；变更期间及之后旧来源均被撤销
     */
    public CompletionStage<WebChat.RoomView> member(String room, WebChat.ChangeMember request, Runnable guard) {
        check(guard);
        return store.room(room, guard).thenCompose(known -> {
            var gate = gates.computeIfAbsent(known.id(), ignored -> new Gate());
            gate.begin();
            try {
                return store.member(room, request, guard).thenApply(WebChatStore::view)
                        .whenComplete((value, failure) -> gate.end());
            } catch (RuntimeException failure) {
                gate.end();
                return CompletableFuture.failedStage(failure);
            }
        });
    }

    /**
     * 2026/11/05：删除时撤销原房间来源，迟到交付不能恢复已删除消息。
     * @param room 已知聊天标识
     * @param revision 已确认版本
     * @param guard 当前管理员会话
     * @return 实际删除结果；重复调用不重新创建房间
     */
    public CompletionStage<Boolean> delete(String room, long revision, Runnable guard) {
        check(guard);
        var gate = gates.computeIfAbsent(room, ignored -> new Gate());
        gate.begin();
        try {
            return store.deleteRoom(room, revision, guard).whenComplete((deleted, failure) -> gate.end());
        } catch (RuntimeException failure) {
            gate.end();
            return CompletableFuture.failedStage(failure);
        }
    }

    /**
     * @param room 固定聊天
     * @param request 原请求
     * @param guard 当前会话
     * @return 持久受理事实，不把事件通知当作模型或回复完成
     */
    public CompletionStage<WebChat.Receipt> submit(String room, WebChat.Send request, Runnable guard) {
        check(guard);
        // 2026/11/04：直接提交旧私聊同样完成默认准备，已有关闭记录不会被覆盖。
        return store.room(room, guard).thenCompose(known -> prepare(known, guard))
                .thenCompose(known -> accept(known, request, guard));
    }

    /**
     * @param room 原已知聊天
     * @param request 原请求
     * @param guard 当前会话
     * @return 同一物理工作或原持久事实
     */
    private CompletionStage<WebChat.Receipt> accept(WebChat.Room room, WebChat.Send request, Runnable guard) {
        String digest = StableDigest.sha256(room.id(), mapper.writeValueAsString(request));
        var existing = active.get(request.operationId());
        if (existing != null) {
            return existing.replay(digest, guard);
        }
        if (!slots.tryAcquire()) {
            return CompletableFuture.failedStage(new IllegalStateException("网页入站处理槽已满。"));
        }
        var gate = gates.computeIfAbsent(room.id(), ignored -> new Gate());
        final Work work;
        try {
            long epoch = gate.capture();
            work = new Work(digest, () -> {
                check(guard);
                gate.check(epoch);
            });
        } catch (RuntimeException failure) {
            slots.release();
            return CompletableFuture.failedStage(failure);
        }
        var previous = active.putIfAbsent(request.operationId(), work);
        if (previous != null) {
            slots.release();
            return previous.replay(digest, guard);
        }
        try {
            store.claim(room.id(), request, instance, work.guard).whenComplete((claim, failure) -> {
                if (failure != null) {
                    work.accepted.completeExceptionally(failure);
                    release(request.operationId(), work);
                } else if (!claim.acquired()) {
                    lookup(room.id(), request.operationId(), work, guard).whenComplete((receipt, queryFailure) -> {
                        if (queryFailure == null) {
                            work.accepted.complete(receipt);
                        } else {
                            work.accepted.completeExceptionally(queryFailure);
                        }
                        release(request.operationId(), work);
                    });
                } else {
                    work.accepted.complete(claim.receipt());
                    try {
                        dispatch(room.id(), request.operationId(), claim, work);
                    } catch (RuntimeException rejected) {
                        facts(room.id(), request.operationId(), "INDETERMINATE")
                                .whenComplete((receipt, saveFailure) -> release(request.operationId(), work));
                    }
                }
            });
        } catch (RuntimeException failure) {
            work.accepted.completeExceptionally(failure);
            release(request.operationId(), work);
        }
        return work.accepted.minimalCompletionStage();
    }

    /**
     * @param room 原聊天
     * @param operation 原编号
     * @param claim 原已提交消息
     * @param work 当前进程唯一拥有者
     */
    private void dispatch(String room, UUID operation, WebChatStore.Claim claim, Work work) {
        CompletableFuture.runAsync(work.guard, executor)
                .thenCompose(ignored -> messages.create(claim.room(), Objects.requireNonNull(claim.message()), work.guard, work.guard))
                .thenCompose(data -> {
                    data.checkCommandSource();
                    var event = new WebMessageReceiveEvent(data);
                    return TaskStages.successful(events.pushEventWithFuture(event), "网页消息入站")
                            // 2026/11/04：事件通知结束后继续等待实际命令和 Agent 工作，不阻塞通知线程。
                            .thenCompose(ignored -> event.processingCompletion())
                            .thenApply(ignored -> event.isCancelled() ? "REJECTED" : "DISPATCHED");
                }).handle((status, failure) -> facts(room, operation, failure == null ? status : "INDETERMINATE"))
                .thenCompose(stage -> stage).whenComplete((receipt, failure) -> release(operation, work));
    }

    /**
     * @param room 原聊天
     * @param operation 原编号
     * @param work 仅代表本次重放查询的新候选，不冒充旧工作拥有者
     * @param guard 当前会话
     * @return 安全查询阶段，同步装配失败也能释放原工作槽
     */
    private CompletionStage<WebChat.Receipt> lookup(String room, UUID operation, Work work, Runnable guard) {
        try {
            return store.operation(room, operation, instance, () -> active.get(operation) != work, guard);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedStage(failure);
        }
    }

    /**
     * @param room 原聊天
     * @param operation 原编号
     * @param status 已发生的入站事实
     * @return 宿主事实保存阶段；撤权不使已发生事实丢失，也不新增执行权限
     */
    private CompletionStage<WebChat.Receipt> facts(String room, UUID operation, String status) {
        try {
            return store.finish(room, operation, status, () -> {});
        } catch (RuntimeException failure) {
            return CompletableFuture.failedStage(failure);
        }
    }

    /**
     * @param room 固定聊天
     * @param operation 原编号
     * @param guard 当前会话
     * @return 原事实，查询不发消息
     */
    public CompletionStage<WebChat.Receipt> operation(String room, UUID operation, Runnable guard) {
        check(guard);
        return store.operation(room, operation, instance, () -> active.containsKey(operation), guard);
    }

    /**
     * @param operation 原编号
     * @param work 当前工作
     */
    private void release(UUID operation, Work work) {
        if (active.remove(operation, work)) {
            slots.release();
        }
    }

    /**
     * @param guard 原会话
     * @throws SecurityException 当会话或服务生命周期已经失效时
     */
    private void check(Runnable guard) {
        guard.run();
        if (closed) {
            throw new SecurityException("网页客户端正在关闭。");
        }
    }

    /** 撤销所有网页来源，保留已提交事实，不声称已取消外部 IO。 */
    @PreDestroy
    public void close() {
        closed = true;
    }

    /**
     * 成员变更在整个事务期间关闭旧来源，回滚同样只撤权，不额外授予能力。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    private static final class Gate {
        long epoch;
        boolean editing;

        /** @return 当前可捕获代次 */
        synchronized long capture() {
            if (editing) {
                throw new IllegalStateException("聊天成员正在更新，请稍后提交。");
            }
            return epoch;
        }

        /**
         * @param expected 原代次
         * @throws SecurityException 当成员正在修改或原能力失效时
         */
        synchronized void check(long expected) {
            if (editing || epoch != expected) {
                throw new SecurityException("网页成员已经变更，旧来源不再有效。");
            }
        }

        /** 开始专用管理，禁止与另一成员写入交叠。 */
        synchronized void begin() {
            if (editing) {
                throw new IllegalStateException("聊天成员已经在更新。");
            }
            editing = true;
            epoch++;
        }

        /** 实际事务结束后才重新开放新来源。 */
        synchronized void end() {
            epoch++;
            editing = false;
        }
    }

    /**
     * 原入站通知的物理工作，不代表 Agent 全轮业务完成。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    private static final class Work {
        final String digest;
        final Runnable guard;
        final CompletableFuture<WebChat.Receipt> accepted = new CompletableFuture<>();

        /**
         * @param digest 原目标和请求摘要
         * @param guard 原会话和成员代次
         */
        Work(String digest, Runnable guard) {
            this.digest = digest;
            this.guard = guard;
        }

        /**
         * @param digest 本次参数摘要
         * @param guard 本次会话
         * @return 原工作受理事实
         */
        CompletionStage<WebChat.Receipt> replay(String digest, Runnable guard) {
            if (!this.digest.equals(digest)) {
                throw new IllegalArgumentException("网页消息编号已绑定其他聊天或正文。");
            }
            return accepted.thenApply(receipt -> {
                guard.run();
                return receipt;
            });
        }
    }
}
