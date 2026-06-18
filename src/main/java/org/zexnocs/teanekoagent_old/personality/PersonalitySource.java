package org.zexnocs.teanekoagent_old.personality;

/**
 * active base personality 的来源。
 *
 * @author zExNocs
 * @date 2026/06/09
 * @since 4.4.1
 */
public enum PersonalitySource {
    /**
     * 来自文件主性格。
     */
    FILE,

    /**
     * 来自 ConfigData 自定义性格。
     */
    CUSTOM_CONFIG,

    /**
     * 来自内置兜底性格。
     */
    FALLBACK
}
