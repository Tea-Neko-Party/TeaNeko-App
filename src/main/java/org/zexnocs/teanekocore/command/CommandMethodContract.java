package org.zexnocs.teanekocore.command;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.CompletionStage;

/**
 * 扫描和执行共用的命令方法契约，避免装配与运行采用不同规则。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
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
        if (!Modifier.isPublic(method.getModifiers())
                || !CompletionStage.class.isAssignableFrom(method.getReturnType())) {
            throw new IllegalArgumentException("Command handler must be public and return CompletionStage: " + method);
        }
    }
}
