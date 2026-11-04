package org.zexnocs.teanekoagent.component.llm.framework.route;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.framework.extension.AnnotatedBeanRegistry;
import org.zexnocs.teanekocore.utils.scanner.inerfaces.IBeanScanner;

/**
 * 配置来源注册表，系统文件与聊天私有路由互不回退，不依赖适配器内的业务条件分支。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.8.0-l
 */
@Service
public final class LLMRouteDirectory {
    private final IBeanScanner scanner;
    private volatile @org.jspecify.annotations.Nullable AnnotatedBeanRegistry<String, ILLMRouteSource> registry;

    /** @param scanner 原注解扫描器，延迟发现以避免协议服务的构造循环 */
    public LLMRouteDirectory(IBeanScanner scanner) {
        this.scanner = scanner;
    }

    /**
     * @param route 完整路由
     * @return 原 Spring 来源实现
     * @throws IllegalArgumentException 当来源未声明时
     */
    public ILLMRouteSource source(String route) {
        var current = registry;
        if (current == null) {
            synchronized (this) {
                current = registry;
                if (current == null) {
                    current = new AnnotatedBeanRegistry<>(scanner, LLMRouteSource.class, ILLMRouteSource.class,
                            declaration -> {
                                if (!declaration.namespace().matches("[a-z][a-z0-9-]*")) {
                                    throw new IllegalArgumentException("模型配置来源命名空间无效。");
                                }
                                return declaration.namespace();
                            });
                    registry = current;
                }
            }
        }
        int separator = route.indexOf(':');
        return current.require(separator < 0 ? "file" : route.substring(0, separator)).bean();
    }
}
