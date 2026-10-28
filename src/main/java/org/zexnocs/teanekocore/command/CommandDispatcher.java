package org.zexnocs.teanekocore.command;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.actuator.task.TaskStages;
import org.zexnocs.teanekocore.command.api.DefaultCommand;
import org.zexnocs.teanekocore.command.api.SubCommand;
import org.zexnocs.teanekocore.command.event.CommandDispatchEvent;
import org.zexnocs.teanekocore.command.event.CommandExecuteEvent;
import org.zexnocs.teanekocore.command.exception.CommandDataTypeMismatchException;
import org.zexnocs.teanekocore.command.interfaces.*;
import org.zexnocs.teanekocore.event.interfaces.IEventService;
import org.zexnocs.teanekocore.logger.ILogger;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.zexnocs.teanekocore.command.CommandResult.Status.*;

/**
 * 复用原事件、参数、作用域和权限链，返回等待反馈及业务完成的分发结果。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Service
@RequiredArgsConstructor
public class CommandDispatcher implements ICommandDispatcher {
    private final ILogger logger;
    private final ICommandPermissionManager permissionManager;
    private final ICommandScopeManager scopeManager;
    private final ICommandExecutor commandExecutor;
    private final ICommandArgumentProcessor argumentProcessor;
    private final CommandScanner commandScanner;
    private final IEventService iEventService;

    /**
     * 经过分发事件链匹配命令，并等待处理方法及反馈完成；取消、拒绝和未匹配也产生可观察回执。
     *
     * @param data 本次处理的输入数据
     * @param errorHandler 参数、权限和作用域拒绝的反馈处理器
     * @param helpHandler 帮助反馈处理器
     * @return 包含分发状态与实际处理结果的完成阶段；反馈或业务失败以异常完成
     */
    @Override
    public CompletionStage<CommandResult> dispatch(@NonNull CommandData<?> data,
            ICommandErrorHandler errorHandler, IHelpSubCommandHandler helpHandler) {
        try {
            var event = new CommandDispatchEvent(this, data, errorHandler, helpHandler);
            return TaskStages.successful(iEventService.pushEventWithFuture(event), "命令分发事件")
                    .thenCompose(ignored -> {
                        if (event.getCompletion() != null) return event.getCompletion();
                        if (event.isCancelled()) return result(CANCELLED);
                        return CompletableFuture.failedStage(new IllegalStateException("Dispatch event did not dispatch"));
                    });
        } catch (Exception e) {
            return CompletableFuture.failedStage(e);
        }
    }

    /**
     * 仅供 CommandDispatchEvent 在同步监听器完成后调用。
     *
     * @param data 本次处理的输入数据
     * @param errors 命令错误反馈处理器
     * @param help 帮助反馈处理器
     * @return 包含分发状态与实际处理结果的完成阶段；反馈或业务失败以异常完成
     */
    public CompletionStage<CommandResult> __dispatchForEvent(@NonNull CommandData<?> data,
            ICommandErrorHandler errors, IHelpSubCommandHandler help) {
        var prefix = commandScanner.getPrefixCommand(data.getBody());
        if (prefix != null) {
            return prefix.getCommandAnnotation().enable()
                    ? handlePrefix(data, prefix, errors, help)
                    : feedback(errors.handleCommandClosed(data), DISABLED);
        }
        var args = data.getArgs() == null ? new String[0] : data.getArgs();
        var body = data.getBody() + (args.length == 0 ? "" : " " + String.join(" ", args));
        for (var key : commandScanner.getRegexpCommandKeys()) {
            if (!body.matches(key)) continue;
            var map = commandScanner.getRegexpCommand(key);
            if (map == null) continue;
            return map.getCommandAnnotation().enable()
                    ? handleRegex(data, map, errors)
                    : feedback(errors.handleCommandClosed(data), DISABLED);
        }
        return result(NOT_MATCHED);
    }

    /**
     * 按帮助、子命令、默认方法的优先级选择入口，保留原始可信命令数据及剩余参数。
     *
     * @param data 本次处理的输入数据
     * @param map 按当前操作语义定位的输入映射
     * @param errors 命令错误反馈处理器
     * @param help 帮助反馈处理器
     * @return 包含分发状态与实际处理结果的完成阶段；反馈或业务失败以异常完成
     */
    private CompletionStage<CommandResult> handlePrefix(CommandData<?> data, CommandMapData map,
            ICommandErrorHandler errors, IHelpSubCommandHandler help) {
        var args = data.getArgs() == null ? new String[0] : data.getArgs();
        if (args.length > 0) {
            for (var key : help.getHelpCommandKey()) {
                if (args[0].equalsIgnoreCase(key)) {
                    return feedback(help.handleSubHelp(data, map, Arrays.copyOfRange(args, 1, args.length)), HELP);
                }
            }
            var sub = map.getSubCommand(args[0]);
            if (sub != null) {
                return processAndExecute(data, map, sub.getSecond(), sub.getFirst(),
                        Arrays.copyOfRange(args, 1, args.length), errors);
            }
        }
        var method = map.getDefaultCommandMethod();
        if (method == null || map.getDefaultCommandAnnotation() == null) {
            return feedback(errors.handleMethodNotFound(data), METHOD_NOT_FOUND);
        }
        return processAndExecute(data, map, method, null, args, errors);
    }

    /**
     * 完成参数绑定后进入原执行链；类型不适用和参数错误按各自状态结束，错误反馈亦被等待。
     *
     * @param data 本次处理的输入数据
     * @param map 按当前操作语义定位的输入映射
     * @param method 已选定的目标方法
     * @param sub 匹配到的子命令声明；默认命令可为空
     * @param args 待绑定的命令参数
     * @param errors 命令错误反馈处理器
     * @return 包含分发状态与实际处理结果的完成阶段；反馈或业务失败以异常完成
     */
    private CompletionStage<CommandResult> processAndExecute(CommandData<?> data, CommandMapData map,
            Method method, SubCommand sub, String[] args, ICommandErrorHandler errors) {
        final Object[] processed;
        try {
            processed = argumentProcessor.process(method, args, data);
        } catch (CommandDataTypeMismatchException ignored) {
            return result(DATA_TYPE_MISMATCH);
        }
        if (processed == null) return feedback(errors.handleArgsError(data), INVALID_ARGUMENTS);
        return execute(data, map, method, sub, processed, errors);
    }

    /**
     * 处理正则命令的既有参数形式，拒绝不支持的签名，不绕过执行阶段的权限和事件检查。
     *
     * @param data 本次处理的输入数据
     * @param map 按当前操作语义定位的输入映射
     * @param errors 命令错误反馈处理器
     * @return 包含分发状态与实际处理结果的完成阶段；反馈或业务失败以异常完成
     */
    private CompletionStage<CommandResult> handleRegex(CommandData<?> data, CommandMapData map,
            ICommandErrorHandler errors) {
        var method = map.getDefaultCommandMethod();
        if (method == null || map.getDefaultCommandAnnotation() == null) {
            return feedback(errors.handleMethodNotFound(data), METHOD_NOT_FOUND);
        }
        var parameters = method.getParameterTypes();
        if (parameters.length == 0) return execute(data, map, method, null, new Object[0], errors);
        if (parameters.length == 1 && parameters[0] == CommandData.class) {
            return execute(data, map, method, null, new Object[]{data}, errors);
        }
        logger.errorWithReport("CommandDispatcher", "正则指令参数错误：" + map.getCommand(),
                new IllegalArgumentException("正则指令只支持无参或单个 CommandData 参数"));
        return result(INVALID_ARGUMENTS);
    }

    /**
     * 在原客户端、作用域、权限和事件链中执行已绑定方法，拒绝反馈与业务完成共同构成回执。
     *
     * @param data 本次处理的输入数据
     * @param map 按当前操作语义定位的输入映射
     * @param method 已选定的目标方法
     * @param sub 匹配到的子命令声明；默认命令可为空
     * @param args 待绑定的命令参数
     * @param errors 命令错误反馈处理器
     * @return 包含分发状态与实际处理结果的完成阶段；反馈或业务失败以异常完成
     */
    private CompletionStage<CommandResult> execute(CommandData<?> data, CommandMapData map, Method method,
            SubCommand sub, Object[] args, ICommandErrorHandler errors) {
        var command = map.getCommandAnnotation();
        DefaultCommand defaults = map.getDefaultCommandAnnotation();
        boolean inScope = sub == null ? scopeManager.inScope(command, defaults, data)
                : scopeManager.inScope(command, sub, data);
        if (!inScope) return feedback(errors.handleNotInScope(data), OUT_OF_SCOPE);
        boolean permitted = sub == null ? permissionManager.hasPermission(command, defaults, data)
                : permissionManager.hasPermission(command, sub, data);
        if (!permitted) return feedback(errors.handleNoPermission(data), PERMISSION_DENIED);
        var event = new CommandExecuteEvent(commandExecutor, data, map,
                sub == null ? null : sub.value()[0], method, args);
        logger.info(getClass().getName(), "提交指令 %s，方法 %s，发送者 %s，权限 %s"
                .formatted(data.getBody(), method.getName(), data.getSenderId(), data.getPermission()));
        return TaskStages.successful(iEventService.pushEventWithFuture(event), "命令执行事件")
                .thenCompose(ignored -> {
                    if (event.getCompletion() != null) {
                        return event.getCompletion().thenApply(value -> new CommandResult(COMPLETED, value));
                    }
                    if (event.isCancelled()) return result(CANCELLED);
                    return CompletableFuture.failedStage(new IllegalStateException("Execute event did not execute"));
                });
    }

    /**
     * 为无需执行处理方法的分支创建已完成回执，保证所有分发路径均能结束。
     *
     * @param status 本次处理的终止状态
     * @return 包含分发状态与实际处理结果的完成阶段；反馈或业务失败以异常完成
     */
    private static CompletionStage<CommandResult> result(CommandResult.Status status) {
        return CompletableFuture.completedStage(CommandResult.of(status));
    }

    /**
     * 等待帮助或错误反馈结束后生成对应回执；发送失败继续向调用方传播。
     *
     * @param stage 需要观察或组合的完成阶段
     * @param status 本次处理的终止状态
     * @return 包含分发状态与实际处理结果的完成阶段；反馈或业务失败以异常完成
     * @throws NullPointerException 当必需的输入或依赖为空时
     */
    private static CompletionStage<CommandResult> feedback(CompletionStage<?> stage, CommandResult.Status status) {
        return Objects.requireNonNull(stage, "Command feedback returned no stage")
                .thenApply(ignored -> CommandResult.of(status));
    }
}
