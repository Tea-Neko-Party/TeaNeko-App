package org.zexnocs.teanekoagent.adapter.config;

import org.zexnocs.teanekoagent.agent.access.AgentScopePolicy;
import org.zexnocs.teanekocore.database.configdata.api.IConfigFieldChecker;

import java.util.Arrays;

/**
 * 校验作用域开关、活动模式及窗口覆盖，拒绝未知字段，不承担调用者权限校验。
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.0.0
 * @version 4.5.0-l
 */
public final class AgentScopeFieldChecker implements IConfigFieldChecker {
    /**
     * 按显式字段列表校验值，权限继续由原 /cfg 命令链负责。
     * @param field 目标字段
     * @param value 输入值
     * @return 合法时为 null，否则为固定中文原因
     */
    @Override public String isValid(String field, String value) {
        if ("enabled".equals(field) || "memoryReadEnabled".equals(field) || "memoryAddEnabled".equals(field) || "memoryLearningEnabled".equals(field)
                || "customPersonalityEnabled".equals(field) || "personalityCorrectionsEnabled".equals(field) || "personalityLearningEnabled".equals(field)) {
            return "true".equals(value) || "false".equals(value) ? null : "Agent 开关只能为 true 或 false。";
        }
        if ("personalityId".equals(field)) { return value != null && value.length() <= 128 ? null : "人格 ID 不能超过 128 字符。"; }
        if ("customPersonality".equals(field) || "customSpeakingStyle".equals(field)) {
            return value != null && value.length() <= 4096 ? null : "自定义人格文本不能超过 4096 字符。";
        }
        if ("mode".equals(field)) {
            return Arrays.stream(AgentScopePolicy.Mode.values()).anyMatch(mode -> mode.name().equals(value))
                    ? null : "Agent 模式只能为 PASSIVE_BASIC、PASSIVE_CONTEXTUAL 或 ACTIVE。";
        }
        if ("historyMaxMessages".equals(field)) {
            try { if (Integer.parseInt(value) >= -1) { return null; } }
            catch (NumberFormatException ignored) { /* 非整数按固定原因拒绝。 */ }
            return "历史消息上限必须为非负整数，或使用 -1 继承本地值。";
        }
        if ("inputMaxBytes".equals(field)) {
            try { long size = Long.parseLong(value); if (size == -1 || size > 0) { return null; } }
            catch (NumberFormatException ignored) { /* 非整数按固定原因拒绝。 */ }
            return "输入字节上限必须为正整数，或使用 -1 继承本地值。";
        }
        return "该字段不允许通过 Agent 作用域配置修改。";
    }
}
