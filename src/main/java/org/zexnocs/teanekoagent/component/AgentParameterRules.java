package org.zexnocs.teanekoagent.component;

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoagent.runtime.access.AgentScopePolicy;

import java.util.Arrays;

/**
 * 原生组件与通用聊天入口的参数约束，具体业务规则由所属插件维护。
 * <br>2026/11/04：更新分层重构后的类型引用，保持原调用与生命周期行为。
 * <br>2026/11/05：说明时区解析只用于验证，保持原始合法别名，不伪造对解析结果的使用。
 * <br>2026/11/07：接入插件归属与独立贡献，保留原权限及执行契约。
 * <br>2026/11/07：移出业务字段列表与范围校验，只保留原生运行限制。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.9.0-l
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
        // 2026/11/07：这里只校验基础开关，业务字段由所属插件组件验证。
        if ("enabled".equals(field) || "failureReplyEnabled".equals(field)) {
            return "true".equals(value) || "false".equals(value) ? null : "Agent 开关只能为 true 或 false。";
        }
        if ("triggerNames".equals(field)) {
            if (value == null || value.length() > 263) { return "机器人称呼列表过长。"; }
            if (value.isEmpty()) { return null; }
            var names = value.split(",", -1);
            return names.length <= 8 && Arrays.stream(names).allMatch(name -> !name.isBlank() && name.equals(name.strip()) && name.length() <= 32)
                    && Arrays.stream(names).distinct().count() == names.length ? null : "机器人称呼须为最多八项不重复的列表，每项1至32字符，不带首尾空白。";
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

