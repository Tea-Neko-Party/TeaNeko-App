package org.zexnocs.teanekocore.command.event;

import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.command.CommandData;
import org.zexnocs.teanekocore.command.CommandMapData;
import org.zexnocs.teanekocore.command.interfaces.ICommandExecutor;
import org.zexnocs.teanekocore.event.AbstractEvent;

import java.lang.reflect.Method;

/**
 * 在执行 command 方法之前推送的事件。
 * <br>注意，当推送该事件时说明发送者已符合发送条件
 * <br>如果取消该事件，则不会执行该命令
 *
 * @author zExNocs
 * @date 2026/03/14
 * @since 4.3.1
 */
@Getter
public class CommandExecuteEvent extends AbstractEvent<CommandData<?>> {
    /// 执行器
    private final ICommandExecutor executor;

    /// command 的原始数据
    private final CommandMapData mapData;

    /// 执行的 subcommand key；如果是 default command，则为 null
    @Nullable
    private final String subCommandKey;

    /// 执行的方法
    private final Method method;

    /// 方法执行的方法参数
    private final Object[] args;

    /**
     * 事件的构造函数。
     *
     * @param data          事件数据
     * @param mapData       command 的原始数据
     * @param method        执行的方法
     * @param args          方法执行的方法参数
     */
    public CommandExecuteEvent(@NonNull ICommandExecutor executor,
                               @NonNull CommandData<?> data,
                               @NonNull CommandMapData mapData,
                               @Nullable String subCommandKey,
                               @NonNull Method method,
                               @NonNull Object[] args) {
        super(data);
        this.executor = executor;
        this.mapData = mapData;
        this.method = method;
        this.args = args;
        this.subCommandKey = subCommandKey;
    }

    /**
     * 通知完监听器后，执行该方法。
     */
    @Override
    public void _afterNotify() {
        var commandAnnotation = mapData.getCommandAnnotation();
        executor.execute(
                mapData.getCommand(),
                method,
                args,
                commandAnnotation.taskNamespace());
    }
}
