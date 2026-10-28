package org.zexnocs.teanekoapp.command;

import org.springframework.beans.factory.annotation.Autowired;
import org.zexnocs.teanekoapp.message.TeaNekoMessageReceiveEvent;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.command.interfaces.ICommandDispatcher;
import org.zexnocs.teanekocore.event.core.EventHandler;
import org.zexnocs.teanekocore.event.core.EventListener;
import org.zexnocs.teanekocore.logger.ILogger;

/**
 * 用于事件将 message 处理成指令的接收器。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.9
 * @version 4.5.0-l
 */
@EventListener
public class TeaNekoCommandReceiver {
    /// 指令转换器
    private final ILogger logger;
    private final TeaNekoCommandConverter teaNekoCommandConverter;

    /// 指令调度器
    private final ICommandDispatcher iCommandDispatcher;

    /// 指令错误处理器
    private final TeaNekoCommandErrorHandler teaNekoCommandErrorHandler;

    /// 帮助子指令处理器
    private final TeaNekoHelpSubCommandHandler teaNekoHelpSubCommandHandler;

    /**
     * 装配原消息到命令的转换、分发及反馈入口，事件监听端统一消费异步失败。
     *
     * @param teaNekoCommandConverter 用于本次处理的 {@code teaNekoCommandConverter} 数据
     * @param iCommandDispatcher 本次装配使用的 {@code ICommandDispatcher} 服务
     * @param teaNekoCommandErrorHandler 由装配方提供的 {@code TeaNekoCommandErrorHandler} 协作依赖
     * @param teaNekoHelpSubCommandHandler 由装配方提供的 {@code TeaNekoHelpSubCommandHandler} 协作依赖
     * @param logger 项目日志接口
     */
    @Autowired
    public TeaNekoCommandReceiver(TeaNekoCommandConverter teaNekoCommandConverter,
                                  ICommandDispatcher iCommandDispatcher, TeaNekoCommandErrorHandler teaNekoCommandErrorHandler, TeaNekoHelpSubCommandHandler teaNekoHelpSubCommandHandler, ILogger logger) {
        this.logger = logger;
        this.teaNekoCommandConverter = teaNekoCommandConverter;
        this.iCommandDispatcher = iCommandDispatcher;
        this.teaNekoCommandErrorHandler = teaNekoCommandErrorHandler;
        this.teaNekoHelpSubCommandHandler = teaNekoHelpSubCommandHandler;
    }

    /**
     * 接收 MessageReceivedEvent 事件
     *
     * @param event 用于本次处理的 {@code event} 数据
     */
    @EventHandler(priority = Integer.MIN_VALUE)
    public void handle(TeaNekoMessageReceiveEvent<?> event) {
        var data = event.getData();
        var commandData = teaNekoCommandConverter.parse(data);
        if(commandData == null) {
            // 解析失败，不处理
            return;
        }
        TaskFuture.fromStage(logger, "原生命令分发",
                iCommandDispatcher.dispatch(commandData, teaNekoCommandErrorHandler, teaNekoHelpSubCommandHandler)).finish();
    }
}
