package org.zexnocs.teanekocore.command;

import org.springframework.stereotype.Service;
import org.zexnocs.teanekocore.command.api.*;
import org.zexnocs.teanekocore.command.easydata.CommandEasyData;
import org.zexnocs.teanekocore.command.interfaces.ICommandScopeManager;

/**
 * 指令范围管理器，用于判断指令是否在范围内
 *
 * @author zExNocs
 * @date 2026/02/18
 * @since 4.0.0
 */
@Service("commandScopeManager")
public class CommandScopeManager implements ICommandScopeManager {
    public static final String ENABLE_NAMESPACE = "command.scope.enable";
    public static final String DISABLE_NAMESPACE = "command.scope.disable";

    /**
     * 判断指令是否在范围内，先判断是否被禁止使用权限，
     * 如果被禁止使用权限则直接返回 false，再判断原始权限，如果匹配或者范围是 ALL，则返回 true，最后判断数据库权限
     *
     * @param command 命令
     * @param commandData 命令数据
     * @return boolean
     */
    @Override
    public boolean inScope(Command command, CommandData<?> commandData) {
        return __inScope(command, commandData, command.value()[0], command.scope());
    }

    /**
     * 判断指令是否在范围内，先判断是否被禁止使用权限，如果被禁止使用权限则直接返回 false，
     * 再判断原始权限，如果匹配或者范围是 ALL，则返回 true，最后判断数据库权限
     *
     * @param command 命令
     * @param defaultCommand 默认命令
     * @param commandData 命令数据
     * @return boolean
     */
    @Override
    public boolean inScope(Command command, DefaultCommand defaultCommand, CommandData<?> commandData) {
        var commandId = command.value()[0] + "_default";
        CommandScope expectedScope = defaultCommand.scope();
        if(expectedScope.equals(CommandScope.DEFAULT)) {
            expectedScope = command.scope();
            commandId = command.value()[0];
        }
        return __inScope(command, commandData, commandId, expectedScope);
    }

    /**
     * 判断指令是否在范围内，先判断是否被禁止使用权限，如果被禁止使用权限则直接返回 false，
     * 再判断原始权限，如果匹配或者范围是 ALL，则返回 true，最后判断数据库权限
     *
     * @param command 命令
     * @param subCommand 子命令
     * @param commandData 命令数据
     * @return boolean
     */
    @Override
    public boolean inScope(Command command, SubCommand subCommand, CommandData<?> commandData) {
        var commandId = command.value()[0] + "_" + subCommand.value()[0];
        CommandScope expectedScope = subCommand.scope();
        if(expectedScope.equals(CommandScope.DEFAULT)) {
            expectedScope = command.scope();
            commandId = command.value()[0];
        }
        return __inScope(command, commandData, commandId, expectedScope);
    }

    /**
     * 判断指令是否在范围内，先判断是否被禁止使用权限，如果被禁止使用权限则直接返回 false，
     * 再判断原始权限，如果匹配或者范围是 ALL，则返回 true，最后判断数据库权限
     *
     * @param command       原 command 注解
     * @param commandData   命令数据
     * @param commandId     命令 ID
     * @param expectedScope 预期范围
     * @return boolean
     */
    private boolean __inScope(Command command,
                              CommandData<?> commandData,
                              String commandId,
                              CommandScope expectedScope) {
        // 取第一个作为指令的主 ID
        var scopeId = commandData.getScopeId();
        // 1. 先判断当前 client 是否支持指令，如果不支持则直接返回 false
        // 只要 currentClinetClass 可以转化为支持的 client 任意一个，就认为支持
        var currentClientClass = commandData.getClientClass();
        var supportedClients = command.supportedClients();
        boolean supported = false;
        for(var expectedClient : supportedClients) {
            if(expectedClient.isAssignableFrom(currentClientClass)) {
                supported = true;
                break;
            }
        }
        // 如果不支持，则直接返回 false
        if(!supported) {
            return false;
        }

        // 2. 先判断是否被被取消权限，如果被禁止使用权限则直接返回 false
        var __disableEasyData = CommandEasyData.of(DISABLE_NAMESPACE);
        if(__disableEasyData.get(commandId).getBoolean(scopeId)) {
            return false;
        }

        // 3. 再判断是否在范围内
        // a. 判断是不是 DEFAULT + DEBUGGER scope 的情况，如果是则直接返回 true
        if(expectedScope.equals(CommandScope.DEFAULT) && commandData.getPermission().equals(CommandPermission.DEBUG)) {
            return true;
        }

        // b. 判断原始权限。如果匹配或者范围是 ALL，则返回 true
        var actualScope = commandData.getScope();
        if(expectedScope.equals(actualScope) || expectedScope.equals(CommandScope.ALL)) {
            return true;
        }

        // c. 判断数据库权限
        var __enableEasyData = CommandEasyData.of(ENABLE_NAMESPACE);
        return __enableEasyData.get(commandId).getBoolean(scopeId);
    }
}
