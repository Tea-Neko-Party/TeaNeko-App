package org.zexnocs.teanekoclient.web;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.framework.extension.AnnotatedBeanRegistry;
import org.zexnocs.teanekocore.plugin.PluginAccess;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 按声明顺序调用房间准备扩展，发现与插件启用分开，异步完成后再次检查原授权。
 *
 * 修改记录：<br>
 * 2026/11/04：扩展以业务 id 注册，不依赖 Spring Bean 名。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.7.5-l
 * @version 4.7.5-l
 */
@Service
public final class WebRoomPreparations {
    private final IBeanScanner scanner;
    private @org.jspecify.annotations.Nullable AnnotatedBeanRegistry<String, IWebRoomPreparation> registry;
    private final ObjectProvider<PluginAccess> plugins;

    /**
     * @param scanner 原 Bean 发现入口
     * @param plugins 只有装配了实际扩展时才取得插件边界
     */
    public WebRoomPreparations(IBeanScanner scanner, ObjectProvider<PluginAccess> plugins) {
        this.plugins = plugins;
        this.scanner = scanner;
    }

    /**
     * 2026/11/04：独立 id 保持扩展键稳定，不占用容器名称。
     * @return 首次实际进入房间时才发现的准备扩展，避免模块构造环
     */
    private synchronized AnnotatedBeanRegistry<String, IWebRoomPreparation> registry() {
        if (registry == null) {
            registry = new AnnotatedBeanRegistry<>(scanner, WebRoomPreparation.class, IWebRoomPreparation.class,
                declaration -> {
                    if (!declaration.id().matches("[a-z][a-z0-9_.-]{0,63}")) {
                        throw new IllegalArgumentException("房间准备扩展标识无效。");
                    }
                    return declaration.id();
                });
        }
        return registry;
    }

    /**
     * @param room 已保存的真实房间
     * @param guard 当前会话与房间生命周期
     * @return 所有已启用扩展实际完成后的阶段
     */
    public CompletionStage<Void> prepare(WebChat.Room room, Runnable guard) {
        CompletionStage<Void> work = CompletableFuture.completedStage(null);
        for (var entry : registry().entries().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).toList()) {
            work = work.thenCompose(ignored -> {
                guard.run();
                var bean = entry.getValue().bean();
                final java.lang.reflect.Method method;
                try {
                    method = IWebRoomPreparation.class.getMethod("prepare", WebChat.Room.class, Runnable.class);
                } catch (NoSuchMethodException failure) {
                    throw new IllegalStateException("房间准备接口缺少声明方法。", failure);
                }
                var access = plugins.getObject();
                if (!access.enabled(bean, method)) {
                    return CompletableFuture.completedStage(null);
                }
                var token = access.capture(bean, method);
                Runnable checked = () -> {
                    guard.run();
                    access.check(token);
                };
                return bean.prepare(room, checked).thenRun(checked);
            });
        }
        return work;
    }
}
