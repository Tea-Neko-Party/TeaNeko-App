package org.zexnocs.teanekoapp.web;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoapp.web.client.ClientManagementDirectory;
import org.zexnocs.teanekoapp.web.client.ManagementTargets;
import org.zexnocs.teanekoapp.web.error.LocalResourceNotAllowedException;
import org.zexnocs.teanekocore.actuator.execution.ExecutionBusyException;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ResourceBudget;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;
import org.zexnocs.teanekocore.plugin.PluginRegistry;
import org.zexnocs.teanekocore.plugin.management.IGlobalManagementResource;
import org.zexnocs.teanekocore.plugin.management.IManagementAuthority;
import org.zexnocs.teanekocore.plugin.management.ManagementContext;
import org.zexnocs.teanekocore.plugin.management.ManagementGateway;
import org.zexnocs.teanekocore.plugin.management.IManagementResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

/**
 * 将真实 Web 会话映射到原管理网关，只授权本次服务器持有的上下文与当前本地管理策略。
 * 在途能力随物理阶段释放，浏览器断开不被当作事务已回滚。
 * <br>2026/11/03：实例级资源使用独立入口，继续复用原管理网关与在途授权。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/06：限制每资源在途并分离等待截止和物理回收，关闭时撤销执行资格。
 * <br>2026/11/07：同步阻塞也受等待期限约束，实际阶段和逻辑结果都结束后回收。
 * <br>2026/11/07：默认开放已注册资源，显式限制的在途撤权保留独立错误类型。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.9.0-l
 */
@Service
public final class WebManagementCalls implements IManagementAuthority, AutoCloseable {
    private final ManagementGateway gateway;
    private final ClientManagementDirectory clients;
    private final PluginRegistry plugins;
    private final IFileConfigService files;
    private final ObjectMapper mapper;
    private final Map<String, IManagementResource<?, ?>> resources;
    private final Map<UUID, ManagementContext> active = new ConcurrentHashMap<>();
    // 2026/11/06：物理占用包含目标解析及资源阶段，逻辑超时不提前归还。
    private final Map<UUID, Work> physical = new HashMap<>();
    private final ScheduledExecutorService scheduler;
    private final ExecutorService workers;
    private boolean closed;

    /**
     * @param gateway 原显式授权网关
     * @param clients 原目标解析目录
     * @param plugins 原插件代次
     * @param files 本地白名单
     * @param mapper 原数据转换器
     * @param contributions 已声明的类型化资源
     * @param scheduler 借用的截止时间调度器
     * @param workers 借用的执行器，隔离扩展在返回阶段前的同步阻塞
     */
    public WebManagementCalls(ManagementGateway gateway, ClientManagementDirectory clients, PluginRegistry plugins,
                              IFileConfigService files, ObjectMapper mapper,
                              List<IManagementResource<?, ?>> contributions,
                              @Qualifier("taskExecutor") ScheduledExecutorService scheduler,
                              @Qualifier("virtualExecutor") ExecutorService workers) {
        this.scheduler = scheduler;
        this.workers = workers;
        this.gateway = gateway;
        this.clients = clients;
        this.plugins = plugins;
        this.files = files;
        this.mapper = mapper;
        var registered = new HashMap<String, IManagementResource<?, ?>>();
        for (var resource : contributions) {
            if (registered.putIfAbsent(resource.descriptor().key(), resource) != null) {
                throw new IllegalArgumentException("Web 管理资源标识重复。");
            }
        }
        resources = Map.copyOf(registered);
    }

    /**
     * @param client 已注册平台
     * @param account 已知账号引用
     * @param target 已知聊天引用
     * @param key 已声明资源 ID
     * @param body 只能绑定该资源明确请求类型的正文
     * @param session 当前真实会话能力
     * @return 原网关的实际业务结果
     */
    public CompletionStage<?> invoke(String client, String account, String target, String key,
                                     JsonNode body, Runnable session) {
        var resource = resources.get(key);
        // 2026/11/07：区分未注册资源与显式白名单拒绝，默认开放不放行未知入口。
        if (resource == null || resource instanceof IGlobalManagementResource<?, ?>) {
            throw new SecurityException("该管理资源未注册或不属于聊天范围。");
        }
        if (!allowed(key)) {
            throw new LocalResourceNotAllowedException();
        }
        if (!body.isObject() || mapper.writeValueAsString(body).length() > 65536) {
            throw new IllegalArgumentException("管理请求必须是有界对象。");
        }
        Object request = mapper.treeToValue(body, resource.requestType());
        var platform = clients.guard(client, session);
        // 2026/11/03：目标解析之后复用同一在途授权，不复制全局管理网关。
        return execute(resource, request, () -> clients.invoke(client, adapter -> adapter.resolve(account, target)
            .thenApply(resolved -> ManagementTargets.encode(client, resolved.scope()))), platform);
    }

    /**
     * 2026/11/03：只接受明确声明全局作用域的资源。
     * @param key 已声明实例级资源
     * @param body 该资源的明确请求
     * @param session 当前会话
     * @return 原管理网关实际阶段
     */
    public CompletionStage<?> invokeGlobal(String key, JsonNode body, Runnable session) {
        session.run();
        var resource = resources.get(key);
        // 2026/11/07：默认开放已注册实例资源，显式限制使用独立诊断。
        if (!(resource instanceof IGlobalManagementResource<?, ?>)) {
            throw new SecurityException("该实例级资源未注册。");
        }
        if (!allowed(key)) {
            throw new LocalResourceNotAllowedException();
        }
        if (!body.isObject() || mapper.writeValueAsString(body).length() > 65536) {
            throw new IllegalArgumentException("管理请求必须是有界对象。");
        }
        return execute(resource, mapper.treeToValue(body, resource.requestType()),
            () -> CompletableFuture.completedStage("global"), session);
    }

    /**
     * @param resource 原注册资源
     * @param request 原类型化请求
     * @param scope 受准入和截止约束的目标解析
     * @param platform 当前会话及可选平台守卫
     * @return 有界等待；物理阶段实际结束后才释放容量
     */
    private CompletionStage<?> execute(IManagementResource<?, ?> resource, Object request,
                                       Supplier<CompletionStage<String>> scope, Runnable platform) {
        String key = resource.descriptor().key();
        var policy = files.requireCurrent(WebManagementFileConfig.class).snapshot();
        UUID id = UUID.randomUUID();
        Work work;
        synchronized (physical) {
            if (closed || physical.size() >= policy.maxInFlight()
                || physical.values().stream().filter(value -> value.resource().equals(key)).count() >= policy.maxPerResource()) {
                return CompletableFuture.failedStage(new ExecutionBusyException("管理操作繁忙或正在关闭，请保留原编号稍后查询。"));
            }
            var control = ExecutionControl.withDeadline(new ResourceBudget(Map.of()),
                Instant.now().plusMillis(policy.timeoutMillis()), Clock.systemUTC(), scheduler);
            work = new Work(key, Instant.now(), control);
            physical.put(id, work);
        }
        var control = work.control();
        CompletionStage<?> source;
        try {
            // 2026/11/06：同步阻塞同样计入物理占用，HTTP 等待不依赖扩展及时返回 stage。
            source = CompletableFuture.completedFuture(null).thenComposeAsync(ignored -> {
                control.checkActive();
                var token = plugins.capture(resource.descriptor().pluginId());
                Runnable checked = () -> {
                    control.checkActive();
                    platform.run();
                    plugins.check(token);
                    if (!allowed(key)) {
                        throw new LocalResourceNotAllowedException();
                    }
                };
                checked.run();
                return scope.get().thenCompose(resolved -> {
                    checked.run();
                    var context = new ManagementContext("web.local-admin", resolved, id, control, checked);
                    active.put(id, context);
                    return gateway.executePhysical(key, context, request);
                });
            }, workers);
        } catch (RuntimeException failure) {
            source = CompletableFuture.failedStage(failure);
        }
        // 2026/11/07：CompletableFuture 的兄弟回调没有注册顺序保证；两种结束都观察到后才能关闭控制。
        var waiting = control.guard(source);
        CompletableFuture.allOf(source.handle((value, failure) -> null).toCompletableFuture(),
                waiting.handle((value, failure) -> null).toCompletableFuture()).whenComplete((ignored, failure) -> {
            active.remove(id);
            synchronized (physical) {
                physical.remove(id);
            }
            control.close();
        });
        return waiting;
    }

    /** @return 不含主体、目标和请求正文的物理占用及年龄，供管理员定位挂起资源 */
    public List<InFlight> inFlight() {
        synchronized (physical) {
            return physical.entrySet().stream().map(entry -> new InFlight(entry.getKey(), entry.getValue().resource(),
                Duration.between(entry.getValue().started(), Instant.now()).toMillis(),
                entry.getValue().control().termination().toCompletableFuture().isDone())).toList();
        }
    }

    /** 关闭后拒绝新调用并撤销在途权限；不声称物理写入已经停止或回滚。 */
    @Override
    public void close() {
        List<Work> remaining;
        synchronized (physical) {
            closed = true;
            remaining = List.copyOf(physical.values());
        }
        remaining.forEach(work -> work.control().close());
    }

    /**
     * 单个资源的物理占用，不进入序列化视图。
     * @param resource 已注册资源
     * @param started 准入时间
     * @param control 本次控制
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.7-l
     * @version 4.8.7-l
     */
    private record Work(String resource, Instant started, ExecutionControl control) {}

    /**
     * 管理员可见的运行边界，不包含业务输入。
     * @param requestId 宿主调用标识，与业务操作编号分开
     * @param resource 资源标识
     * @param ageMillis 实际占用毫秒数
     * @param waitingEnded 等待是否已截止或关闭，物理阶段仍未完成
     * @author zExNocs
     * @date 2026/11/06
     * @since 4.8.7-l
     * @version 4.8.7-l
     */
    public record InFlight(UUID requestId, String resource, long ageMillis, boolean waitingEnded) {}

    /**
     * @param context 必须是本入口本次持有的同一对象
     * @param resource 原网关的固定声明
     * @return 明确通过或拒绝，不根据字符串主体自动授予权限
     */
    @Override
    public CompletionStage<Void> authorize(ManagementContext context, IManagementResource.Descriptor resource) {
        try {
            // 2026/11/07：区分伪造上下文与本地策略收紧，返回阶段也保持准确错误码。
            if (active.get(context.requestId()) != context) {
                throw new SecurityException("未持有当前管理调用授权。");
            }
            if (!allowed(resource.key())) {
                throw new LocalResourceNotAllowedException();
            }
            context.checkActive();
            return CompletableFuture.completedStage(null);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedStage(failure);
        }
    }

    /**
     * @param key 原资源键
     * @return 当前本地明确允许
     */
    private boolean allowed(String key) {
        // 2026/11/07：管理员默认开放注册资源，显式白名单仍支持运行期撤销。
        return files.requireCurrent(WebSecurityFileConfig.class).snapshot().allowsManagementResource(key);
    }
}
