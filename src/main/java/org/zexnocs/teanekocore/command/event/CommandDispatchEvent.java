package org.zexnocs.teanekocore.command.event;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.CommandDispatcher;
import org.zexnocs.teanekocore.command.CommandResult;
import org.zexnocs.teanekocore.command.interfaces.ICommandErrorHandler;
import org.zexnocs.teanekocore.command.interfaces.IHelpSubCommandHandler;
import org.zexnocs.teanekocore.event.AbstractEvent;

import java.util.concurrent.CompletionStage;

/**
 * 指令被 dispatch 前推送的事件。
 * <br>当推送该事件时还没有检查是否符合执行条件
 * <br>取消该事件将会取消全部的指令执行流程，包括发送条件检查和权限检查等
 * <br>该事件只适用于 {@link CommandDispatcher}
 * 不适合其他的 {@link org.zexnocs.teanekocore.command.interfaces.ICommandDispatcher}
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.3.1
 * @version 4.5.0-l
 */
@Getter
@Setter
public class CommandDispatchEvent extends AbstractEvent<CommandData<?>> {
    /// 指令分发器
    private final CommandDispatcher dispatcher;

    /// 错误处理器
    @Nullable
    private ICommandErrorHandler errorHandler;

    /// 子指令处理器
    @Nullable
    private IHelpSubCommandHandler helpSubCommandHandler;

    @Setter(AccessLevel.NONE)
    private volatile CompletionStage<CommandResult> completion;

    /**
     * 事件的构造函数。
     *
     * @param dispatcher            指令分发器
     * @param data                  事件数据
     * @param errorHandler          错误处理器
     * @param helpSubCommandHandler 子指令帮助处理器
     */
    public CommandDispatchEvent(CommandDispatcher dispatcher,
                                @NonNull CommandData<?> data,
                                @Nullable ICommandErrorHandler errorHandler,
                                @Nullable IHelpSubCommandHandler helpSubCommandHandler) {
        super(data);
        this.dispatcher = dispatcher;
        this.errorHandler = errorHandler;
        this.helpSubCommandHandler = helpSubCommandHandler;
    }

    /**
     * 处理完监听器，分发指令。
     */
    @Override
    public void _afterNotify() {
        var errors = errorHandler != null ? errorHandler : new ICommandErrorHandler() {};
        var help = helpSubCommandHandler != null ? helpSubCommandHandler : new IHelpSubCommandHandler() {
            /**
             * 提供此反馈实现允许识别的帮助关键字，供原命令分发优先级判断。
             *
             * @return 满足本方法约束的 {@code String[]} 结果
             */
            @Override public String[] getHelpCommandKey() { return new String[0]; }
        };
        completion = dispatcher.__dispatchForEvent(getData(), errors, help);
    }
}
