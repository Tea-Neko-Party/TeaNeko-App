package org.zexnocs.teanekoagent._app_config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.memory.recall.IMemoryRecallDefaults;
import org.zexnocs.teanekoagent.memory.recall.MemoryRecallException;
import org.zexnocs.teanekoagent.memory.recall.MemoryRecallSettings;

import java.util.Objects;

/**
 * 懒读并验证整份检索策略，非法配置只使本次查询失败，修复后下一次恢复。
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service
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
        var value = environment.getProperty("tea-neko.agent.memory.recall." + key, Integer.class, fallback);
        if (value == null) { throw new IllegalStateException("记忆检索配置不能返回空整数。"); } return value;
    }
}
