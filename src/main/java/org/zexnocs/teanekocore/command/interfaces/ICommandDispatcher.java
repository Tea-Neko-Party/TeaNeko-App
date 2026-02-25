package org.zexnocs.teanekocore.command.interfaces;

import org.zexnocs.teanekocore.command.CommandData;

/**
 * 命令调度器。
 * 不包含解析指令
 * 包含 验证 → 执行
 *
 * @author zExNocs
 * @date 2026/02/18
 * @since 4.0.0
 */
public interface ICommandDispatcher {
    /**
     * 调度指令。
     *
     * @param data                  指令数据
     * @param errorHandler          错误处理器
     * @param helpSubCommandHandler 帮助子指令处理器
     */
    void dispatch(CommandData<?> data,
                  ICommandErrorHandler errorHandler,
                  IHelpSubCommandHandler helpSubCommandHandler);
}
