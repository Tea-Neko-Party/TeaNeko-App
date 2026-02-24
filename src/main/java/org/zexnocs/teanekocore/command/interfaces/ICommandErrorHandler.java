package org.zexnocs.teanekocore.command.interfaces;

import org.zexnocs.teanekocore.command.CommandData;

/**
 * 指令执行错误时的处理器接口。
 * <p>在调用 {@link ICommandDispatcher} 时进行注入。
 *
 * @author zExNocs
 * @date 2026/02/18
 * @since 4.0.0
 */
public interface ICommandErrorHandler {
    /**
     * 处理指令关闭的情况。
     * @param commandData 指令数据
     */
    void handleCommandClosed(CommandData<?> commandData);

    /**
     * 处理没找到方法的情况。
     * @param commandData 指令数据
     */
    void handleMethodNotFound(CommandData<?> commandData);

    /**
     * 处理参数错误的情况
     * @param commandData 指令数据
     */
    void handleArgsError(CommandData<?> commandData);

    /**
     * 处理没有权限的情况。
     * @param commandData 指令数据
     */
    void handleNoPermission(CommandData<?> commandData);

    /**
     * 处理不在作用域内的情况。
     * @param commandData 指令数据
     */
    void handleNotInScope(CommandData<?> commandData);
}
