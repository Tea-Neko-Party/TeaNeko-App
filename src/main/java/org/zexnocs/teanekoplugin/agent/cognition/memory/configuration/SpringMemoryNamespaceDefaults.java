package org.zexnocs.teanekoplugin.agent.cognition.memory.configuration;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoplugin.agent.cognition.memory.namespace.IMemoryNamespaceDefaults;
import java.util.Objects;

/**
 * 懒读个人记忆空间配额，错误仅影响本次创建，不删除既有空间。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.5.0-l
 * @version 4.9.0-l
 */
@Service
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.cognition")
public final class SpringMemoryNamespaceDefaults implements IMemoryNamespaceDefaults {
    private final Environment environment;

    /**
     * 保存属性来源，不在装配时解析值。
     * @param environment 本地 Spring 属性
     * @throws NullPointerException 当属性来源为空时
     */
    public SpringMemoryNamespaceDefaults(Environment environment) {
        this.environment = Objects.requireNonNull(environment, "记忆空间配置环境不能为空。");
    }

    /**
     * 下一次创建读取最新正整数配额；异常不回显原值和配置异常链。
     * @return 默认五个的个人活动自建空间上限
     * @throws IllegalStateException 当配额配置无效时
     */
    @Override public int maxCreated() {
        try {
            int value = environment.getProperty("tea-neko.agent.memory.max-created-namespaces", Integer.class, 5);
            if (value < 1) { throw new IllegalArgumentException(); }
            return value;
        } catch (RuntimeException invalid) { throw new IllegalStateException("个人记忆空间配额配置无效。"); }
    }
}
