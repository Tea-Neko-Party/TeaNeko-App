package org.zexnocs.teanekocore.plugin;

import org.jspecify.annotations.NonNull;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.jspecify.annotations.Nullable;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 将既有扫描式命令、事件和任务阶段接入插件归属，不改变未声明归属的旧扩展。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/06：提供按缓存插件标识读取当前启用状态的入口。
 * <br>2026/11/07：允许已冻结归属直接捕获有效代次，任务执行不重复发现注解。
 * <br>2026/11/07：原业务目录显式登记方法归属，运行读取未登记目标时明确拒绝。
 * <br>2026/11/07：登记时校验业务包与插件身份，缺失归属诊断包含安全方法定位。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.0-l
 * @version 4.9.0-l
 */
@Component
public final class PluginAccess {
    private final PluginRegistry registry;
    // 2026/11/07：目标身份和方法由原业务扫描器预登记，消息路径只查固定归属。
    private final Map<Object, Map<Method, Optional<String>>> owners = new IdentityHashMap<>();
    /**
     * 借用统一目录。
     * @param registry 插件目录
     */
    public PluginAccess(PluginRegistry registry) {
        this.registry = registry;
    }
    /**
     * 初始化或显式重扫时编译归属，方法声明优先于代理目标类型声明。
     * @param target 运行 Bean
     * @param method 实际方法
     * @return 插件 ID，未声明时为空
     */
    public synchronized @Nullable String register(Object target, Method method) {
        var type = AopUtils.getTargetClass(target);
        var specific = AopUtils.getMostSpecificMethod(method, type);
        var annotation = AnnotatedElementUtils.findMergedAnnotation(specific, PluginOwned.class);
        if (annotation == null) {
            annotation = AnnotatedElementUtils.findMergedAnnotation(type, PluginOwned.class);
        }
        String owner = annotation == null ? null : annotation.value();
        if (owner != null) {
            // 2026/11/07：宿主原生能力无业务插件归属，不能借注解绕过包与身份边界。
            PluginDeclarationScanner.requirePluginPackage(type);
            if (AnnotatedElementUtils.hasAnnotation(specific, PluginOwned.class)) {
                PluginDeclarationScanner.requirePluginPackage(specific.getDeclaringClass());
            }
            registry.descriptor(owner);
        }
        owners.computeIfAbsent(target, ignored -> new HashMap<>()).put(method, Optional.ofNullable(owner));
        return owner;
    }

    /**
     * 读取已经编译的归属，不在请求、权限复核或页面查询时发现注解。
     * @param target 已登记的容器对象
     * @param method 已登记的方法
     * @return 插件标识，显式登记为无归属时为空
     * @throws IllegalStateException 当调用方法尚未经过初始化登记时
     */
    public synchronized @NonNull String owner(Object target, Method method) {
        var methods = owners.get(target);
        var compiled = methods == null ? Optional.<String>empty() : methods.get(method);
        if (compiled.isEmpty()) {
            // 2026/11/07：仅记录静态目标和方法名，禁止带入调用参数或敏感内容。
            throw new IllegalStateException("插件调用归属尚未登记，请完成对应目录的初始化："
                    + target.getClass().getName() + "#" + method.getName());
        }
        return compiled.orElse(null);
    }
    /**
     * 检查声明者已注册并捕获有效版本。
     * @param target 目标 Bean
     * @param method 实际方法
     * @return 无归属时为空，否则返回当前令牌
     */
    public PluginRegistry.@Nullable Token capture(Object target, Method method) {
        var owner = owner(target, method);
        return owner == null ? null : registry.capture(owner);
    }

    /**
     * 2026/11/07：为已登记方法开始实际调用，占用覆盖其所属插件和全部必需依赖。
     * @param target 原容器对象
     * @param method 已完成归属登记的方法
     * @return 有插件归属时返回实际调用占用，宿主旧扩展为空
     */
    public PluginRegistry.@Nullable Call open(Object target, Method method) {
        var owner = owner(target, method);
        return owner == null ? null : registry.open(owner);
    }
    /**
     * 复核先前捕获的版本。
     * @param token 可选调用令牌
     */
    public void check(PluginRegistry.@Nullable Token token) {
        if (token != null) {
            registry.check(token);
        }
    }
    /**
     * 2026/11/06：业务目录缓存声明者后，仅复核插件的当前资格，不重新发现注解。
     * @param owner 初始化时解析的插件标识
     * @return 插件当前已启用
     */
    public boolean enabledOwner(String owner) {
        registry.requireRegistered(owner);
        return registry.isEnabled(owner);
    }

    /**
     * 2026/11/07：为已冻结归属捕获当前代次，不重新解析目标或注解。
     * @param owner 扫描或显式注册时固定的插件标识
     * @return 当前有效代次
     */
    public PluginRegistry.Token captureOwner(String owner) {
        return registry.capture(owner);
    }

    /**
     * 2026/11/07：为低频装配中已固定归属的独立业务开启真实占用，不重新发现注解。
     * @param owner 已确认的插件标识
     * @return 必须跟随实际业务完成阶段释放的调用占用
     */
    public PluginRegistry.Call openOwner(String owner) {
        return registry.open(owner);
    }

    /**
     * 判断监听器是否应该参与当前分发。
     * @param target 目标 Bean
     * @param method 实际方法
     * @return 未声明归属或插件有效时为真
     */
    public boolean enabled(Object target, Method method) {
        var owner = owner(target, method);
        if (owner == null) {
            return true;
        }
        registry.requireRegistered(owner);
        return registry.isEnabled(owner);
    }
}
