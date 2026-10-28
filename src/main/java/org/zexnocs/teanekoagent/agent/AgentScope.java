package org.zexnocs.teanekoagent.agent;

import java.util.Objects;

/**
 * 保留客户端来源的作用域定位；scopeId 沿用宿主的现有格式，不自行解析平台编号。
 * @param clientId 宿主客户端标识
 * @param scopeId 宿主作用域标识
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public record AgentScope(String clientId, String scopeId) {
    /**
     * 拒绝缺失或带首尾空白的身份边界，避免规范化后意外绑定到其他来源。
     * @param clientId 客户端标识
     * @param scopeId 作用域标识
     * @throws IllegalArgumentException 当任一标识为空白或带首尾空白时
     */
    public AgentScope {
        requireIdentity(clientId); requireIdentity(scopeId);
    }

    /**
     * 验证宿主提供的标识，不替换、拼接或推导其权限语义。
     * @param value 待验证标识
     * @throws IllegalArgumentException 当标识为空白或不稳定时
     * @throws NullPointerException 当标识为空时
     */
    static void requireIdentity(String value) {
        Objects.requireNonNull(value, "宿主标识不能为空。");
        if (value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException("宿主标识不能为空白或包含首尾空白。");
        }
    }
}
