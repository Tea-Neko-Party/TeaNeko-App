package org.zexnocs.teanekoagent.llm._app_config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekoagent.llm.framework.interaction.ILLMInteractionDefaults;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMCallLimits;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionSettings;
import org.zexnocs.teanekoagent.llm.framework.model.LLMConfigurationException;

import java.time.Duration;
import java.util.Objects;

/**
 * 从 Spring 本地配置懒读取交互次数和秒级时限，不依赖 Agent 配置或可导出 Prompt。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service
public final class SpringLLMInteractionDefaults implements ILLMInteractionDefaults {
    private static final String PREFIX = "tea-neko.llm.interaction.";
    private final Environment environment;

    /**
     * 仅保存 Environment，不在 Bean 创建阶段解析可能无效的属性。
     * @param environment 应用本地配置来源
     */
    public SpringLLMInteractionDefaults(Environment environment) {
        this.environment = Objects.requireNonNull(environment, "本地配置环境不能为空。");
    }

    /**
     * 每轮读取一个完整快照；解析失败只报告固定说明，不导出属性值或原始异常链。
     * @return 本次固定的次数及时间设置
     * @throws LLMConfigurationException 当任一字段不可解析或超出允许范围时
     */
    @Override
    public LLMInteractionSettings resolve() {
        try {
            return new LLMInteractionSettings(new LLMCallLimits(number("max-model-calls", 3),
                    number("max-tool-rounds", 2), number("max-tool-calls", 6),
                    environment.getProperty(PREFIX + "max-total-tokens", Long.class)),
                    Duration.ofSeconds(number("model-timeout-seconds", 60)),
                    Duration.ofSeconds(number("tool-timeout-seconds", 15)),
                    Duration.ofSeconds(number("turn-timeout-seconds", 120)));
        } catch (RuntimeException ignored) {
            throw new LLMConfigurationException("LLM 交互配置不可用，请检查本地次数和时限设置。");
        }
    }

    /**
     * 读取整数属性，缺失时采用计划默认值，格式错误由调用边界统一脱敏。
     * @param name 当前交互配置字段名
     * @param fallback 未设置字段时的默认值
     * @return 本地整数值
     */
    private int number(String name, int fallback) {
        return environment.getProperty(PREFIX + name, Integer.class, fallback);
    }
}
