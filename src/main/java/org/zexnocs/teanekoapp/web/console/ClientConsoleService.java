package org.zexnocs.teanekoapp.web.console;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.message.content.TextTeaNekoContentPart;
import org.zexnocs.teanekoapp.web.WebSecurityFileConfig;
import org.zexnocs.teanekoapp.web.client.ClientManagementDirectory;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.CommandMapData;
import org.zexnocs.teanekocore.command.CommandMethodContract;
import org.zexnocs.teanekocore.command.CommandResult;
import org.zexnocs.teanekocore.command.CommandScanner;
import org.zexnocs.teanekocore.command.ManagementCommandPolicy;
import org.zexnocs.teanekocore.command.api.CommandOrigin;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.command.api.CommandScope;
import org.zexnocs.teanekocore.command.interfaces.ICommandDispatcher;
import org.zexnocs.teanekocore.command.interfaces.ICommandErrorHandler;
import org.zexnocs.teanekocore.command.interfaces.IHelpSubCommandHandler;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.utils.StableDigest;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

/**
 * 指令台受理、连接捕获、原命令链和持久终态的编排，不派生原生平台身份。
 * 每个编号只有一个物理工作拥有者，浏览器断开不释放实际执行槽或触发自动重放。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/05：指令台目录只查询已注册方法，不在页面请求中解析 Bean 类型或重新枚举方法。
 *
 * <br>2026/11/06：补齐执行与回执持久化诊断，明确命令拒绝原因。
 * <br>2026/11/07：命令目录与执行复核使用真实子命令贡献者归属。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.9.0-l
 */
@Service
public final class ClientConsoleService {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ClientConsoleService.class);
    private static final String ACTOR = "web.local-admin";
    private final UUID instance = UUID.randomUUID();
    private final ClientManagementDirectory clients;
    private final ConsoleOperationStore operations;
    private final ICommandDispatcher dispatcher;
    private final CommandScanner commands;
    private final IFileConfigService files;
    private final org.zexnocs.teanekocore.plugin.PluginAccess plugins;
    private final Executor executor;
    private final Semaphore slots = new Semaphore(8);
    private final ConcurrentMap<UUID, Work> active = new ConcurrentHashMap<>();
    private volatile boolean closed;

    /**
     * 2026/11/05：命令元数据复用原 Scanner，移除运行期底层发现依赖。
     * @param clients 原平台管理目录
     * @param operations 原 EasyData 持久回执
     * @param dispatcher 原命令分发器
     * @param commands 原已注册命令目录
     * @param files 本地白名单
     * @param plugins 原命令插件归属和代次
     * @param executor 借用原执行域
     */
    public ClientConsoleService(ClientManagementDirectory clients, ConsoleOperationStore operations,
            ICommandDispatcher dispatcher, CommandScanner commands, IFileConfigService files,
            org.zexnocs.teanekocore.plugin.PluginAccess plugins,
            @Qualifier("virtualExecutor") Executor executor) {
        this.clients = clients;
        this.operations = operations;
        this.dispatcher = dispatcher;
        this.commands = commands;
        this.files = files;
        this.plugins = plugins;
        this.executor = executor;
    }

    /**
     * @param client 原平台
     * @param account 原账号引用
     * @param target 原聊天引用
     * @param session 当前真实会话
     * @return 明确声明的管理方法及当前平台状态，不能作为未来执行许可
     */
    public CompletionStage<ClientConsole.Catalog> catalog(String client, String account, String target, Runnable session) {
        var guard = clients.guard(client, session);
        return clients.invoke(client, adapter -> adapter.resolve(account, target).thenCompose(resolved ->
                adapter.consoleState(resolved).thenApply(state -> {
                    guard.run();
                    var found = new ArrayList<ClientConsole.Command>();
                    // 2026/11/07：从已编译命令目录投影全部管理声明，显式白名单才收窄前缀。
                    var policy = files.requireCurrent(WebSecurityFileConfig.class).snapshot();
                    var prefixes = commands.getPrefixCommandKeys().stream()
                            .filter(policy::allowsConsolePrefix).distinct().toList();
                    for (var prefix : prefixes) {
                        var map = commands.getPrefixCommand(prefix);
                        if (map == null || !map.getCommandAnnotation().enable()) {
                            continue;
                        }
                        // 2026/11/07：方法目录复用扫描缓存，插件子命令按实际贡献 Bean 判断归属。
                        for (var method : map.getRegisteredMethods()) {
                            var metadata = CommandMethodContract.metadata(method);
                            if (!ManagementCommandPolicy.declared(method) || !plugins.enabled(map.target(method), method)) {
                                continue;
                            }
                            String usage = prefix;
                            var sub = metadata.child();
                            if (sub != null && map.getSubCommand(sub.value()[0]) != null) {
                                usage += " " + sub.value()[0];
                            } else if (!method.equals(map.getDefaultCommandMethod())) {
                                continue;
                            }
                            found.add(new ClientConsole.Command(usage, !metadata.documented() ? "未提供使用说明"
                                    : metadata.description().substring(0, Math.min(4000, metadata.description().length())),
                                    Objects.requireNonNull(metadata.management()).sendsMessages(),
                                    plugins.owner(map.target(method), method)));
                        }
                    }
                    return new ClientConsole.Catalog(resolved.name(), state,
                            found.stream().distinct().sorted(Comparator.comparing(ClientConsole.Command::usage)).limit(100).toList(), Instant.now());
                })));
    }

    /**
     * 先持久受理再启动唯一工作，立即返回受理事实，最终状态通过原编号查询。
     * @param client 原平台
     * @param account 原账号
     * @param target 原聊天
     * @param request 原正文、连接与编号
     * @param session 当前会话
     * @return 真实受理或原操作事实，不把受理当作完成
     */
    public CompletionStage<ClientConsole.Receipt> submit(String client, String account, String target,
            ClientConsole.Request request, Runnable session) {
        Runnable platform = clients.guard(client, session);
        String boundary = boundary(client, account, target);
        String digest = StableDigest.sha256(boundary, request.text(), request.connection(), request.observedAt().toString());
        var existing = active.get(request.operationId());
        if (existing != null) {
            requireSame(existing, digest);
            return existing.accepted.thenApply(receipt -> {
                platform.run();
                return receipt;
            }).minimalCompletionStage();
        }
        if (closed || !slots.tryAcquire()) {
            return CompletableFuture.failedStage(new IllegalStateException("指令台正在关闭或实际执行槽已满。"));
        }
        var work = new Work(client, account, target, boundary, digest, request, platform);
        var previous = active.putIfAbsent(request.operationId(), work);
        if (previous != null) {
            work.close();
            slots.release();
            requireSame(previous, digest);
            return previous.accepted.thenApply(receipt -> {
                platform.run();
                return receipt;
            }).minimalCompletionStage();
        }
        CompletionStage<ConsoleOperationStore.Claim> claimed;
        try {
            claimed = operations.claim(ACTOR, boundary, digest, request, instance, platform);
        } catch (RuntimeException failure) {
            work.accepted.completeExceptionally(failure);
            release(work);
            return work.accepted.minimalCompletionStage();
        }
        claimed.whenComplete((claim, failure) -> {
            if (failure != null) {
                work.accepted.completeExceptionally(failure);
                release(work);
            } else if (!claim.acquired()) {
                lookup(work).whenComplete((receipt, lookupFailure) -> {
                            if (lookupFailure == null) {
                                work.accepted.complete(receipt);
                            } else {
                                work.accepted.completeExceptionally(lookupFailure);
                            }
                            release(work);
                        });
            } else {
                start(work);
                work.accepted.complete(claim.receipt());
            }
        });
        return work.accepted.minimalCompletionStage();
    }

    /**
     * @param client 原平台
     * @param account 原账号
     * @param target 原聊天
     * @param operation 原编号
     * @param session 当前会话
     * @return 同主体及目标的原持久事实，孤立非终态不重新执行
     */
    public CompletionStage<ClientConsole.Receipt> operation(String client, String account, String target,
            UUID operation, Runnable session) {
        var guard = clients.guard(client, session);
        return operations.operation(ACTOR, boundary(client, account, target), operation, instance,
                () -> active.containsKey(operation), guard).thenApply(receipt -> {
                    guard.run();
                    return receipt;
                });
    }

    /**
     * @param work 已持久认领的唯一工作
     */
    private void start(Work work) {
        CompletionStage<CommandResult> physical;
        try {
            physical = CompletableFuture.runAsync(() -> check(work), executor).thenCompose(ignored ->
                    clients.invoke(work.client, adapter -> adapter.resolve(work.account, work.target)
                            .thenCompose(resolved -> adapter.consoleLease(resolved, () -> check(work)))))
                    .thenCompose(lease -> {
                        check(work);
                        if (!lease.connection().equals(work.request.connection())) {
                            throw new IllegalStateException("连接已换代，请重新读取目录并明确提交新操作。");
                        }
                        var words = new TextTeaNekoContentPart(work.request.text()).toCommandArgs();
                        var command = Objects.requireNonNull(commands.getPrefixCommand(words[0]), "原命令已经不在目录。");
                        var source = new ConsoleInvocation(lease, () -> check(work), method -> {
                            // 2026/11/07：扩展子命令固定实际贡献者的插件代次。
                            var token = plugins.capture(command.target(method), method);
                            return () -> plugins.check(token);
                        });
                        work.source = source;
                        var data = CommandData.<ConsoleInvocation>builder().origin(CommandOrigin.MANAGEMENT)
                                .parentExecution(work.control).body(words[0]).args(Arrays.copyOfRange(words, 1, words.length))
                                .scope(lease.target().kind().equals("GROUP") ? CommandScope.GROUP : CommandScope.PRIVATE)
                                .scopeId(lease.target().scope()).permission(CommandPermission.MEMBER).senderId(ACTOR)
                                .clientClass(lease.clientClass()).rawData(source).build();
                        return operations.finish(ACTOR, work.boundary, work.request.operationId(), "RUNNING", "",
                                List.of(), List.of(), "原命令链已经准备启动，尚未证明实际业务或发送成功。", () -> check(work))
                                .thenCompose(saved -> {
                                    work.invoked = true;
                                    return dispatcher.dispatch(data, new ICommandErrorHandler() {}, help());
                                });
                    });
        } catch (RuntimeException failure) {
            physical = CompletableFuture.failedStage(failure);
        }
        physical.handle((result, failure) -> {
            var source = work.source;
            if (source != null) {
                // 业务链结束即撤销能力，回执事务尚未提交也不能允许迟到副作用。
                source.close();
            }
            var output = source == null ? List.<String>of() : source.output();
            var messages = source == null ? List.<String>of() : source.confirmed();
            // 2026/11/06：异常记录原操作编号和安全原因，未知结果不自动重放。
            if (failure != null) {
                LOG.warn("指令台执行未确认。操作={}，阶段={}，{}", work.request.operationId(),
                        work.invoked ? "原命令执行" : "前置检查",
                        org.zexnocs.teanekocore.logger.FailureDiagnostic.from(failure));
            }
            boolean complete = failure == null && result != null && Set.of(CommandResult.Status.COMPLETED,
                    CommandResult.Status.HELP).contains(result.status());
            String status = complete ? "COMPLETED" : failure == null || !work.invoked ? "REJECTED" : "INDETERMINATE";
            String code = failure == null && result != null ? result.status().name() : "UNCONFIRMED";
            // 已发生事实的保存不因浏览器退出而丢失，也不赋予任何新的执行权限。
            return operations.finish(ACTOR, work.boundary, work.request.operationId(), status, code, output, messages,
                    complete ? "原处理链已结束；只有已确认消息列表证明平台返回了相应消息号。"
                            : !work.invoked ? "前置检查拒绝，原命令尚未启动。请检查连接、账号、平台状态和本机白名单。"
                            : failure == null && result != null ? rejection(result.status())
                            : "处理结果未确认；" + org.zexnocs.teanekocore.logger.FailureDiagnostic.from(
                                    failure == null ? new IllegalStateException("命令返回值为空。") : failure).description()
                                    + " 已开始的外部动作不会自动重放。", () -> {});
        }).thenCompose(stage -> stage).whenComplete((receipt, failure) -> {
            try {
                if (failure != null) {
                    LOG.error("指令台终态保存失败。操作={}，{}；保留原编号查询，不自动重放。",
                            work.request.operationId(), org.zexnocs.teanekocore.logger.FailureDiagnostic.from(failure));
                }
            } finally {
                release(work);
            }
        });
    }

    /**
     * 2026/11/06：将命令分发拒绝码映射为可操作原因，不输出命令正文。
     * @param status 原分发终态
     * @return 固定原因
     */
    private static String rejection(CommandResult.Status status) {
        return switch (status) {
            case NOT_MATCHED, METHOD_NOT_FOUND -> "指令或子指令不存在，请按当前目录填写。";
            case DISABLED -> "该指令当前未启用。";
            case CANCELLED -> "指令已取消或执行资格已撤销。";
            case INVALID_ARGUMENTS -> "参数数量或格式错误，请查看指令参数说明。";
            case DATA_TYPE_MISMATCH -> "该指令未适配管理来源，不能在指令台运行。";
            case OUT_OF_SCOPE -> "该指令不适用于当前聊天类型或客户端。";
            case PERMISSION_DENIED -> "管理来源没有执行该指令的权限。";
            case HELP, COMPLETED -> "原命令处理链已结束。";
        };
    }

    /**
     * @param work 只用于重放已存在编号的新候选，不代表原工作仍在本进程
     * @return 原回执或安全失败阶段，防止同步装配失败遗留执行槽
     */
    private CompletionStage<ClientConsole.Receipt> lookup(Work work) {
        try {
            return operations.operation(ACTOR, work.boundary, work.request.operationId(), instance,
                    () -> active.get(work.request.operationId()) != work, work.platform);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedStage(failure);
        }
    }

    /**
     * @return 帮助输出仍使用明确上下文和声明，不能借 help 绕过来源门禁
     */
    private IHelpSubCommandHandler help() {
        return new IHelpSubCommandHandler() {
            /**
             * @param data 原管理来源
             * @param map 原命令目录
             * @param args 帮助参数
             * @return 明确说明已加入指令台输出
             */
            @Override
            public CompletionStage<Void> handleSubHelp(CommandData<?> data, CommandMapData map, String[] args) {
                Method method = map.getDefaultCommandMethod();
                if (args.length > 0 && map.getSubCommand(args[0]) != null) {
                    method = map.getSubCommand(args[0]).getSecond();
                }
                if (method == null || !(data.getRawData() instanceof ConsoleInvocation source)) {
                    return CompletableFuture.failedStage(new SecurityException("该帮助入口不支持管理来源。"));
                }
                ManagementCommandPolicy.check(data, method);
                var metadata = CommandMethodContract.metadata(method);
                return source.print(metadata.documented() ? metadata.description() : "该方法未提供使用说明。");
            }
        };
    }

    /**
     * @param work 原工作
     * @throws SecurityException 当生命周期、会话、连接前置或本机白名单已失效时
     */
    private void check(Work work) {
        work.control.checkActive();
        work.platform.run();
        var words = new TextTeaNekoContentPart(work.request.text()).toCommandArgs();
        if (closed || Instant.now().isAfter(work.deadline) || words.length == 0
                || !files.requireCurrent(WebSecurityFileConfig.class).snapshot().allowsConsolePrefix(words[0])) {
            throw new SecurityException("指令台生命周期或本机命令白名单已失效。");
        }
    }

    /**
     * @param work 原物理工作
     */
    private void release(Work work) {
        if (active.remove(work.request.operationId(), work)) {
            work.close();
            slots.release();
        }
    }

    /**
     * @param previous 已拥有原编号的工作
     * @param digest 当前完整请求摘要
     */
    private static void requireSame(Work previous, String digest) {
        if (!previous.digest.equals(digest)) {
            throw new IllegalArgumentException("指令操作编号已绑定其他主体、目标或参数。");
        }
    }

    /**
     * @param client 服务端已注册平台
     * @param account 已知账号引用
     * @param target 已知聊天引用
     * @return 无拼接歧义的固定目标摘要
     */
    private static String boundary(String client, String account, String target) {
        if (client.length() > 64 || account.length() > 256 || target.length() > 256) {
            throw new IllegalArgumentException("指令台目标标识超过上限。");
        }
        return StableDigest.sha256(client, account, target);
    }

    /** 停止新受理并撤销已有能力，不宣称物理 IO 已撤销。 */
    @PreDestroy
    public void close() {
        closed = true;
        active.values().forEach(Work::close);
    }

    /**
     * 当前进程拥有的工作，不输出控制、会话或正文。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @author zExNocs
     * @date 2026/11/03
     * @since 4.7.0-l
     * @version 4.7.0-l
     */
    private static final class Work {
        final String client;
        final String account;
        final String target;
        final String boundary;
        final String digest;
        final ClientConsole.Request request;
        final Runnable platform;
        final ExecutionControl control = ExecutionControl.create();
        final Instant deadline = Instant.now().plusSeconds(300);
        final CompletableFuture<ClientConsole.Receipt> accepted = new CompletableFuture<>();
        volatile @org.jspecify.annotations.Nullable ConsoleInvocation source;
        volatile boolean invoked;

        /**
         * @param client 原平台
         * @param account 原账号
         * @param target 原目标
         * @param boundary 原目标摘要
         * @param digest 完整请求摘要
         * @param request 原请求
         * @param platform 原会话和平台插件守卫
         */
        Work(String client, String account, String target, String boundary, String digest,
                ClientConsole.Request request, Runnable platform) {
            this.client = client;
            this.account = account;
            this.target = target;
            this.boundary = boundary;
            this.digest = digest;
            this.request = request;
            this.platform = platform;
        }

        /** 物理处理链结束后释放来源与原控制，不在 HTTP 返回时提前关闭。 */
        void close() {
            var current = source;
            if (current != null) {
                current.close();
            }
            control.close();
        }
    }
}
