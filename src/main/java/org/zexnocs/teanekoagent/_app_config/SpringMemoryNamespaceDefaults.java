package org.zexnocs.teanekoagent._app_config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.memory.namespace.IMemoryNamespaceDefaults;
import java.util.Objects;

/**
 * 懒读个人记忆空间配额，错误仅影响本次创建，不删除既有空间。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service
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
