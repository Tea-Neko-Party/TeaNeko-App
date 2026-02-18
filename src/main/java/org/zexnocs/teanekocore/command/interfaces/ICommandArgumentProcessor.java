package org.zexnocs.teanekocore.command.interfaces;

import org.zexnocs.teanekocore.command.CommandData;

import java.lang.reflect.Method;

/**
 * 指令参数处理器接口，用于处理指令参数并将其转换为方法参数。
 *
 * @author zExNocs
 * @date 2026/02/18
 */
public interface ICommandArgumentProcessor {
    /**
     * 处理指令参数。
     * @param method 指令方法
     * @param args 指令参数
     * @param commandData 指令数据。如果有参数是 CommandData 的话，可以直接使用
     * @return 处理后的参数
     */
    Object[] process(Method method, String[] args, CommandData<?> commandData);
}
