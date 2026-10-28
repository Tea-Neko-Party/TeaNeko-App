package org.zexnocs.teanekocore.command.interfaces;

import org.zexnocs.teanekocore.command.CommandData;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * 指令执行错误时的处理器接口。
 * <p>在调用 {@link ICommandDispatcher} 时进行注入。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface ICommandErrorHandler {
    /**
     * 处理指令关闭的情况。
     *
     * @param commandData 指令数据
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    default CompletionStage<Void> handleCommandClosed(CommandData<?> commandData) {
        return CompletableFuture.completedStage(null);
    }

    /**
     * 处理没找到方法的情况。
     *
     * @param commandData 指令数据
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    default CompletionStage<Void> handleMethodNotFound(CommandData<?> commandData) {
        return CompletableFuture.completedStage(null);
    }

    /**
     * 处理参数错误的情况
     *
     * @param commandData 指令数据
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    default CompletionStage<Void> handleArgsError(CommandData<?> commandData) {
        return CompletableFuture.completedStage(null);
    }

    /**
     * 处理没有权限的情况。
     *
     * @param commandData 指令数据
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    default CompletionStage<Void> handleNoPermission(CommandData<?> commandData) {
        return CompletableFuture.completedStage(null);
    }

    /**
     * 处理不在作用域内的情况。
     *
     * @param commandData 指令数据
     * @return 等待本次业务及其异步后续处理结束的阶段；正常完成不额外证明具体业务已被接受
     */
    default CompletionStage<Void> handleNotInScope(CommandData<?> commandData) {
        return CompletableFuture.completedStage(null);
    }
}
