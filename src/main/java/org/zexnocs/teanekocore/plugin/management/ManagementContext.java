package org.zexnocs.teanekocore.plugin.management;

import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import java.util.Objects;
import java.util.UUID;

/**
 * 管理调用的可信身份及生命周期，只能由已认证的宿主适配器构建，不接受浏览器自行声明角色。
 * @param subject 已认证主体的宿主标识
 * @param scopeRef 完整客户端及作用域引用，全局操作使用宿主约定值
 * @param requestId 本次调用 ID，写操作也作为幂等请求关联
 * @param control 借用管理调用控制
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.0.0
 * @version 4.6.0-l
 */
@com.fasterxml.jackson.annotation.JsonIgnoreType
public record ManagementContext(String subject, String scopeRef, UUID requestId, ExecutionControl control) {
    /**
     * 拒绝缺失身份与执行控制，不根据文本推断权限。
     * @param subject 可信主体
     * @param scopeRef 授权目标
     * @param requestId 请求 ID
     * @param control 借用控制
     */
    public ManagementContext {
        if (subject == null || subject.isBlank() || scopeRef == null || scopeRef.isBlank()) { throw new IllegalArgumentException("管理身份或作用域不能为空。"); }
        Objects.requireNonNull(requestId); Objects.requireNonNull(control);
    }
    /** @return 只含请求标识的安全诊断，不导出主体、作用域或控制 */
    @Override public String toString() { return "ManagementContext[请求=" + requestId + "，身份及控制已隐藏]"; }
}
