package org.zexnocs.teanekocore.plugin;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * 校验插件目录并提供逻辑启停；版本令牌拒绝禁用前调用的迟到结果。
 * 不卸载 Bean，不关闭借用资源，也不把禁用解释为副作用回滚。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.6.0-l
 */
@Service
public final class PluginRegistry {
    private final Map<String, PluginDescriptor> descriptors;
    private final Map<String, Long> revisions = new HashMap<>();
    private final Set<String> disabled = new HashSet<>();

    /**
     * 固定完整目录，先校验重名、缺失依赖及依赖环，失败不发布部分目录。
     * @param modules 应用装配的插件入口
     * @throws IllegalArgumentException 当目录冲突或依赖无效时
     */
    public PluginRegistry(List<PluginModule> modules) {
        var values = new TreeMap<String, PluginDescriptor>();
        for (var module : modules) {
            var value = Objects.requireNonNull(module.descriptor(), "插件声明不能为空。");
            if (values.putIfAbsent(value.id(), value) != null) { throw new IllegalArgumentException("插件标识重复：" + value.id()); }
        }
        descriptors = Collections.unmodifiableMap(values);
        for (var id : values.keySet()) { visit(id, new HashSet<>(), new HashSet<>()); revisions.put(id, 1L); }
    }

    /**
     * 根据启动配置安装初始禁用列表；运行期开关仍为进程内状态。
     * @param modules 插件入口
     * @param environment 宿主配置，不写回文件
     */
    @org.springframework.beans.factory.annotation.Autowired
    public PluginRegistry(List<PluginModule> modules, org.springframework.core.env.Environment environment) {
        this(modules);
        org.springframework.boot.context.properties.bind.Binder.get(environment)
                .bind("tea-neko.plugins.disabled", org.springframework.boot.context.properties.bind.Bindable.listOf(String.class))
                .orElse(List.of()).forEach(id -> setEnabled(id, false));
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
        var descriptor = descriptors.get(id);
        if (descriptor == null || !visiting.add(id)) { throw new IllegalArgumentException("插件依赖缺失或成环：" + id); }
        descriptor.dependencies().forEach(dependency -> visit(dependency, visiting, visited));
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
        var descriptor = descriptors.get(id);
        return descriptor != null && !disabled.contains(id) && descriptor.dependencies().stream().allMatch(this::isEnabled);
    }

    /**
     * 修改逻辑路由，重启用不会恢复旧令牌；资源仍由原 Bean 拥有者释放。
     * @param id 已注册插件
     * @param enabled 是否启用
     * @throws IllegalArgumentException 当插件未注册时
     */
    public synchronized void setEnabled(String id, boolean enabled) {
        requireRegistered(id);
        if (!enabled && descriptors.get(id).mandatory()) { throw new IllegalArgumentException("宿主基础模块不能通过插件开关关闭：" + id); }
        boolean changed = enabled ? disabled.remove(id) : disabled.add(id);
        if (changed) { revisions.compute(id, (key, revision) -> Objects.requireNonNull(revision) + 1); }
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
     * 收集传递依赖版本。
     * @param id 插件标识
     * @param selected 已收集版本
     */
    private void collect(String id, Map<String, Long> selected) {
        if (selected.putIfAbsent(id, revisions.get(id)) == null) {
            descriptors.get(id).dependencies().forEach(dependency -> collect(dependency, selected));
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
     * 等待真实业务完成并复核结果版本，保留原异步失败。
     * @param id 所属插件
     * @param action 业务阶段工厂
     * @param <T> 结果类型
     * @return 原业务的可组合完成阶段
     */
    public <T> CompletionStage<T> invoke(String id, Supplier<CompletionStage<T>> action) {
        try {
            var token = capture(id);
            return Objects.requireNonNull(action.get(), "插件不能返回空阶段。").thenApply(value -> { check(token); return value; });
        } catch (RuntimeException failure) { return CompletableFuture.failedStage(failure); }
    }

    /**
     * 验证目标属于已装配插件。
     * @param id 插件标识
     * @throws IllegalArgumentException 当插件未知时
     */
    public void requireRegistered(String id) {
        if (!descriptors.containsKey(id)) { throw new IllegalArgumentException("插件未注册：" + id); }
    }

    /**
     * 调用采用的插件与依赖版本，只包含不可变业务数据。
     * @param revisions 固定修订号
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.0.0
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
     * @param descriptor 静态声明
     * @param enabled 包含依赖的有效状态
     * @param revision 当前逻辑路由修订号
     * @author zExNocs
     * @date 2026/11/02
     * @since 4.0.0
     * @version 4.6.0-l
     */
    public record View(PluginDescriptor descriptor, boolean enabled, long revision) {}
}
