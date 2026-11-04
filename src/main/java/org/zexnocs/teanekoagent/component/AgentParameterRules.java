package org.zexnocs.teanekoagent.component;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.component.skill.SkillPolicy;
import org.zexnocs.teanekoagent.runtime.access.AgentScopePolicy;

import java.util.Arrays;

/**
 * 旧字段及内置组件共用的参数约束，组件装配不会绕过既有业务范围。
 *
 * <br>2026/11/04：更新分层重构后的类型引用，保持原调用与生命周期行为。
 *
 * <br>2026/11/05：说明时区解析只用于验证，保持原始合法别名，不伪造对解析结果的使用。
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.8.0-l
 */
public final class AgentParameterRules {
    /** 禁止创建无状态参数校验器。 */
    private AgentParameterRules() {}

    /**
     * @param field 稳定字段名
     * @param value 原标量文本
     * @return 非法时的固定说明，合法时为空
     */
    public static @Nullable String validate(String field, String value) {
        // 2026/11/04：空路由表示继承；具体存在性由模型预检及模型选择入口判断。
        if ("modelRoute".equals(field)) {
            return value != null && (value.isEmpty() || value.matches("[a-z][a-z0-9-]{0,63}"))
                    ? null : "模型路由须为小写字母开头的字母、数字或连字符，最多64字符。";
        }
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
                // 2026/11/05：这里只验证原始时区文本，故意通过解析异常拒绝输入，不改写合法别名。
                //noinspection ResultOfMethodCallIgnored
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

