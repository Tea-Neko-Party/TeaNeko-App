package org.zexnocs.teanekocore.command.interfaces;

import java.lang.reflect.Method;

/**
 * 指令执行器接口。
 * 主要用于包装成 TimerTask 来执行。
 *
 * @author zExNocs
 * @date 2026/02/18
 * @since 4.0.0
 */
public interface ICommandExecutor {
    /**
     * 执行指令。
     * @param object 指令执行对象
     * @param method 指令方法
     * @param args 指令参数
     * @param taskNamespace 指令命名空间
     */
    void execute(Object object, Method method, Object[] args, String taskNamespace);
}
