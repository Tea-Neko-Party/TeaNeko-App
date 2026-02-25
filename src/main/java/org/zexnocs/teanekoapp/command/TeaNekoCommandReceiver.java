package org.zexnocs.teanekoapp.command;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekoapp.message.TeaNekoMessageReceiveEvent;
import org.zexnocs.teanekocore.command.interfaces.ICommandDispatcher;
import org.zexnocs.teanekocore.event.core.EventHandler;
import org.zexnocs.teanekocore.event.core.EventListener;

/**
 * 用于事件将 message 处理成指令的接收器。
 *
 * @author zExNocs
 * @date 2026/02/25
 * @since 4.0.9
 */
@EventListener
public class TeaNekoCommandReceiver {
    /// 指令转换器
    private final TeaNekoCommandConverter teaNekoCommandConverter;

    /// 指令调度器
    private final ICommandDispatcher iCommandDispatcher;

    /// 指令错误处理器
    private final TeaNekoCommandErrorHandler teaNekoCommandErrorHandler;

    /// 帮助子指令处理器
    private final TeaNekoHelpSubCommandHandler teaNekoHelpSubCommandHandler;

    @Autowired
    public TeaNekoCommandReceiver(TeaNekoCommandConverter teaNekoCommandConverter,
                                  ICommandDispatcher iCommandDispatcher, TeaNekoCommandErrorHandler teaNekoCommandErrorHandler, TeaNekoHelpSubCommandHandler teaNekoHelpSubCommandHandler) {
        this.teaNekoCommandConverter = teaNekoCommandConverter;
        this.iCommandDispatcher = iCommandDispatcher;
        this.teaNekoCommandErrorHandler = teaNekoCommandErrorHandler;
        this.teaNekoHelpSubCommandHandler = teaNekoHelpSubCommandHandler;
    }

    /**
     * 接收 MessageReceivedEvent 事件
     */
    @EventHandler(priority = Integer.MIN_VALUE)
    public void handle(TeaNekoMessageReceiveEvent<?> event) {
        var data = event.getData();
        var commandData = teaNekoCommandConverter.parse(data);
        if(commandData == null) {
            // 解析失败，不处理
            return;
        }
        iCommandDispatcher.dispatch(commandData, teaNekoCommandErrorHandler, teaNekoHelpSubCommandHandler);
    }
}
