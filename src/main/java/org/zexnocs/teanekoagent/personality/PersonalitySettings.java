package org.zexnocs.teanekoagent.personality;

/**
 * 本轮固定的人格选择与独立应用/学习开关，不包含可由普通配置修改的补正 key。
 * @param personalityId 基础人格，空值使用本地默认
 * @param customEnabled 是否替换人格描述层
 * @param customPersonality 作用域自定义描述
 * @param customSpeakingStyle 自定义表达风格
 * @param correctionsEnabled 是否应用已接受补正
 * @param learningEnabled 是否允许候选新增，不自动触发模型学习
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record PersonalitySettings(String personalityId, boolean customEnabled, String customPersonality, String customSpeakingStyle,
        boolean correctionsEnabled, boolean learningEnabled) {
    /**
     * 拒绝损坏配置，不把开启但空白的自定义人格静默改为其他人格。
     * @param personalityId 基础人格选择
     * @param customEnabled 自定义开关
     * @param customPersonality 自定义描述
     * @param customSpeakingStyle 自定义风格
     * @param correctionsEnabled 应用开关
     * @param learningEnabled 学习开关
     * @throws IllegalArgumentException 当配置范围无效时
     */
    public PersonalitySettings {
        if (personalityId == null || personalityId.length() > 128 || customPersonality == null || customSpeakingStyle == null
                || customPersonality.length() > 4096 || customSpeakingStyle.length() > 4096 || customEnabled && customPersonality.isBlank()) {
            throw new IllegalArgumentException("人格作用域配置无效。");
        }
    }
}
