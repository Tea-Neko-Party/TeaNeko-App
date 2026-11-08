package org.zexnocs.teanekoplugin.agent.cognition.memory.configuration;

// 2026/11/07：认知业务契约归入插件，纯会话数据沿用运行底座。

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoplugin.agent.cognition.memory.recall.IMemoryRecallDefaults;
import org.zexnocs.teanekoplugin.agent.cognition.memory.recall.MemoryRecallException;
import org.zexnocs.teanekoplugin.agent.cognition.memory.recall.MemoryRecallSettings;

import java.util.Objects;

/**
 * 懒读并验证整份检索策略，非法配置只使本次查询失败，修复后下一次恢复。
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
public final class SpringMemoryRecallDefaults implements IMemoryRecallDefaults {
    private final Environment environment;
    /**
     * 保存属性来源，不在装配时读取值。
     * @param environment 本地 Spring 配置
     * @throws NullPointerException 当环境为空时
     */
    public SpringMemoryRecallDefaults(Environment environment) { this.environment = Objects.requireNonNull(environment, "记忆检索配置来源不能为空。"); }
    /**
     * 一次取得完整合法策略，不回显原始配置值或绑定异常链。
     * @return 本次策略
     * @throws MemoryRecallException 当配置无效时
     */
    @Override public MemoryRecallSettings settings() {
        try {
            return new MemoryRecallSettings(value("injection-limit", 12), value("tool-limit", 8), value("hard-limit", 20),
                    value("max-characters", 16000), value("max-scanned", 1000));
        } catch (RuntimeException invalid) { throw new MemoryRecallException(MemoryRecallException.Reason.CONFIGURATION_ERROR); }
    }
    /**
     * 按明确前缀读取整数，范围由策略整体校验。
     * @param key 配置后缀
     * @param fallback 缺失默认值
     * @return 配置整数
     * @throws IllegalStateException 当配置来源返回空整数时
     */
    private int value(String key, int fallback) {
        return environment.getProperty("tea-neko.agent.memory.recall." + key, Integer.class, fallback);
    }
}
