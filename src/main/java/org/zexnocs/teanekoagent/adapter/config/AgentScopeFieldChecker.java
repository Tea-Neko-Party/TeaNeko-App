package org.zexnocs.teanekoagent.adapter.config;

import org.zexnocs.teanekoagent.agent.access.AgentScopePolicy;
import org.zexnocs.teanekocore.database.configdata.api.IConfigFieldChecker;

import java.util.Arrays;

/**
 * 校验作用域开关和活动模式，拒绝未知字段或大小写近似值，不承担调用者权限校验。
 *
 * @author zExNocs
 * @date 2026/10/28
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
        if ("enabled".equals(field)) { return "true".equals(value) || "false".equals(value) ? null : "Agent 开关只能为 true 或 false。"; }
        if ("mode".equals(field)) {
            return Arrays.stream(AgentScopePolicy.Mode.values()).anyMatch(mode -> mode.name().equals(value))
                    ? null : "Agent 模式只能为 PASSIVE_BASIC、PASSIVE_CONTEXTUAL 或 ACTIVE。";
        }
        return "该字段不允许通过 Agent 作用域配置修改。";
    }
}
