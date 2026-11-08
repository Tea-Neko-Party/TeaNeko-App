package org.zexnocs.teanekocore.plugin.management;

// 2026/11/07：接口统一使用 I 前缀，调用合同与持久数据保持不变。

import lombok.NonNull;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import java.util.Objects;
import java.util.UUID;

/**
 * 管理调用的可信身份及生命周期，只能由已认证的宿主适配器构建，不接受浏览器自行声明角色。
 * <br>2026/11/03：宿主授权回调随实际事务复核，不依赖请求线程上下文。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @param subject 已认证主体的宿主标识
 * @param scopeRef 完整客户端及作用域引用，全局操作使用宿主约定值
 * @param requestId 本次调用 ID，写操作也作为幂等请求关联
 * @param control 借用管理调用控制
 * @param authorization 宿主捕获的实时权限复核，不代替资源权限声明
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.0-l
 * @version 4.7.0-l
 */
@com.fasterxml.jackson.annotation.JsonIgnoreType
public record ManagementContext(String subject, String scopeRef, UUID requestId, ExecutionControl control,
                                Runnable authorization) {
    /**
     * 拒绝缺失身份与执行控制，不根据文本推断权限。
     * @param subject 可信主体
     * @param scopeRef 授权目标
     * @param requestId 请求 ID
     * @param control 借用控制
     * @param authorization 实际执行前重复检查的宿主能力
     */
    public ManagementContext {
        if (subject == null || subject.isBlank() || scopeRef == null || scopeRef.isBlank()) { throw new IllegalArgumentException("管理身份或作用域不能为空。"); }
        // 2026/11/03：授权句柄只在内存借用，不导出或持久化。
        Objects.requireNonNull(requestId);
        Objects.requireNonNull(control);
        Objects.requireNonNull(authorization);
    }
    /**
     * 保留原传输无关构造，具体资源权限继续由 IManagementAuthority 校验。
     * @param subject 原主体
     * @param scopeRef 原目标
     * @param requestId 原请求
     * @param control 原控制
     */
    public ManagementContext(String subject, String scopeRef, UUID requestId, ExecutionControl control) {
        this(subject, scopeRef, requestId, control, control::checkActive);
    }

    /** 复核控制和宿主实时授权，供实际数据库事务及外部副作用调用。 */
    public void checkActive() {
        control.checkActive();
        authorization.run();
    }
    /** @return 只含请求标识的安全诊断，不导出主体、作用域或控制 */
    @Override @NonNull
    public String toString() { return "ManagementContext[请求=" + requestId + "，身份及控制已隐藏]"; }
}
