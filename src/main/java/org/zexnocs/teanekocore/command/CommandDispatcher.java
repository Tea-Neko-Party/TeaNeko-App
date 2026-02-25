package org.zexnocs.teanekocore.command;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.command.api.DefaultCommand;
import org.zexnocs.teanekocore.command.api.SubCommand;
import org.zexnocs.teanekocore.command.exception.CommandDataTypeMismatchException;
import org.zexnocs.teanekocore.command.interfaces.*;
import org.zexnocs.teanekocore.logger.ILogger;

import java.lang.reflect.Method;

/**
 * 指令总调度器。
 * 1. 寻找是否存在指令
 * 2. 判断指令是否有权限
 * 3. 判断指令作用域
 * 4. 执行指令
 *
 * @author zExNocs
 * @date 2025/04/25
 * @since 4.0.0
 */
@Service
public class CommandDispatcher implements ICommandDispatcher {
    private final ILogger logger;
    private final ICommandPermissionManager permissionManager;
    private final ICommandScopeManager scopeManager;
    private final ICommandExecutor commandExecutor;
    private final ICommandArgumentProcessor argumentProcessor;
    private final CommandScanner commandScanner;

    @Autowired
    public CommandDispatcher(ILogger logger,
                             ICommandPermissionManager permissionManager,
                             ICommandScopeManager scopeManager,
                             ICommandExecutor commandExecutor,
                             ICommandArgumentProcessor argumentProcessor,
                             CommandScanner commandScanner) {
        this.permissionManager = permissionManager;
        this.scopeManager = scopeManager;
        this.commandExecutor = commandExecutor;
        this.commandScanner = commandScanner;
        this.argumentProcessor = argumentProcessor;
        this.logger = logger;
    }

    /**
     * 指令调度器。
     * @param data 指令数据
     * @param errorHandler 错误处理器
     * @param helpSubCommandHandler 帮助子指令处理器
     */
    @Override
    public void dispatch(@NonNull CommandData<?> data, ICommandErrorHandler errorHandler, IHelpSubCommandHandler helpSubCommandHandler) {
        var commandBody = data.getBody();
        // 首先尝试解析成前缀指令
        var prefixMapData = commandScanner.getPrefixCommand(commandBody);
        if(prefixMapData != null) {
            // 判断指令是否关闭
            if(prefixMapData.getCommandAnnotation().enable()) {
                __handlePrefixCommand(data, prefixMapData, errorHandler, helpSubCommandHandler);
            } else {
                errorHandler.handleCommandClosed(data);
            }
            return;
        }
        // 尝试解析成正则指令
        var sb = new StringBuilder();
        sb.append(commandBody);
        for(var arg: data.getArgs()) {
            sb.append(" ").append(arg);
        }
        var regexpBody = sb.toString();
        var regexpKeySet = commandScanner.getRegexpCommandKeys();
        for(var key: regexpKeySet) {
            if(regexpBody.matches(key)) {
                var regexpMapData = commandScanner.getRegexpCommand(key);
                if (regexpMapData == null) {
                    // 理论上不应该发生，说明在获取正则指令时发生了问题，直接跳过
                    continue;
                }
                if(regexpMapData.getCommandAnnotation().enable()) {
                    __handleRegexpCommand(data, regexpMapData, errorHandler);
                } else {
                    errorHandler.handleCommandClosed(data);
                }
                return;
            }
        }
    }


    // -------------- 前缀指令处理 --------------
    /**
     * 处理前缀指令。
     * @param data 指令输入数据
     * @param mapData 指令匹配映射数据
     * @param errorHandler 错误处理器
     */
    private void __handlePrefixCommand(@NonNull CommandData<?> data,
                                       CommandMapData mapData,
                                       ICommandErrorHandler errorHandler,
                                       IHelpSubCommandHandler helpSubCommandHandler) {
        var args = data.getArgs();
        // 尝试解析成 sub command
        if(args != null && args.length > 0) {
            // 首先判断是不是 help 子指令
            var helpSubCommandKey = helpSubCommandHandler.getHelpCommandKey();
            for(var key: helpSubCommandKey) {
                if(args[0].equalsIgnoreCase(key)) {
                    var newArgs = new String[args.length - 1];
                    System.arraycopy(args, 1, newArgs, 0, args.length - 1);
                    helpSubCommandHandler.handleSubHelp(data, mapData, newArgs);
                    return;
                }
            }
            // 否则尝试解析成其他子指令
            var subCommandMethodPair = mapData.getSubCommand(args[0]);
            if(subCommandMethodPair != null) {
                var newArgs = new String[args.length - 1];
                System.arraycopy(args, 1, newArgs, 0, args.length - 1);
                Object[] processedArgs;
                try {
                    processedArgs = argumentProcessor.process(subCommandMethodPair.getSecond(), newArgs, data);
                } catch (CommandDataTypeMismatchException ignore) {
                    // 如果参数类型不匹配，说明是子类数据获得了父类数据，正常现象，直接跳过
                    return;
                }
                if(processedArgs == null) {
                    errorHandler.handleArgsError(data);
                } else {
                    __handleSubCommand(data, mapData, errorHandler,
                            subCommandMethodPair.getFirst(),
                            subCommandMethodPair.getSecond(),
                            processedArgs);
                }
                return;
            }
        }
        // 尝试解析成 default command
        var defaultCommandMethod = mapData.getDefaultCommandMethod();
        var defaultCommand = mapData.getDefaultCommandAnnotation();
        if(defaultCommandMethod != null && defaultCommand != null) {
            Object[] processedArgs;
            try {
                processedArgs = argumentProcessor.process(defaultCommandMethod, args, data);
            } catch (CommandDataTypeMismatchException ignore) {
                // 如果参数类型不匹配，说明是子类数据获得了父类数据，正常现象，直接跳过
                return;
            }
            if(processedArgs == null) {
                errorHandler.handleArgsError(data);
            } else {
                __handleDefaultCommand(data, mapData, errorHandler,
                        defaultCommand,
                        defaultCommandMethod,
                        processedArgs);
            }
            return;
        }

        // 否则处理为指令未找到
        errorHandler.handleMethodNotFound(data);
    }

    // -------------- 正则指令处理 --------------
    /**
     * 处理正则指令。
     * @param data 指令输入数据
     * @param mapData 指令匹配映射数据
     * @param errorHandler 错误处理器
     */
    private void __handleRegexpCommand(@NonNull CommandData<?> data, CommandMapData mapData, ICommandErrorHandler errorHandler) {
        // 前缀指令只能使用默认指令，并且默认指令的参数要么为空，要么为 CommandData<?> 类型
        var defaultCommandMethod = mapData.getDefaultCommandMethod();
        var defaultCommand = mapData.getDefaultCommandAnnotation();
        if(defaultCommandMethod == null || defaultCommand == null) {
            errorHandler.handleMethodNotFound(data);
            return;
        }
        // 处理参数
        var parameterTypes = defaultCommandMethod.getParameterTypes();
        if(parameterTypes.length == 0) {
            __handleDefaultCommand(data, mapData, errorHandler, defaultCommand, defaultCommandMethod, new Object[0]);
        } else if (parameterTypes.length == 1 && parameterTypes[0] == CommandData.class) {
            __handleDefaultCommand(data, mapData, errorHandler, defaultCommand, defaultCommandMethod, new Object[]{data});
        } else {
            logger.errorWithReport("CommandDispatcher",
                    "正则指令参数错误，要么没有参数，要么参数为 CommandData<?> 类型。指令：" + mapData.getCommand(),
                    new IllegalArgumentException("正则指令错误。"));
        }
    }


    // -------------- 指令具体处理 --------------
    /**
     * 处理子指令。
     * @param data 指令输入数据
     * @param mapData 指令匹配映射数据
     * @param errorHandler 错误处理器
     * @param subCommand  子指令注解
     * @param commandMethod 子指令方法
     * @param args 参数
     */
    private void __handleSubCommand(@NonNull CommandData<?> data,
                                    CommandMapData mapData,
                                    ICommandErrorHandler errorHandler,
                                    SubCommand subCommand,
                                    Method commandMethod,
                                    Object[] args) {
        var commandAnnotation = mapData.getCommandAnnotation();
        // 判断指令作用域
        if(!scopeManager.inScope(commandAnnotation, subCommand, data)) {
            errorHandler.handleNotInScope(data);
            return;
        }

        // 判断指令是否有权限
        if(!permissionManager.hasPermission(commandAnnotation, subCommand, data)) {
            errorHandler.handleNoPermission(data);
            return;
        }
        // 执行指令
        commandExecutor.execute(mapData.getCommand(), commandMethod, args, commandAnnotation.taskNamespace());

        logger.info(this.getClass().getName(), """
                执行指令 %s 中子指令 %s, 执行ID: %s, 权限: %s / %s"""
                .formatted(data.getBody(), subCommand.value()[0],
                        data.getSenderId(),
                        data.getPermission(),
                        subCommand.permission().equals(CommandPermission.DEFAULT) ?
                                mapData.getCommandAnnotation().permission() : subCommand.permission()));
    }

    /**
     * 处理默认指令。
     * @param data 指令输入数据
     * @param mapData 指令匹配映射数据
     * @param errorHandler 错误处理器
     * @param defaultCommand 默认指令注解
     * @param commandMethod 默认指令方法
     * @param args 参数
     */
    private void __handleDefaultCommand(@NonNull CommandData<?> data,
                                        CommandMapData mapData,
                                        ICommandErrorHandler errorHandler,
                                        DefaultCommand defaultCommand,
                                        Method commandMethod,
                                        Object[] args) {
        var commandAnnotation = mapData.getCommandAnnotation();
        // 判断指令作用域
        if(!scopeManager.inScope(commandAnnotation, defaultCommand, data)) {
            errorHandler.handleNotInScope(data);
            return;
        }
        // 判断指令是否有权限
        if(!permissionManager.hasPermission(commandAnnotation, defaultCommand, data)) {
            errorHandler.handleNoPermission(data);
            return;
        }
        // 执行指令
        commandExecutor.execute(mapData.getCommand(), commandMethod, args, commandAnnotation.taskNamespace());

        logger.info(this.getClass().getName(), """
                执行指令 %s 中默认指令, 执行ID: %s, 权限: %s / %s"""
                .formatted(data.getBody(),
                        data.getSenderId(),
                        data.getPermission(),
                        defaultCommand.permission().equals(CommandPermission.DEFAULT) ?
                                mapData.getCommandAnnotation().permission() : defaultCommand.permission()));
    }
}
