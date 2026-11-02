package org.zexnocs.teanekocore.plugin;

import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import java.lang.reflect.Method;

/**
 * 将既有扫描式命令、事件和任务阶段接入插件归属，不改变未声明归属的旧扩展。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.6.0-l
 */
@Component
public final class PluginAccess {
    private final PluginRegistry registry;
    /**
     * 借用统一目录。
     * @param registry 插件目录
     */
    public PluginAccess(PluginRegistry registry) { this.registry = registry; }
    /**
     * 方法声明优先，其次读取代理目标类型的归属。
     * @param target 运行 Bean
     * @param method 实际方法
     * @return 插件 ID，未声明时为空
     */
    public String owner(Object target, Method method) {
        var type = AopUtils.getTargetClass(target);
        var specific = AopUtils.getMostSpecificMethod(method, type);
        var annotation = AnnotatedElementUtils.findMergedAnnotation(specific, PluginOwned.class);
        if (annotation == null) { annotation = AnnotatedElementUtils.findMergedAnnotation(type, PluginOwned.class); }
        return annotation == null ? null : annotation.value();
    }
    /**
     * 检查声明者已注册并捕获有效版本。
     * @param target 目标 Bean
     * @param method 实际方法
     * @return 无归属时为空，否则返回当前令牌
     */
    public PluginRegistry.Token capture(Object target, Method method) {
        var owner = owner(target, method); return owner == null ? null : registry.capture(owner);
    }
    /**
     * 复核先前捕获的版本。
     * @param token 可选调用令牌
     */
    public void check(PluginRegistry.Token token) { if (token != null) { registry.check(token); } }
    /**
     * 判断监听器是否应该参与当前分发。
     * @param target 目标 Bean
     * @param method 实际方法
     * @return 未声明归属或插件有效时为真
     */
    public boolean enabled(Object target, Method method) {
        var owner = owner(target, method);
        if (owner == null) { return true; }
        registry.requireRegistered(owner); return registry.isEnabled(owner);
    }
}
