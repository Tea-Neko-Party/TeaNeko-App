package org.zexnocs.teanekoagent_old.file_config.personality;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.zexnocs.teanekocore.database.configdata.api.IConfigData;
import org.zexnocs.teanekocore.framework.description.Description;

import java.util.ArrayList;
import java.util.List;

/**
 * 作用域级 Agent 人格与模型覆盖配置。
 *
 * @author zExNocs
 * @date 2026/06/09
 * @since 4.4.1
 */
@Getter
@Setter
@NoArgsConstructor
public class AgentPersonalityConfigData implements IConfigData {
    /**
     * 当前作用域是否启用 Agent。
     */
    @Description("当前作用域是否启用 Agent。")
    private boolean enabled = true;

    /**
     * Agent ID。
     * <br>空值表示使用文件配置中的默认 agent。
     */
    @Description("Agent ID。空值表示使用文件配置中的默认 agent。")
    private String agentId = "";

    /**
     * 是否启用自定义性格。
     * <br>启用后该作用域不再使用文件主性格作为人格层。
     */
    @Description("是否启用自定义性格。")
    private boolean customPersonalityEnabled = false;

    /**
     * 自定义基础性格。
     */
    @Description("自定义基础性格。仅在启用自定义性格时生效。")
    private String customPersonality = "";

    /**
     * 自定义说话风格。
     */
    @Description("自定义说话风格。")
    private String customSpeakingStyle = "";

    /**
     * 自定义硬边界。
     * <br>该字段不能覆盖运行层硬规则。
     */
    @Description("自定义硬边界，不能覆盖运行层硬规则。")
    private List<String> customBoundaries = new ArrayList<>();

    /**
     * 是否启用性格学习。
     */
    @Description("是否启用性格学习。")
    private boolean personalityLearningEnabled = true;

    /**
     * 是否启用长期记忆读取。
     */
    @Description("是否启用长期记忆读取。")
    private boolean memoryEnabled = true;

    /**
     * 模型适配器 ID。
     * <br>该值是 provider 级 ID，例如 {@code deepseek}，不是具体模型名。
     */
    @Description("模型适配器 ID，例如 deepseek。")
    private String modelId = "";

    /**
     * 供应商侧具体模型名。
     */
    @Description("供应商侧具体模型名。")
    private String model = "";

    /**
     * 供应商侧 API 名称或 endpoint。
     * <br>不要在此字段保存原始 API key。
     */
    @Description("供应商侧 API 名称或 endpoint，不应保存原始 API key。")
    private String modelApi = "";

    /**
     * 可选供应商 base URL。
     */
    @Description("可选供应商 base URL。")
    private String baseUrl = "";

    /**
     * 可选采样温度。
     */
    @Description("可选采样温度。")
    private Double temperature = null;

    /**
     * 可选 top-p 参数。
     */
    @Description("可选 top-p 参数。")
    private Double topP = null;

    /**
     * 可选输出 token 上限。
     */
    @Description("可选输出 token 上限。")
    private Integer maxTokens = null;

    /**
     * 转换为 Agent Core 使用的不可变运行配置。
     *
     * @return Agent 人格运行配置。
     */
    public AgentPersonalityRuntimeConfig toRuntimeConfig() {
        return new AgentPersonalityRuntimeConfig(
                enabled,
                safe(agentId),
                customPersonalityEnabled,
                safe(customPersonality),
                safe(customSpeakingStyle),
                customBoundaries == null ? List.of() : List.copyOf(customBoundaries),
                personalityLearningEnabled,
                memoryEnabled,
                safe(modelId),
                safe(model),
                safe(modelApi),
                safe(baseUrl),
                temperature,
                topP,
                maxTokens
        );
    }

    /**
     * 规范化字符串字段。
     *
     * @param value 原始值。
     * @return 非空字符串。
     */
    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
