package org.zexnocs.teanekoagent.component.llm.framework.route;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.framework.extension.AnnotatedBeanRegistry;
import org.zexnocs.teanekocore.reload.api.IScanner;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

/**
 * 配置来源注册表，系统文件与聊天私有路由互不回退，不依赖适配器内的业务条件分支。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：初始化或显式重扫发现来源，模型请求只查询缓存，不在失败后反复扫描。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.8.2-l
 */
@Service
public final class LLMRouteDirectory implements IScanner {
    private final IBeanScanner scanner;
    private volatile @org.jspecify.annotations.Nullable AnnotatedBeanRegistry<String, ILLMRouteSource> registry;

    /** @param scanner 原注解扫描器，发现延迟到重载服务初始化以避免构造循环 */
    public LLMRouteDirectory(IBeanScanner scanner) {
        this.scanner = scanner;
    }

    /** 2026/11/05：由原扫描生命周期初始化一次，不在模型请求内发现来源。 */
    @Override
    public synchronized void init() {
        if (registry == null) {
            reload();
        }
    }

    /**
     * 2026/11/05：完整来源注册表校验后原子发布，失败保留上一目录。
     * @throws IllegalArgumentException 当来源标识非法或重复时
     */
    @Override
    public synchronized void reload() {
        registry = new AnnotatedBeanRegistry<>(scanner, LLMRouteSource.class, ILLMRouteSource.class,
                declaration -> {
                    if (!declaration.namespace().matches("[a-z][a-z0-9-]*")) {
                        throw new IllegalArgumentException("模型配置来源命名空间无效。");
                    }
                    return declaration.namespace();
                });
    }

    /**
     * @param route 完整路由
     * @return 原 Spring 来源实现
     * @throws IllegalArgumentException 当来源未声明时
     * @throws IllegalStateException 当目录尚未成功初始化时
     */
    public ILLMRouteSource source(String route) {
        var current = registry;
        // 2026/11/05：查询不承担扫描或重试，避免每次模型请求重新触发失败发现。
        if (current == null) {
            throw new IllegalStateException("模型路由目录尚未成功初始化，请检查初始化日志并显式重扫。");
        }
        int separator = route.indexOf(':');
        return current.require(separator < 0 ? "file" : route.substring(0, separator)).bean();
    }
}
