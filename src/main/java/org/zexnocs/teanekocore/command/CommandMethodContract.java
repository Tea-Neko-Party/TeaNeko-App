package org.zexnocs.teanekocore.command;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.CompletionStage;

/**
 * 扫描和执行共用的命令方法契约，避免装配与运行采用不同规则。
 * <br>2026/11/03：管理命令必须携带明确的受信上下文参数。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.5.0-l
 * @version 4.7.0-l
 */
public final class CommandMethodContract {
    /**
     * 禁止实例化方法契约检查器，扫描与执行统一复用静态校验。
     */
    private CommandMethodContract() {}

    /**
     * 在扫描和执行边界拒绝非公开或不返回 CompletionStage 的命令方法，避免留下同步执行旁路。
     *
     * @param method 已选定的目标方法
     * @throws IllegalArgumentException 当参数格式、范围或声明的类型约束不满足时
     */
    public static void requireAsync(Method method) {
        // 2026/11/03：不允许无上下文的管理方法绕过排队后的当前授权检查。
        if (method.isAnnotationPresent(org.zexnocs.teanekocore.command.api.ManagementCommand.class)
                && !ManagementCommandPolicy.declared(method)) {
            throw new IllegalArgumentException("管理命令必须接收具体的 CommandData 管理上下文，并使用支持的协议版本。");
        }
        if (!Modifier.isPublic(method.getModifiers())
                || !CompletionStage.class.isAssignableFrom(method.getReturnType())) {
            throw new IllegalArgumentException("Command handler must be public and return CompletionStage: " + method);
        }
    }
}
