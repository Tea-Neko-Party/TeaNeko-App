package org.zexnocs.teanekocore.command.interfaces;

import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.CommandResult;

import java.util.concurrent.CompletionStage;

/**
 * 命令调度器。
 * 不包含解析指令
 * 包含 验证 → 执行
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
public interface ICommandDispatcher {
    /**
     * 调度指令。
     *
     * @param data                  指令数据
     * @param errorHandler          错误处理器
     * @param helpSubCommandHandler 帮助子指令处理器
     * @return 包含分发状态与实际处理结果的完成阶段；反馈或业务失败以异常完成
     */
    CompletionStage<CommandResult> dispatch(CommandData<?> data,
                  ICommandErrorHandler errorHandler,
                  IHelpSubCommandHandler helpSubCommandHandler);
}
