package org.zexnocs.teanekoagent.adapter.config;

import org.zexnocs.teanekoagent.agent.access.AgentScopePolicy;
import org.zexnocs.teanekoagent.skill.SkillPolicy;
import org.zexnocs.teanekocore.database.configdata.api.IConfigFieldChecker;

import java.util.Arrays;

/**
 * 校验作用域开关、活动模式及窗口覆盖，拒绝未知字段，不承担调用者权限校验。
 *
 * <br>2026/11/03：沿用原 cfg 权限校验新的表达开关。
 * <br>2026/11/03：素材采集开关执行相同严格布尔校验。
 * <br>2026/11/03：共享图库和原生表情回复开关同样严格校验。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.6.2-l
 */
public final class AgentScopeFieldChecker implements IConfigFieldChecker {
    /**
     * 按显式字段列表校验值，权限继续由原 /cfg 命令链负责。
     * 时区与 Skill 解析仅用于验证输入，不替换原始配置值。
     * @param field 目标字段
     * @param value 输入值
     * @return 合法时为 null，否则为固定中文原因
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override public String isValid(String field, String value) {
        // 2026/11/03：表达与素材采集开关沿用原严格布尔校验。
        // 2026/11/03：资源开关沿用现有 cfg 权限与严格布尔约束。
        if ("stickerReplyEnabled".equals(field) || "nativeExpressionsEnabled".equals(field) || "stickerCollectEnabled".equals(field) || "expressionEnabled".equals(field) || "proactiveTimedEnabled".equals(field) || "proactiveInterjectionEnabled".equals(field) || "enabled".equals(field) || "commandToolsEnabled".equals(field) || "skillsEnabled".equals(field) || "memoryReadEnabled".equals(field) || "memoryAddEnabled".equals(field) || "memoryLearningEnabled".equals(field)
                || "customPersonalityEnabled".equals(field) || "personalityCorrectionsEnabled".equals(field) || "personalityLearningEnabled".equals(field)
                || "historyCaptureEnabled".equals(field) || "historyReadEnabled".equals(field) || "autoSummaryEnabled".equals(field)) {
            return "true".equals(value) || "false".equals(value) ? null : "Agent 开关只能为 true 或 false。";
        }
        if ("skills".equals(field)) {
            try { SkillPolicy.parse(true, value); return null; }
            catch (IllegalArgumentException invalid) { return "Skill 名称须为英文逗号分隔的显式列表，最多八项，不允许重复或路径。"; }
        }
        if ("triggerNames".equals(field)) {
            if (value == null || value.length() > 263) { return "机器人称呼列表过长。"; }
            if (value.isEmpty()) { return null; }
            var names = value.split(",", -1);
            return names.length <= 8 && Arrays.stream(names).allMatch(name -> !name.isBlank() && name.equals(name.strip()) && name.length() <= 32)
                    && Arrays.stream(names).distinct().count() == names.length ? null : "机器人称呼须为最多八项不重复的列表，每项1至32字符，不带首尾空白。";
        }
        if ("personalityId".equals(field)) { return value != null && value.length() <= 128 ? null : "人格 ID 不能超过 128 字符。"; }
        if ("customPersonality".equals(field) || "customSpeakingStyle".equals(field)) {
            return value != null && value.length() <= 4096 ? null : "自定义人格文本不能超过 4096 字符。";
        }
        if ("mode".equals(field)) {
            return Arrays.stream(AgentScopePolicy.Mode.values()).anyMatch(mode -> mode.name().equals(value))
                    ? null : "Agent 模式只能为 PASSIVE_BASIC、PASSIVE_CONTEXTUAL 或 ACTIVE。";
        }
        if ("taskTimeZone".equals(field) || "activityTimeZone".equals(field)) {
            try {
                if ("taskTimeZone".equals(field) && value.isEmpty()) { return null; }
                java.time.ZoneId.of(value);
                return null;
            }
            catch (java.time.DateTimeException | NullPointerException invalid) {
                return "taskTimeZone".equals(field) ? "任务时区必须为空或合法的 ZoneId，例如 Asia/Shanghai。"
                        : "活动时区必须是合法的 ZoneId，例如 UTC 或 Asia/Shanghai。";
            }
        }
        if ("proactiveCooldownSeconds".equals(field) || "proactiveQuietStartHour".equals(field) || "proactiveQuietEndHour".equals(field)) {
            try {
                int number = Integer.parseInt(value);
                return "proactiveCooldownSeconds".equals(field) ? number >= 1 && number <= 86400 ? null : "主动冷却必须为一秒至一天。"
                        : number >= 0 && number <= 23 ? null : "静默时段小时必须为 0 至 23。";
            } catch (NumberFormatException invalid) { return "主动触发时段与冷却必须为整数。"; }
        }
        if ("proactiveIntervalSeconds".equals(field) || "proactiveAttemptCooldownSeconds".equals(field)) {
            try { int seconds = Integer.parseInt(value); return seconds >= 60 && seconds <= 86400 ? null : "自主候选间隔和退避须在一分钟至一天之间。"; }
            catch (NumberFormatException invalid) { return "自主候选间隔和退避必须为整数。"; }
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
