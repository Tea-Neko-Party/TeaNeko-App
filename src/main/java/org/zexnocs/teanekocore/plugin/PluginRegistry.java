package org.zexnocs.teanekocore.plugin;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * 校验插件目录并提供逻辑启停；版本令牌拒绝禁用前调用的迟到结果。
 * 不卸载 Bean，不关闭借用资源，也不把禁用解释为副作用回滚。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/07：支持外层提供的依赖边、可诊断阻塞和统一目录修订号。
 * <br>2026/11/07：登记真实调用及传递依赖占用，撤门后等待实际异步工作排空。
 * <br>2026/11/07：独立登记宿主能力，业务插件从条件装配后的注解声明构建。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.0-l
 * @version 4.9.0-l
 */
@Service
public final class PluginRegistry {
    private final Map<String, PluginDescriptor> descriptors;
    private final Map<String, HostCapability> hosts;
    private final Map<String, Set<String>> clientRequirements;
    private final Map<String, Long> revisions = new HashMap<>();
    private final Set<String> disabled = new HashSet<>();
    // 2026/11/07：只保存外层发布的中立事实，不认识客户端或业务实现。
    private Map<String, Set<String>> additionalDependencies = Map.of();
    private Map<String, List<String>> unavailable = Map.of();
    private long catalogRevision = 1;
    // 2026/11/07：调用本身持有完成信号，外部取消观察不能提前移除在途占用。
    private final Set<Call> calls = new HashSet<>();

    /**
     * 固定完整目录，拒绝重名和依赖环；普通插件缺失依赖时保留可诊断目录。
     * @param modules 应用装配的插件入口
     * @throws IllegalArgumentException 当目录冲突或依赖无效时
     */
    public PluginRegistry(List<IPluginModule> modules) {
        this(modules, List.of());
    }

    /**
     * 2026/11/07：隔离装配程序化插件与独立宿主能力，生产入口使用注解目录。
     * @param modules 程序化业务声明
     * @param capabilities 宿主能力
     * @throws IllegalArgumentException 当身份冲突或宿主依赖无效时
     */
    public PluginRegistry(List<IPluginModule> modules, List<HostCapability> capabilities) {
        this(modules.stream().map(module -> new PluginDeclarationScanner.Declaration(
                Objects.requireNonNull(module.descriptor(), "插件声明不能为空。"), Set.of())).toList(), capabilities, false);
    }

    /**
     * 2026/11/07：容器仅解析已通过条件装配的注解；发现不等于业务启用。
     * @param declarations 完整静态插件目录
     * @param capabilities 不可启停的宿主能力
     */
    @org.springframework.beans.factory.annotation.Autowired
    public PluginRegistry(PluginDeclarationScanner declarations, List<HostCapability> capabilities) {
        this(declarations.declarations(), capabilities, true);
    }

    /**
     * 2026/11/07：统一校验两类身份，宿主依赖只允许指向其他宿主能力。
     * @param declarations 业务插件声明
     * @param capabilities 宿主能力声明
     * @param managed 是否由外层生命周期开启业务插件
     */
    private PluginRegistry(List<PluginDeclarationScanner.Declaration> declarations,
                           List<HostCapability> capabilities, boolean managed) {
        var values = new TreeMap<String, PluginDescriptor>();
        var clients = new TreeMap<String, Set<String>>();
        for (var declaration : declarations) {
            var value = declaration.descriptor();
            if (values.putIfAbsent(value.id(), value) != null) {
                throw new IllegalArgumentException("插件标识重复：" + value.id());
            }
            clients.put(value.id(), declaration.requiredClients());
        }
        descriptors = Collections.unmodifiableMap(values);
        clientRequirements = Map.copyOf(clients);
        var hostValues = new TreeMap<String, HostCapability>();
        for (var capability : capabilities) {
            if (values.containsKey(capability.id()) || hostValues.putIfAbsent(capability.id(), capability) != null) {
                throw new IllegalArgumentException("插件或宿主能力标识重复：" + capability.id());
            }
        }
        hosts = Map.copyOf(hostValues);
        for (var capability : hosts.values()) {
            if (!hosts.keySet().containsAll(capability.dependencies())) {
                throw new IllegalArgumentException("宿主能力缺少必需宿主依赖：" + capability.id());
            }
        }
        var identities = new TreeSet<>(values.keySet());
        identities.addAll(hosts.keySet());
        for (var id : identities) {
            visit(id, new HashSet<>(), new HashSet<>());
            revisions.put(id, 1L);
        }
        for (var descriptor : values.values()) {
            if (descriptor.mandatory() && !identities.containsAll(descriptor.dependencies())) {
                throw new IllegalArgumentException("宿主基础模块缺少必需依赖：" + descriptor.id());
            }
        }
        if (managed) {
            descriptors.values().stream().filter(descriptor -> !descriptor.mandatory())
                    .forEach(descriptor -> setEnabled(descriptor.id(), false));
        }
    }

    /**
     * 深度检查必需依赖及循环。
     * @param id 当前插件
     * @param visiting 当前路径
     * @param visited 已验证节点
     * @throws IllegalArgumentException 当依赖缺失或成环时
     */
    private void visit(String id, Set<String> visiting, Set<String> visited) {
        if (visited.contains(id)) { return; }
        if (!descriptors.containsKey(id) && !hosts.containsKey(id)) {
            return;
        }
        if (!visiting.add(id)) {
            throw new IllegalArgumentException("插件依赖成环：" + id);
        }
        dependencies(id).forEach(dependency -> visit(dependency, visiting, visited));
        visiting.remove(id); visited.add(id);
    }

    /**
     * 导出稳定目录及当前有效状态，依赖禁用时消费者同样不可用。
     * @return 不可变管理快照
     */
    public synchronized List<View> list() {
        return descriptors.values().stream().map(value -> new View(value, isEnabled(value.id()), revisions.get(value.id()))).toList();
    }

    /**
     * 查询包含依赖在内的有效开关。
     * @param id 插件标识
     * @return 插件及全部依赖可用时为真
     */
    public synchronized boolean isEnabled(String id) {
        // 2026/11/07：宿主能力在装配时已验证依赖，不参与业务插件开关。
        if (hosts.containsKey(id)) {
            return true;
        }
        var descriptor = descriptors.get(id);
        return descriptor != null && !disabled.contains(id) && unavailable.getOrDefault(id, List.of()).isEmpty()
                && dependencies(id).stream().allMatch(this::isEnabled);
    }

    /**
     * 修改逻辑路由，重启用不会恢复旧令牌；资源仍由原 Bean 拥有者释放。
     * @param id 已注册插件
     * @param enabled 是否启用
     * @throws IllegalArgumentException 当插件未注册时
     */
    public synchronized void setEnabled(String id, boolean enabled) {
        descriptor(id);
        if (!enabled && descriptors.get(id).mandatory()) { throw new IllegalArgumentException("宿主基础模块不能通过插件开关关闭：" + id); }
        boolean changed = enabled ? disabled.remove(id) : disabled.add(id);
        if (changed) {
            revisions.compute(id, (key, revision) -> Objects.requireNonNull(revision) + 1);
            catalogRevision++;
        }
    }

    /**
     * 提供静态声明，生命周期协调器不能以查询代替资源启动。
     * @param id 插件标识
     * @return 已登记声明
     */
    public PluginDescriptor descriptor(String id) {
        var value = descriptors.get(id);
        if (value == null) {
            throw new IllegalArgumentException("业务插件未注册，宿主能力不能作为插件管理：" + id);
        }
        return value;
    }

    /**
     * 2026/11/07：区分稳定宿主身份与可启停插件，不扫描类型。
     * @param id 资源归属或依赖身份
     * @return 是否为已登记宿主能力
     */
    public boolean isHostCapability(String id) {
        return hosts.containsKey(id);
    }

    /**
     * 2026/11/07：提供注解声明的客户端先决条件，由 App 解析实际安装事实。
     * @param id 业务插件标识
     * @return 不可变客户端 ID 集合
     */
    public Set<String> requiredClients(String id) {
        descriptor(id);
        return clientRequirements.getOrDefault(id, Set.of());
    }

    /**
     * 返回含外层能力提供者边的依赖，不在查询时扫描客户端。
     * @param id 插件标识
     * @return 固定依赖集合
     */
    public synchronized Set<String> dependencies(String id) {
        requireRegistered(id);
        if (hosts.containsKey(id)) {
            return hosts.get(id).dependencies();
        }
        var result = new TreeSet<>(descriptors.get(id).dependencies());
        result.addAll(additionalDependencies.getOrDefault(id, Set.of()));
        return Collections.unmodifiableSet(result);
    }

    /**
     * 返回统一目录代次，管理预览在任何启停或依赖事实改变后失效。
     * @return 当前目录修订号
     */
    public synchronized long revision() {
        return catalogRevision;
    }

    /**
     * 发布外层已经完成发现的依赖事实，整图验证成功后才修改调用资格。
     * @param dependencies 能力提供者产生的额外必需边
     * @param reasons 非插件依赖的不可用原因
     * @throws IllegalArgumentException 当归属未知或依赖环存在时
     */
    public synchronized void publishAvailability(Map<String, Set<String>> dependencies,
                                                 Map<String, List<String>> reasons) {
        var nextEdges = new TreeMap<String, Set<String>>();
        dependencies.forEach((id, values) -> {
            descriptor(id);
            values.forEach(PluginDescriptor::requireId);
            nextEdges.put(id, Set.copyOf(values));
        });
        var nextReasons = new TreeMap<String, List<String>>();
        reasons.forEach((id, values) -> {
            descriptor(id);
            nextReasons.put(id, List.copyOf(values));
        });
        var previous = additionalDependencies;
        additionalDependencies = Map.copyOf(nextEdges);
        try {
            for (var id : descriptors.keySet()) {
                visit(id, new HashSet<>(), new HashSet<>());
            }
        } catch (RuntimeException failure) {
            additionalDependencies = previous;
            throw failure;
        }
        boolean changed = false;
        for (var id : descriptors.keySet()) {
            if (!previous.getOrDefault(id, Set.of()).equals(nextEdges.getOrDefault(id, Set.of()))
                    || !unavailable.getOrDefault(id, List.of()).equals(nextReasons.getOrDefault(id, List.of()))) {
                revisions.compute(id, (key, value) -> Objects.requireNonNull(value) + 1);
                changed = true;
            }
        }
        unavailable = Map.copyOf(nextReasons);
        if (changed) {
            catalogRevision++;
        }
    }

    /**
     * 读取静态缺失与上层资格原因，停用原因由生命周期视图补充。
     * @param id 插件标识
     * @return 不含凭据的阻塞原因
     */
    public synchronized List<String> blockers(String id) {
        requireRegistered(id);
        var result = new ArrayList<>(unavailable.getOrDefault(id, List.of()));
        for (var dependency : dependencies(id)) {
            if (!descriptors.containsKey(dependency) && !hosts.containsKey(dependency)) {
                result.add("缺少依赖插件：" + dependency);
            }
        }
        return List.copyOf(result);
    }

    /**
     * 按必需提供者优先返回完整顺序，停用时使用其逆序。
     * @return 已安装插件的拓扑顺序
     */
    public synchronized List<String> activationOrder() {
        var result = new ArrayList<String>();
        var visited = new HashSet<String>();
        for (var id : descriptors.keySet()) {
            appendOrder(id, visited, result);
        }
        return List.copyOf(result);
    }

    /**
     * 收集已安装依赖，不将缺失插件伪装成目录成员。
     * @param id 当前标识
     * @param visited 已收集标识
     * @param result 当前有序结果
     */
    private void appendOrder(String id, Set<String> visited, List<String> result) {
        if (descriptors.containsKey(id) && visited.add(id)) {
            dependencies(id).forEach(value -> appendOrder(value, visited, result));
            result.add(id);
        }
    }

    /**
     * 捕获本插件及全部必需依赖的版本，不受无关插件变化影响。
     * @param id 调用所属插件
     * @return 不可变调用令牌
     * @throws IllegalStateException 当插件当前不可用时
     */
    public synchronized Token capture(String id) {
        requireRegistered(id);
        if (!isEnabled(id)) { throw new IllegalStateException("插件或依赖已禁用：" + id); }
        var selected = new TreeMap<String, Long>(); collect(id, selected);
        return new Token(selected);
    }

    /**
     * 2026/11/07：在同一锁内捕获资格并登记占用，关闭路由后不能再加入新调用。
     * @param id 调用所属插件
     * @return 须跟随真实工作阶段释放的调用占用
     * @throws IllegalStateException 当插件或依赖不再可用时
     */
    public synchronized Call open(String id) {
        var call = new Call(capture(id));
        calls.add(call);
        return call;
    }

    /**
     * 2026/11/07：撤门后等待已占用本插件的调用，包括以其为传递依赖的消费者。
     * 调用方须先关闭目标路由；本方法只等待真实终态，不取消业务或关闭借用资源。
     * @param id 已撤销新调用资格的插件
     * @return 独立只读排空阶段，取消外部观察不影响实际排空
     */
    public synchronized CompletionStage<Void> drain(String id) {
        requireRegistered(id);
        return CompletableFuture.allOf(calls.stream().filter(call -> call.token.revisions().containsKey(id))
                .map(call -> call.finished).toArray(CompletableFuture<?>[]::new)).minimalCompletionStage();
    }

    /**
     * 收集传递依赖版本。
     * @param id 插件标识
     * @param selected 已收集版本
     */
    private void collect(String id, Map<String, Long> selected) {
        if (selected.putIfAbsent(id, revisions.get(id)) == null) {
            dependencies(id).forEach(dependency -> collect(dependency, selected));
        }
    }

    /**
     * 在实际执行、提交或交付前复核固定版本。
     * @param token 原调用令牌
     * @throws IllegalStateException 当插件或依赖关闭、换代时
     */
    public synchronized void check(Token token) {
        token.revisions().forEach((id, revision) -> {
            if (!isEnabled(id) || !revision.equals(revisions.get(id))) { throw new IllegalStateException("插件调用版本已失效：" + id); }
        });
    }

    /**
     * 2026/11/07：等待真实业务完成并复核结果版本，同一调用占用覆盖全部异步工作。
     * @param id 所属插件
     * @param action 业务阶段工厂
     * @param <T> 结果类型
     * @return 原业务的可组合完成阶段
     */
    public <T> CompletionStage<T> invoke(String id, Supplier<CompletionStage<T>> action) {
        final Call call;
        try {
            call = open(id);
        } catch (RuntimeException failure) {
            return CompletableFuture.failedStage(failure);
        }
        boolean tracked = false;
        try {
            var result = call.track(Objects.requireNonNull(action.get(), "插件不能返回空阶段。"));
            tracked = true;
            return result;
        } catch (RuntimeException failure) {
            return CompletableFuture.failedStage(failure);
        } finally {
            if (!tracked) {
                call.close();
            }
        }
    }

    /**
     * 一次实际调用的版本与资源占用，异步工作由完成回调释放，不能由观察者取消代替。
     * <br>2026/11/07：命令和事件沿用各自完成合同接入统一插件排空。
     *
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    public final class Call implements AutoCloseable {
        private final Token token;
        private final CompletableFuture<Void> finished = new CompletableFuture<>();
        private final java.util.concurrent.atomic.AtomicBoolean tracked = new java.util.concurrent.atomic.AtomicBoolean();

        /**
         * @param token 在登记占用的同一锁内捕获的有效版本
         */
        private Call(Token token) {
            this.token = token;
        }

        /** @return 本次调用固定的插件及依赖代次 */
        public Token token() {
            return token;
        }

        /**
         * 复核当前调用仍具有原资格。
         * @throws IllegalStateException 当任一所需插件已关闭或换代时
         */
        public void check() {
            PluginRegistry.this.check(token);
        }

        /**
         * 等待真实阶段结束，独立完成观察结果，外部取消或手动完成观察不会撤销清理回调。
         * @param work 实际业务完成阶段，只能登记一次
         * @param <T> 业务结果类型
         * @return 实际结束并复核版本后的只读结果
         * @throws IllegalStateException 当同一调用重复登记工作时
         */
        public <T> CompletionStage<T> track(CompletionStage<T> work) {
            Objects.requireNonNull(work, "插件实际工作阶段不能为空。");
            if (!tracked.compareAndSet(false, true)) {
                throw new IllegalStateException("同一插件调用只能登记一个实际完成阶段。");
            }
            var result = new CompletableFuture<T>();
            work.whenComplete((value, failure) -> {
                Throwable outcome = failure;
                if (outcome == null) {
                    try {
                        check();
                    } catch (RuntimeException invalid) {
                        outcome = invalid;
                    }
                }
                // 真实业务已经结束，先释放占用再通知观察者，避免观察回调等待自身排空。
                close();
                if (outcome == null) {
                    result.complete(value);
                } else {
                    result.completeExceptionally(outcome);
                }
            });
            return result.minimalCompletionStage();
        }

        /** 同步启动失败或真实异步阶段已结束时释放占用；重复释放不影响其他调用。 */
        @Override
        public void close() {
            final boolean removed;
            synchronized (PluginRegistry.this) {
                removed = calls.remove(this);
            }
            if (removed) {
                finished.complete(null);
            }
        }
    }

    /**
     * 验证目标属于已装配插件。
     * @param id 插件标识
     * @throws IllegalArgumentException 当插件未知时
     */
    public void requireRegistered(String id) {
        if (!descriptors.containsKey(id) && !hosts.containsKey(id)) {
            throw new IllegalArgumentException("插件或宿主能力未注册：" + id);
        }
    }

    /**
     * 调用采用的插件与依赖版本，只包含不可变业务数据。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param revisions 固定修订号
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.6.0-l
     * @version 4.6.0-l
     */
    public record Token(Map<String, Long> revisions) {
        /**
         * 防止外部修改令牌。
         * @param revisions 版本映射
         */
        public Token { revisions = Map.copyOf(revisions); }
    }

    /**
     * 脱离运行句柄的插件管理视图。
     * <br>2026/11/04：校正 @since 为首次引入版本。
     *
     * @param descriptor 静态声明
     * @param enabled 包含依赖的有效状态
     * @param revision 当前逻辑路由修订号
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.6.0-l
     * @version 4.6.0-l
     */
    public record View(PluginDescriptor descriptor, boolean enabled, long revision) {}
}
